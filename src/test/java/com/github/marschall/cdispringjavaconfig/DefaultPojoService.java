package com.github.marschall.cdispringjavaconfig;

import static java.util.Objects.requireNonNull;

public class DefaultPojoService implements PojoService {
  
  private final Pojo pojo;

  public DefaultPojoService(Pojo pojo) {
    requireNonNull(pojo);
    this.pojo = pojo;
  }

}
