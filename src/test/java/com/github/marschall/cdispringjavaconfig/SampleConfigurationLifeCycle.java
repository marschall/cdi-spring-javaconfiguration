package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SampleConfigurationLifeCycle {

  @Bean(initMethod = "initMethod", destroyMethod = "destroyMethod")
  public Pojo pojo() {
    return new LifeCyclePojo();
  }

}
