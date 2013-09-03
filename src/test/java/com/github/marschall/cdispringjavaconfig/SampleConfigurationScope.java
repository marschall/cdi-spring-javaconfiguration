package com.github.marschall.cdispringjavaconfig;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;


@Configuration
public class SampleConfigurationScope {

  @Bean
  @Scope(SCOPE_PROTOTYPE)
  public Pojo pojo() {
    return new SimplePojo();
  }

}
