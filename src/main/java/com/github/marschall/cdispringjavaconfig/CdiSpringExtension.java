package com.github.marschall.cdispringjavaconfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ConversationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.util.AnnotationLiteral;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * The actual CDI extension implementation.
 *
 * <p>
 * Clients should not reference this class in any way (eg. instantiate or subclass).
 * This class is to be used solely by the CDI framework.
 */
public class CdiSpringExtension implements Extension {
  // http://docs.jboss.org/weld/reference/latest/en-US/html/extend.html
  // http://jaxenter.com/tutorial-cdi-extension-programming-42972.html

  private static final Object[] EMPTY_ARRAY = new Object[0];
  
  private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];
  
  private final Set<Class<?>> configurationClasses;
  
  // Maps a class to it's proxy subclass
  private Map<Class<?>, Class<?>> proxyClasses;
  
  public CdiSpringExtension() {
    this.configurationClasses = new HashSet<>();
  }

  public void loadConfiguration(@Observes ProcessAnnotatedType<?> pat, BeanManager beanManager) {
    AnnotatedType<?> annotatedType = pat.getAnnotatedType();
    ConfigurationClasses configurationClassAnnotation = annotatedType.getAnnotation(ConfigurationClasses.class);
    if (configurationClassAnnotation == null) {
      // not an EJB with @ConfigurationClasses -> we don't care
      return;
    }
    
    for (Class<?> clazz : configurationClassAnnotation.value()) {
      configurationClasses.add(clazz);
    }
  }

  public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
    Map<ClassLoader, Set<Class<?>>> proxySubclasses = this.buildProxySubclasses();
    Map<Class<?>, ?> configurationObjects = this.instantiate(proxySubclasses);
    
    Set<Class<?>> alreadyProcessed = new HashSet<>(this.configurationClasses.size());
    for (Class<?> configurationClass : this.configurationClasses) {
      this.buildConfiguraion(configurationClass, alreadyProcessed, abd, bm, configurationObjects);
    }
  }
  
  private Map<ClassLoader, Set<Class<?>>> buildProxySubclasses() {
    Map<ClassLoader, Set<Class<?>>> perClassloader = new HashMap<>();
    Set<Class<?>> processed = new HashSet<>();
    for (Class<?> configurationClass : this.configurationClasses) {
      this.processImports(configurationClass, processed, perClassloader);
    }
    
    return perClassloader;
  }
  
  private void processImports(Class<?> configurationClass, Set<Class<?>> processed, Map<ClassLoader, Set<Class<?>>> perClassloader) {
    if (processed.contains(configurationClass)) {
      return;
    }
    processed.add(configurationClass);
    
    this.validateConfigurationClass(configurationClass);
    Import importAnnotation = configurationClass.getAnnotation(Import.class);
    for (Class<?> importClass : importAnnotation.value()) {
      ClassLoader classLoader = importClass.getClassLoader();
      Set<Class<?>> classes = perClassloader.get(classLoader);
      if (classes == null) {
        classes = new HashSet<>();
        perClassloader.put(classLoader, classes);
      }
      classes.add(importClass);
      
      processImports(importClass, processed, perClassloader);
    }
    
  }
  
  private Map<Class<?>, Object> instantiate(Map<ClassLoader, Set<Class<?>>> perClassLoader) {
    Map<Class<?>, Object> proxyClasses = new HashMap<>();
    for (Entry<ClassLoader, Set<Class<?>>> entry : perClassLoader.entrySet()) {
      ClassLoader classLoader = entry.getKey();
      Set<Class<?>> classes = entry.getValue();
      Map<String, byte[]> generatedCode = new HashMap<>(classes.size());
      Map<String, Class<?>> proxyNameToClass = new HashMap<>(classes.size());
      for (Class<?> clazz : classes) {
        // TODO cache
        List<Method> beanMethods = this.getBeanMethods(clazz);
        // TODO cache
        ProxySubclassGenerator generator = new ProxySubclassGenerator(clazz, beanMethods);
        byte[] byteCode = generator.generate();
        String proxyName = generator.getClassName();
        proxyNameToClass.put(proxyName, clazz);
        generatedCode.put(proxyName, byteCode);
      }
      AdHocClassLoader proxyClassLoader = new AdHocClassLoader(classLoader, generatedCode);
      for (String proxyName : generatedCode.keySet()) {
        Object proxy;
        try {
          Class<?> proxyClass = Class.forName(proxyName, true, proxyClassLoader);
          proxy = proxyClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
          throw new BeanCreationException("failed to create proxy instance: " + proxyName, e);
        }
        proxyClasses.put(proxyNameToClass.get(proxyName), proxy);
      }
    }
    return proxyClasses;
  }
  
  private void validateConfigurationClass(Class<?> configurationClass) {
    validateDefaultConstructor(configurationClass);
    validateAnnotationNotPresent(configurationClass, ImportResource.class);
    validateAnnotationNotPresent(configurationClass, Profile.class);
  }

  private void buildConfiguraion(Class<?> configurationClass, Set<Class<?>> alreadyProcessed, AfterBeanDiscovery abd, BeanManager bm, Map<Class<?>, ?> configurationObjects) {
    Set<Bean<?>> configurations = bm.getBeans(configurationClass);
    if (!configurations.isEmpty()) {
      // configuration already registered
      return;
    }
    if (alreadyProcessed.contains(configurationClass)) {
      return;
    }
    alreadyProcessed.add(configurationClass);
    
    Import importAnnotation = configurationClass.getAnnotation(Import.class);
    // TODO add to configuration classes, attention we're iterating over it
    for (Class<?> importedClass : importAnnotation.value()) {
      buildConfiguraion(importedClass, alreadyProcessed, abd, bm, configurationObjects);
    }

    // TODO cache
    List<Method> beanMethods = this.getBeanMethods(configurationClass);
    abd.addBean(buildConfigurationBean(bm, configurationClass, configurationObjects));
    addBeanMethods(configurationClass, beanMethods, abd, bm);
  }

  private void validateAnnotationNotPresent(Class<?> configurationClass, Class<? extends Annotation> annotationClass) {
    Annotation annotation = configurationClass.getAnnotation(annotationClass);
    if (annotation != null) {
      throw new CreationException('@' + annotationClass.getSimpleName() + " on " + configurationClass + " not supported");
    }
  }
  
  private void validateDefaultConstructor(Class<?> configurationClass) {
    for (Constructor<?> constructor : configurationClass.getDeclaredConstructors()) {
      if (constructor.getParameterTypes().length == 0) {
        int modifiers = constructor.getModifiers();
        if (Modifier.isPrivate(modifiers)) {
          // public and protected are fine
          // package protected will be fine since the generated class will be in the same package
          // (the constructor we'll generate will be public)
          throw new CreationException(configurationClass + " default constructor must not be private");
        }
      }
    }
    throw new CreationException(configurationClass + " is missing a default constructor");
  }
  
  private List<Method> getBeanMethods(Class<?> configurationClass) {
    // TODO non public
    return Arrays.asList(configurationClass.getMethods());
  }

  private void addBeanMethods(Class<?> configurationClass, List<Method> beanMethods, AfterBeanDiscovery abd, BeanManager bm) {
    for (Method method : beanMethods) {
      org.springframework.context.annotation.Bean springBeanAnnoation = method.getAnnotation(org.springframework.context.annotation.Bean.class);
      if (springBeanAnnoation != null) {
        Bean<?> bean =  addBeanMethod(configurationClass, method, abd, bm);
        abd.addBean(bean);
      }
    }
  }

  private Bean<?> addBeanMethod(final Class<?> configurationClass, final Method method, AfterBeanDiscovery abd, final BeanManager bm) {
    int modifiers = method.getModifiers();
    if (Modifier.isStatic(modifiers)) {
      throw new InjectionException("static method: " + method + " not supported");
    }
    // boolean isLazy = method.getAnnotation(Lazy.class) != null;
    final AnnotatedType<Object> at = (AnnotatedType<Object>) bm.createAnnotatedType(method.getReturnType());
    final InjectionTarget<Object> it = bm.createInjectionTarget(at);

    return new Bean<Object>() {

      private org.springframework.context.annotation.Bean beanAnnotation;

      @Override
      public Class<?> getBeanClass() {
        return method.getReturnType();
      }

      @Override
      public Set<InjectionPoint> getInjectionPoints() {
        return it.getInjectionPoints();
      }

      @Override
      public String getName() {
        return method.getDeclaringClass().getName() + '.' + method.getName();
      }

      @Override
      public Set<Annotation> getQualifiers() {
        Annotation primary = findAnnotation(method, Primary.class);
        Annotation qualifier = findAnnotation(method, org.springframework.beans.factory.annotation.Qualifier.class);
        if (primary == null && qualifier == null) {
          return Collections.emptySet();
        } else if (primary != null && qualifier == null) {
          return Collections.singleton(qualifier);
        } else if (primary == null && qualifier != null) {
          return Collections.singleton(qualifier);
        } else {
          return new TwoElementSet<>(qualifier, primary);
        }
      }

      @Override
      public Class<? extends Annotation> getScope() {
        org.springframework.context.annotation.Scope scope = findAnnotation(method, org.springframework.context.annotation.Scope.class);
        return translateScope(scope);
      }

      @Override
      public Set<Class<? extends Annotation>> getStereotypes() {
        List<org.springframework.stereotype.Component> components = findAnnotations(method, org.springframework.stereotype.Component.class);
        Set<Class<? extends Annotation>> stereotypes = new HashSet<>(components.size());
        for (org.springframework.stereotype.Component component : components) {
          stereotypes.add(component.annotationType());
        }
        return stereotypes;
      }

      @Override
      public Set<Type> getTypes() {
        return Collections.<Type>singleton(method.getReturnType());
      }

      @Override
      public boolean isAlternative() {
        org.springframework.beans.factory.annotation.Qualifier qualifier = findAnnotation(method, org.springframework.beans.factory.annotation.Qualifier.class);
        return qualifier != null;
      }

      @Override
      public boolean isNullable() {
        // apparently it's OK for @Bean methods to return null
        return true;
      }

      @Override
      public Object create(CreationalContext<Object> ctx) {
        Bean<?> bean = getRequiredBean(bm, configurationClass, EMPTY_ANNOTATION_ARRAY);
        Object configuration = bm.getReference(bean, configurationClass, ctx);
        Object[] parameters = getParameters(ctx);
        // can be null
        Object springBean;
        try {
          springBean = method.invoke(configuration, parameters);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
          throw new CreationException("could not create bean for: " + method, e);
        }
        if (isAutowire()) {
          autowire(springBean, ctx, bm);
        }
        callMethod(getInitMethod(), springBean);
        if (springBean != null) {
          it.postConstruct(springBean);
        }
        if (springBean instanceof FactoryBean) {
          try {
            springBean = ((FactoryBean<?>) springBean).getObject();
          } catch (Exception e) {
            throw new CreationException("could not create bean", e);
          }
          initialize(springBean);
          it.postConstruct(springBean);
        }
        
        return springBean;
      }

      private Object[] getParameters(CreationalContext<?> ctx) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] parameters;
        if (parameterTypes.length == 0) {
          // common case
          parameters = EMPTY_ARRAY;
        } else {
          parameters = new Object[parameterTypes.length];
          for (int i = 0; i < parameterTypes.length; i++) {
            Annotation[] parameterAnnotation = parameterAnnotations[i];
            Type type = parameterTypes[i];
            Bean<?> bean = getRequiredBean(bm, type, findQualifiers(parameterAnnotation));
            parameters[i] = bm.getReference(bean, type, ctx);
            
          }
        }
        return parameters;
      }
      
      private void initialize(Object springBean) {
        if (springBean instanceof InitializingBean) {
          try {
            ((InitializingBean) springBean).afterPropertiesSet();
          } catch (Exception e) {
            throw new CreationException("could initialize bean: " + springBean + " for: " + method, e);
          }
        }
      }
      
      private void callMethod(String methodName, Object springBean) {
        if (springBean == null) {
          return;
        }
        
        if (!methodName.isEmpty()) {
          try {
            springBean.getClass().getMethod(methodName).invoke(springBean);
          } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new CreationException("could call:" + methodName + " on: " + springBean + " for: " + method, e);
          }
        }
      }
      
      private boolean isAutowire() {
        return getBeanAnnotation().autowire().isAutowire();
      }
      
      private String getInitMethod() {
        return defaultString(getBeanAnnotation().initMethod());
      }
      
      private String getDestroyMethod() {
        return defaultString(getBeanAnnotation().destroyMethod());
      }
      
      private org.springframework.context.annotation.Bean getBeanAnnotation() {
        if (this.beanAnnotation == null) {
          this.beanAnnotation = method.getAnnotation(org.springframework.context.annotation.Bean.class);
        }
        return this.beanAnnotation;
      }

      @Override
      public void destroy(Object springBean, CreationalContext<Object> ctx) {
        it.preDestroy(springBean);
        String destroyMethod = getDestroyMethod();
        if (destroyMethod.isEmpty()) {
          // TODO close magic
        } else {
          callMethod(destroyMethod, springBean);
        }
        if (springBean instanceof AutoCloseable) {
          if (!("close".equals(destroyMethod) && method.getParameterTypes().length == 0)) {
            AutoCloseable autoCloseable = (AutoCloseable) springBean;
            try {
              autoCloseable.close();
            } catch (Exception e) {
              throw new CreationException("could not auto close " + springBean, e);
            }
          }
        }
        it.dispose(springBean);
        ctx.release();
      }
    };
  }
  
  static String defaultString(String s) {
    return s == null ? "" : s;
  }
  
  private static Bean<?> getBean(BeanManager bm, Autowired autowired, Type type, AccessibleObject annotationHolder) {
    if (autowired.required()) {
      return getRequiredBean(bm, type, findQualifiers(annotationHolder));
    } else {
      return getBean(bm, type, findQualifiers(annotationHolder));
    }
  }
  
  private static Bean<?> getBean(BeanManager bm, Autowired autowired, Type type, Annotation[] qualifiers) {
    if (autowired.required()) {
      return getRequiredBean(bm, type, qualifiers);
    } else {
      return getBean(bm, type, qualifiers);
    }
  }

  private static Bean<?> getRequiredBean(BeanManager bm, Type type, Annotation[] qualifiers) {
    Bean<?> bean = getBean(bm, type, qualifiers);
    if (bean == null) {
      throw new CreationException("no beans for class: " + type + " found");
    }
    return bean;
  }

  private static Bean<?>  getBean(BeanManager bm, Type type, Annotation[] qualifiers) {
    Set<Bean<?>> beans = bm.getBeans(type, qualifiers);
    if (beans.isEmpty()) {
      return null;
    }
    if (beans.size() > 1) {
      throw new CreationException("multiple beans for class: " + type + " found");
    }
    return beans.iterator().next();
  }

  private <A extends Annotation> A findAnnotation(AccessibleObject accessibleObject, Class<A> annotationClass) {
    A annotation = accessibleObject.getAnnotation(annotationClass);
    if (annotation != null) {
      return annotation;
    }
    // search for meta annotations
    //
    // @Bean
    // @MyQualifier
    // public Object myBean() {}
    //
    // @Qualifier
    // public @interface MyQualifier {}
    for (Annotation methodAnnotation : accessibleObject.getAnnotations()) {
      A typeAnnotation = methodAnnotation.annotationType().getAnnotation(annotationClass);
      if (typeAnnotation != null) {
        return typeAnnotation;
      }
    }

    // nothing found
    return null;
  }
  
  private static <A extends Annotation> List<A> findAnnotations(AccessibleObject annotationHolder, Class<A> annotationClass) {
    List<A>  annotations = new ArrayList<>(4);
    A annotation = annotationHolder.getAnnotation(annotationClass);
    if (annotation != null) {
      annotations.add(annotation);
    }
    // search for meta annotations
    //
    // @Bean
    // @MyQualifier
    // public Object myBean() {}
    //
    // @Qualifier
    // public @interface MyQualifier {}
    for (Annotation methodAnnotation : annotationHolder.getAnnotations()) {
      A typeAnnotation = methodAnnotation.annotationType().getAnnotation(annotationClass);
      if (typeAnnotation != null) {
        annotations.add(typeAnnotation);
      }
    }
    
    return annotations;
  }
  
  private static <A extends Annotation> List<A> findAnnotations(Annotation[] annotations, Class<A> annotationClass) {
    List<A>  annotationList = new ArrayList<>(4);
    for (Annotation annotation : annotations) {
      if (annotationClass.isInstance(annotation)) {
        annotationList.add(annotationClass.cast(annotation));
      } else {
        // search for meta annotations
        //
        // @Bean
        // @MyQualifier
        // public Object myBean() {}
        //
        // @Qualifier
        // public @interface MyQualifier {}
        A typeAnnotation = annotation.annotationType().getAnnotation(annotationClass);
        if (typeAnnotation != null) {
          annotationList.add(typeAnnotation);
        }
      }
    }
    
    return annotationList;
  }
  
  private static Annotation[] findQualifiers(Annotation[] annotations) {
    List<org.springframework.beans.factory.annotation.Qualifier> qualifiers = findAnnotations(annotations, org.springframework.beans.factory.annotation.Qualifier.class);
    if (qualifiers.isEmpty()) {
      return EMPTY_ANNOTATION_ARRAY;
    } else {
      return qualifiers.toArray(new Annotation[qualifiers.size()]);
    }
  }

  private static Annotation[] findQualifiers(AccessibleObject annotationHolder) {
    List<org.springframework.beans.factory.annotation.Qualifier> qualifiers = findAnnotations(annotationHolder, org.springframework.beans.factory.annotation.Qualifier.class);
    if (qualifiers.isEmpty()) {
      return EMPTY_ANNOTATION_ARRAY;
    } else {
      return qualifiers.toArray(new Annotation[qualifiers.size()]);
    }
  }

  private Class<? extends Annotation> translateScope(org.springframework.context.annotation.Scope scope) {
    if (scope == null) {
      // spring default is singleton
      return ApplicationScoped.class;
    }
    switch (scope.value()) {
      // don't want a spring web-mwc dependency
      case "request":
        return RequestScoped.class;
        // don't want a spring web-mwc dependency
      case "session":
        return SessionScoped.class;
      case "conversation":
        return ConversationScoped.class;
      case ConfigurableBeanFactory.SCOPE_PROTOTYPE:
        // ???
        return Dependent.class;
      case ConfigurableBeanFactory.SCOPE_SINGLETON:
        return ApplicationScoped.class;
      default:
        // ???
        return ApplicationScoped.class;
    }
  }

  private Bean<Object> buildConfigurationBean(BeanManager bm, Class<?> configurationClass, Map<Class<?>, ?> configurationObjects) {
    AnnotatedType<?> at = bm.createAnnotatedType(configurationClass);
    //use this to instantiate the class and inject dependencies
    InjectionTarget<Object> it = (InjectionTarget<Object>) bm.createInjectionTarget(at);
    return new ConfigurationBean(bm, it, configurationObjects.get(configurationClass), configurationClass);
  }
  
  private static void autowire(Object springBean, CreationalContext<Object> ctx, BeanManager bm) {
    if (springBean == null) {
      return;
    }

    Class<? extends Object> current = springBean.getClass();
    while (current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        Autowired autowired = field.getAnnotation(Autowired.class);
        if (autowired != null) {
          Type type = field.getType();
          Bean<?> bean = getBean(bm, autowired, type, field);
          field.setAccessible(true);
          try {
            field.set(springBean, bm.getReference(bean, type, ctx));
          } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new CreationException("could not set value of: " + field, e);
          }
        }
      }

      current = current.getSuperclass();
    }

    // TODO non-public
    for (Method method : springBean.getClass().getMethods()) {
      Autowired autowired = method.getAnnotation(Autowired.class);
      if (autowired != null) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
          Annotation[] parameterAnnotation = parameterAnnotations[i];
          Type type = parameterTypes[i];
          Bean<?> bean = getBean(bm, autowired, type, findQualifiers(parameterAnnotation));
          parameters[i] = bm.getReference(bean, type, ctx);
        }
        try {
          method.invoke(springBean, parameters);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
          throw new CreationException("could not invoke: " + method, e);
        }
      }
    }

  }

  static final class ConfigurationBean implements Bean<Object> {

    private final InjectionTarget<Object> it;
    private final Object configuration;
    private final BeanManager bm;
    private final Class<?> configurationClass;

    ConfigurationBean(BeanManager bm, InjectionTarget<Object> it, Object configuration, Class<?> configurationClass) {
      this.it = it;
      this.bm = bm;
      this.configuration = configuration;
      this.configurationClass = configurationClass;
    }

    @Override
    public Class<?> getBeanClass() {
      return this.configurationClass;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
      return it.getInjectionPoints();
    }

    @Override
    public String getName() {
      String simpleName = configurationClass.getSimpleName();
      if (simpleName.length() == 1) {
        return simpleName.toLowerCase();
      } else {
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
      }
    }

    @Override
    public Set<Annotation> getQualifiers() {
      Set<Annotation> qualifiers = new HashSet<Annotation>();
      qualifiers.add(new AnnotationLiteral<Default>() {});
      qualifiers.add(new AnnotationLiteral<Any>() {});
      return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
      return ApplicationScoped.class;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
      return Collections.emptySet();
    }

    @Override

    public Set<Type> getTypes() {
      return Collections.<Type>singleton(this.configurationClass);
    }

    @Override
    public boolean isAlternative() {
      return false;
    }

    @Override
    public boolean isNullable() {
      return false;
    }

    @Override
    public Object create(CreationalContext<Object> ctx) {
      autowire(this.configuration, ctx, bm);
      it.inject(this.configuration, ctx);
      it.postConstruct(this.configuration);
      return this.configuration;
    }

    @Override
    public void destroy(Object instance, CreationalContext<Object> ctx) {
      it.preDestroy(instance);
      it.dispose(instance);
      ctx.release();
    }
  }

}
