package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SampleConfigurationPostConstruct {

  @Bean
  public Pojo pojo() {
    return new PostConstructPojo();
  }

}
