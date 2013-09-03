package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


@Configuration
public class SampleConfigurationPrimary {

  @Bean
  public Pojo secondaryPojo() {
    return new SimplePojo();
  }

  @Bean
  @Primary
  public Pojo primaryPojo() {
    return new SimplePojo();
  }

}
