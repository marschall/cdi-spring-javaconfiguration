package com.github.marschall.cdispringjavaconfig;

import javax.annotation.PostConstruct;

public class PostConstructPojo implements Pojo {
  
  private boolean postConstructCalled;

  @PostConstruct
  public void postConstruct() {
    this.postConstructCalled = true;
  }
  
  public boolean isPostConstructCalled() {
    return postConstructCalled;
  }

}
