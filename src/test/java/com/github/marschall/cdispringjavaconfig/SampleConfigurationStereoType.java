package com.github.marschall.cdispringjavaconfig;

import org.springframework.context.annotation.Configuration;


@Configuration
public class SampleConfigurationStereoType {

  @CustomStereotype
  public Pojo pojo() {
    return new SimplePojo();
  }

}
