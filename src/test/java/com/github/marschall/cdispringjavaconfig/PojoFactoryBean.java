package com.github.marschall.cdispringjavaconfig;

import org.springframework.beans.factory.FactoryBean;

public class PojoFactoryBean implements FactoryBean<Pojo> {

  @Override
  public Pojo getObject() throws Exception {
    return new SimplePojo();
  }

  @Override
  public Class<?> getObjectType() {
    return Pojo.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

}
