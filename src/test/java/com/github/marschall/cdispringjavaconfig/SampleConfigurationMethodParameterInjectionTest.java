package com.github.marschall.cdispringjavaconfig;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SampleConfigurationMethodParameterInjection.class)
public class SampleConfigurationMethodParameterInjectionTest {
  
  @Autowired
  private PojoService pojoService;

  @Test
  public void methodParameterInjection() {
    assertNotNull(this.pojoService);
  }

}
