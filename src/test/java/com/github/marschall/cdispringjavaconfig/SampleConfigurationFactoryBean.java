package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SampleConfigurationFactoryBean {

  @Bean
  public Object pojo() {
    return new PojoFactoryBean();
  }

  
}
