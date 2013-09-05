package com.github.marschall.cdispringjavaconfig;

import javax.inject.Singleton;

import org.springframework.beans.factory.annotation.Autowired;

@Singleton
@ConfigurationClasses(SampleConfiguration.class)
public class SampleEjb {
  
  @Autowired
  private Pojo pojo;
  
  public Pojo getPojo() {
    return pojo;
  }

}
