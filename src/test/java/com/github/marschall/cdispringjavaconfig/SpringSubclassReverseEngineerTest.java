package com.github.marschall.cdispringjavaconfig;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SampleConfiguration.class)
public class SpringSubclassReverseEngineerTest {

  @Autowired
  private SampleConfiguration configuration;

  @Test
  public void classLoader() {
    assertNotNull(this.configuration);
    assertNotSame(SampleConfiguration.class, this.configuration.getClass());

//    assertNotSame(SampleConfiguration.class.getClassLoader(), this.configuration.getClass().getClassLoader());
  }


}
