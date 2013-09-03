package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SampleConfiguration {

  public SampleConfiguration() {
    // for debug purposes
    super();
  }

  @Bean
  public Pojo pojo() {
    return new SimplePojo();
  }

}
