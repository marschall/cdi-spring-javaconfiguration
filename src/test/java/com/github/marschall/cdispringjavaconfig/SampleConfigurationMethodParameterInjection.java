package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SampleConfigurationMethodParameterInjection {
  
  @Bean
  public Pojo pojo() {
    return new SimplePojo();
  }

  @Bean
  public PojoService pojoService(Pojo pojo) {
    return new DefaultPojoService(pojo);
  }

}
