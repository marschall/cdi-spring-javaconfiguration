package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SampleConfigurationNonPublic {

  @Bean
  protected Pojo pojo() {
    return new SimplePojo();
  }

}
