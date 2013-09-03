package com.github.marschall.cdispringjavaconfig;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SampleConfigurationQualifier {

  @Bean
  @Qualifier("sampleQualifier")
  public Pojo pojo() {
    return new SimplePojo();
  }

}
