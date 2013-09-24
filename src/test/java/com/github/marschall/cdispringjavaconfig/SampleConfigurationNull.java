package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SampleConfigurationNull {

  @Bean
  public Pojo pojo() {
    return null;
  }

}
