package com.github.marschall.cdispringjavaconfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SampleConfigurationNonPublic {

  private boolean applicationContextCalled = false;

  @Bean
  protected Pojo pojo() {
    return new SimplePojo();
  }
  
  @Autowired
  private void setApplicationContext(ApplicationContext applicationContext) {
    applicationContextCalled = true;
  }
  
  boolean isApplicationContextCalled() {
    return applicationContextCalled;
  }

}
