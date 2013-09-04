package com.github.marschall.cdispringjavaconfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
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
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.util.AnnotationLiteral;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowire;
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

  public void processInjectionTarget (@Observes ProcessInjectionTarget<?> pit, BeanManager bm) {
    InjectionTarget<?> it = pit.getInjectionTarget();
    for (InjectionPoint ip : it.getInjectionPoints()) {
      Annotated annotated = ip.getAnnotated();
      Autowired autowired = annotated.getAnnotation(Autowired.class);

      // TODO qualifiers
      Bean<?> bean;
      if (autowired.required()) {
        bean = getRequiredBean(bm, getClass());
      } else {
        bean = getBean(bm, getClass());

      }
      if (bean != null) {
        // TODO
        // pit.getInjectionTarget().;
      }
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


    abd.addBean(buildConfigurationBean(configurationClass, it));

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
        return method.getName();
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
        // TODO find org.springframework.stereotype.Service
        return null;
      }

      @Override
      public Set<Type> getTypes() {
        return Collections.<Type>singleton(method.getReturnType());
      }

      @Override
      public boolean isAlternative() {
        // TODO qualifier?
        return false;
      }

      @Override
      public boolean isNullable() {
        return false;
      }

      @Override
      public Object create(CreationalContext<Object> ctx) {
        Bean<?> bean = getRequiredBean(bm, configurationClass);
        Object configuration = bm.getReference(bean, configurationClass, ctx);
        // TODO inject parameters
        // TODO null?
        Object springBean;
        try {
          springBean = method.invoke(configuration);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
          throw new CreationException("could not create bean for: " + method, e);
        }
        // TODO factory bean
        if (springBean instanceof InitializingBean) {
          try {
            ((InitializingBean) springBean).afterPropertiesSet();
          } catch (Exception e) {
            throw new CreationException("could initialize bean: " + springBean + " for: " + method, e);
          }
        }
        callMethod(getInitMethod(), springBean);
        it.postConstruct(springBean);
        return springBean;
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
        return getBeanAnnotation().autowire() != Autowire.NO;
      }
      
      private String getInitMethod() {
        return defaultString(getBeanAnnotation().initMethod());
      }
      
      private String getDestroyMethod() {
        return defaultString(getBeanAnnotation().destroyMethod());
      }
      
      private org.springframework.context.annotation.Bean getBeanAnnotation() {
        return method.getAnnotation(org.springframework.context.annotation.Bean.class);
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

  private static Bean<?> getRequiredBean(BeanManager bm, Type type, Annotation... qualifiers) {
    Bean<?> bean = getBean(bm, type, qualifiers);
    if (bean == null) {
      throw new CreationException("multiple beans for configuration class: " + type + " found");
    }
    return bean;
  }

  private static Bean<?> getBean(BeanManager bm, Type type, Annotation... qualifiers) {
    Set<Bean<?>> beans = bm.getBeans(type);
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

  private <A extends Annotation> A findAnnotation(Method method, Class<A> annotationClass) {
    A annotation = method.getAnnotation(annotationClass);
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
    for (Annotation methodAnnotation : method.getAnnotations()) {
      A typeAnnotation = methodAnnotation.annotationType().getAnnotation(annotationClass);
      if (typeAnnotation != null) {
        return typeAnnotation;
      }
    }

    // nothing found
    return null;
  }

  private Annotation findQualifier(Method method) {
    Annotation annotation = method.getAnnotation(org.springframework.beans.factory.annotation.Qualifier.class);
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
    for (Annotation methodAnnotation : method.getAnnotations()) {
      Annotation typeAnnotation = methodAnnotation.annotationType().getAnnotation(org.springframework.beans.factory.annotation.Qualifier.class);
      if (typeAnnotation != null) {
        return methodAnnotation;
      }
    }

    // nothing found
    return null;
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

  private Bean<Object> buildConfigurationBean(Class<?> configurationClass, InjectionTarget<Object> it) {
    return new ConfigurationBean(it, configurationClass);
  }

  static final class ConfigurationBean implements Bean<Object> {

    private final InjectionTarget<Object> it;
    private final Class<?> configurationClass;

    ConfigurationBean(InjectionTarget<Object> it, Class<?> configurationClass) {
      this.it = it;
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
      // TODO aliases
      return configurationClass.getSimpleName();
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
      } catch (ReflectiveOperationException e) {
        throw new CreationException("could not invoke default constructor on: " + configurationClass, e);
      }
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
