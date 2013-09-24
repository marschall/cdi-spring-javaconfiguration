package com.github.marschall.cdispringjavaconfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SampleConfigurationNonPublic {

  private static boolean application_context_called = false;

  @Bean
  protected Pojo pojo() {
    return new SimplePojo();
  }
  
  @Autowired
  private void setApplicationContext(ApplicationContext applicationContext) {
    application_context_called = true;
  }
  
  static boolean isApplicationContextCalled() {
    return application_context_called;
  }

}
