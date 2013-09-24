package com.github.marschall.cdispringjavaconfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Singleton;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Implements a {@link BeanFactory} using {@link BeanManager}
 *
 * @see org.springframework.beans.factory.BeanFactoryAware
 */
class BeanFactoryBridge implements BeanFactory {

  private final BeanManager beanManager;
  // FIXME
  private final CreationalContext context;

  BeanFactoryBridge(BeanManager beanManager, CreationalContext context) {
    this.beanManager = beanManager;
    this.context = context;
  }

  @Override
  public Object getBean(String name) throws BeansException {
    return this.getSingleSpringBean(name, this.beanManager.getBeans(name));
  }

  private Bean<?> getSingleCdiBean(String name) {
    Set<Bean<?>> beans = this.beanManager.getBeans(name);
    return this.getSingleCdiBean(name, beans);
  }

  private Bean<?> getSingleCdiBean(String name, Set<Bean<?>> beans) {
    if (beans.isEmpty()) {
      throw new NoSuchBeanDefinitionException(name);
    }
    if (beans.size() > 1) {
      throw new NoSuchBeanDefinitionException(name, "more than one bean found for \"" + name + "\" found:" + beans);
    }
    return beans.iterator().next();
  }
  
  private Bean<?> getSingleCdiBean(Class<?> type, Set<Bean<?>> beans) {
    if (beans.isEmpty()) {
      throw new NoSuchBeanDefinitionException(type);
    }
    if (beans.size() > 1) {
      throw new NoSuchBeanDefinitionException(type, "more than one bean found for \"" + type + "\" found:" + beans);
    }
    return beans.iterator().next();
  }

  private Object getSingleSpringBean(String name, Set<Bean<?>> beans) {
    Bean<Object> cdiBean = (Bean<Object>) this.getSingleCdiBean(name, beans);
    return cdiBean.create(this.context);
  }

  private Object getSingleSpringBean(Set<Bean<?>> beans, Class<?> requiredType) {
    Bean bean = this.getSingleCdiBean(requiredType, beans);
    return bean.create(this.context);
  }

  @Override
  public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
    // TODO requiredType instead of name?
    return (T) this.getSingleSpringBean(this.beanManager.getBeans(name), requiredType);
  }

  @Override
  public <T> T getBean(Class<T> requiredType) throws BeansException {
    return (T) this.getSingleSpringBean(this.beanManager.getBeans(requiredType), requiredType);
  }

  @Override
  public Object getBean(String name, Object... args) throws BeansException {
    // TODO factory bean
    return getSingleSpringBean(name, this.beanManager.getBeans(name));
  }

  @Override
  public boolean containsBean(String name) {
    return !this.beanManager.getBeans(name).isEmpty();
  }

  @Override
  public boolean isSingleton(String name) {
    Bean<?> cdiBean = this.getSingleCdiBean(name);
    Class<? extends Annotation> scope = cdiBean.getScope();
    if (scope == Singleton.class) {
      return true;
    }
    // TODO default scope
    return scope.getAnnotation(Singleton.class) != null;
  }

  @Override
  public boolean isPrototype(String name) {
    Bean<?> cdiBean = this.getSingleCdiBean(name);
    Class<? extends Annotation> scope = cdiBean.getScope();
    if (scope == Dependent.class) {
      return true;
    }
    // TODO default scope
    return scope.getAnnotation(Dependent.class) != null;
  }

  @Override
  public boolean isTypeMatch(String name, Class<?> targetType) {
    Bean<?> cdiBean = this.getSingleCdiBean(name);
    for (Type type : cdiBean.getTypes()) {
      if (type instanceof Class) {
        Class<?> clazz = (Class<?>) type;
        if (targetType.isAssignableFrom(clazz)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public Class<?> getType(String name) {
    Bean<?> cdiBean = this.getSingleCdiBean(name);
    return cdiBean.getBeanClass();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] getAliases(String name) {
    // TODO
    return new String[0];
  }

}
