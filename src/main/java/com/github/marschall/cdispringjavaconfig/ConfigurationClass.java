package com.github.marschall.cdispringjavaconfig;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Determines which configuration class a managed object (eg. EJB) should use.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigurationClass {

  /**
   * Returns the spring configuration class (annotated with
   * {@link org.springframework.context.annotation.Configuration}) that should
   * be used to configure a managed object (eg. EJB).
   *
   * @return the configuration class to use
   */
  Class<?>[] value();

}
