package com.github.marschall.cdispringjavaconfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ConversationScoped;
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

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
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
  
  private Class<?>[] configurationClasses;

  public void loadConfiguration(@Observes ProcessAnnotatedType<?> pat, BeanManager beanManager) {
    AnnotatedType<?> annotatedType = pat.getAnnotatedType();
    ConfigurationClass configurationClassAnnotation = annotatedType.getAnnotation(ConfigurationClass.class);
    if (configurationClassAnnotation == null) {
      // not an EJB with @ConfigurationClass -> we don't care
      return;
    }
    configurationClasses = configurationClassAnnotation.value();
  }

  public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
    for (Class<?> configurationClass : this.configurationClasses) {
      this.buildConfiguraion(configurationClass, abd, bm);
    }
  }

  private void buildConfiguraion(final Class<?> configurationClass, AfterBeanDiscovery abd, BeanManager bm) {
    Set<Bean<?>> configurations = bm.getBeans(configurationClass);
    if (!configurations.isEmpty()) {
      // configuration already registered
      return;
    }
    
    validateAnnotationNotPresent(configurationClass, ImportResource.class);
    validateAnnotationNotPresent(configurationClass, Profile.class);
    
    Import importAnnotation = configurationClass.getAnnotation(Import.class);
    for (Class<?> importedClass : importAnnotation.value()) {
      buildConfiguraion(importedClass, abd, bm);
    }

    AnnotatedType<?> at = bm.createAnnotatedType(configurationClass);
    //use this to instantiate the class and inject dependencies
    InjectionTarget<Object> it = (InjectionTarget<Object>) bm.createInjectionTarget(at);


    abd.addBean(buildConfigurationBean(bm, configurationClass, it));

    addBeanMethods(configurationClass, abd, bm);

  }

  void validateAnnotationNotPresent(final Class<?> configurationClass, Class<? extends Annotation> annotationClass) {
    Annotation annotation = configurationClass.getAnnotation(annotationClass);
    if (annotation != null) {
      throw new CreationException("@" + annotationClass.getSimpleName() + " on " + configurationClass + " not supported");
    }
  }

  private void addBeanMethods(Class<?> configurationClass, AfterBeanDiscovery abd, BeanManager bm) {
    for (Method method : configurationClass.getMethods()) {
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
    // TODO lazy?
    boolean isLazy = method.getAnnotation(Lazy.class) != null;
    org.springframework.beans.factory.annotation.Qualifier qualifier = method.getAnnotation(org.springframework.beans.factory.annotation.Qualifier.class);
    for (Annotation methodAnnotation : method.getAnnotations()) {
      boolean isQualifierAnnotation = methodAnnotation.annotationType().getAnnotation(org.springframework.beans.factory.annotation.Qualifier.class) != null;
    }
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
        return false;
      }

      @Override
      public Object create(CreationalContext<Object> ctx) {
        Bean<?> bean = getRequiredBean(bm, configurationClass);
        Object configuration = bm.getReference(bean, configurationClass, ctx);
        Object[] parameters = getParameters();
        Object springBean;
        try {
          // TODO null?
          springBean = method.invoke(configuration, parameters);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
          throw new CreationException("could not create bean for: " + method, e);
        }
        if (isAutowire()) {
          // TODO name vs type?
          autowire(springBean, ctx, bm);
        }
        callMethod(getInitMethod(), springBean);
        it.postConstruct(springBean);
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

      private Object[] getParameters() {
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
            parameters[i] = getRequiredBean(bm, parameterTypes[i], findQualifiers(parameterAnnotation));
            
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
        it.dispose(springBean);
        ctx.release();
      }
    };
  }
  
  static String defaultString(String s) {
    return s == null ? "" : s;
  }
  
  private static Object getBean(BeanManager bm, Autowired autowired, Class<?> type, AccessibleObject annotationHolder) {
    if (autowired.required()) {
      return getRequiredBean(bm, type, findQualifiers(annotationHolder));
    } else {
      return getBean(bm, type, findQualifiers(annotationHolder));
    }
  }
  
  private static Object getBean(BeanManager bm, Autowired autowired, Class<?> type, Annotation[] qualifiers) {
    if (autowired.required()) {
      return getRequiredBean(bm, type, qualifiers);
    } else {
      return getBean(bm, type, qualifiers);
    }
  }

  private static Bean<?> getRequiredBean(BeanManager bm, Type type, Annotation... qualifiers) {
    Bean<?> bean = getBean(bm, type, qualifiers);
    if (bean == null) {
      throw new CreationException("no beans for class: " + type + " found");
    }
    return bean;
  }

  private static Bean<?> getBean(BeanManager bm, Type type, Annotation... qualifiers) {
    Set<Bean<?>> beans = bm.getBeans(type, qualifiers);
    if (beans.isEmpty()) {
      return null;
    }
    if (beans.size() > 1) {
      throw new CreationException("multiple beans for class: " + type + " found");
    }
    return beans.iterator().next();
  }

  private boolean isSingleton(org.springframework.context.annotation.Scope scope) {
    if (scope == null) {
      // spring default
      return true;
    }
    return scope.value().equals(ConfigurableBeanFactory.SCOPE_SINGLETON);
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
        // TODO not yet implemented
      case ConfigurableBeanFactory.SCOPE_SINGLETON:
        return ApplicationScoped.class;
      default:
        // ???
        return ApplicationScoped.class;
    }
  }

  private Bean<Object> buildConfigurationBean(BeanManager bm, Class<?> configurationClass, InjectionTarget<Object> it) {
    return new ConfigurationBean(bm, it, configurationClass);
  }
  
  private static void autowire(Object springBean, CreationalContext<Object> ctx, BeanManager bm) {
    Class<? extends Object> current = springBean.getClass();
    while (current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        Autowired autowired = field.getAnnotation(Autowired.class);
        if (autowired != null) {
          Object value = getBean(bm, autowired, field.getType(), field);
          field.setAccessible(true);
          try {
            field.set(springBean, value);
          } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new CreationException("could not set value of: " + field, e);
          }
        }
      }
      
      current = current.getSuperclass();
    }
    
    // TODO non-public?
    for (Method method : springBean.getClass().getMethods()) {
      Autowired autowired = method.getAnnotation(Autowired.class);
      if (autowired != null) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
          Annotation[] parameterAnnotation = parameterAnnotations[i];
          parameters[i] = getBean(bm, autowired, parameterTypes[i], findQualifiers(parameterAnnotation));
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
    private final Class<?> configurationClass;
    private final BeanManager bm;

    ConfigurationBean(BeanManager bm, InjectionTarget<Object> it, Class<?> configurationClass) {
      this.it = it;
      this.bm = bm;
      this.configurationClass = configurationClass;
    }

    @Override
    public Class<?> getBeanClass() {
      return configurationClass;
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
      return Collections.<Type>singleton(configurationClass);
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
      Object configuration;
      try {
        // TODO dynamic subclass
        configuration = configurationClass.getConstructor().newInstance();
      } catch (ReflectiveOperationException | IllegalArgumentException e) {
        throw new CreationException("could not invoke default constructor on: " + configurationClass, e);
      }
      autowire(configuration, ctx, bm);
      it.inject(configuration, ctx);
      it.postConstruct(configuration);
      return configuration;
    }

    @Override
    public void destroy(Object instance, CreationalContext<Object> ctx) {
      it.preDestroy(instance);
      it.dispose(instance);
      ctx.release();
    }
  }

}
