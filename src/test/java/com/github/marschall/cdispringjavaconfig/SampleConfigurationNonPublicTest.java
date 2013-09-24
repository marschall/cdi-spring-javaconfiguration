package com.github.marschall.cdispringjavaconfig;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SampleConfigurationNonPublic.class)
public class SampleConfigurationNonPublicTest {
  
  @Autowired
  private Pojo pojo;

  @Test
  public void nonPublicBean() {
    assertNotNull(this.pojo);
  }
  
  @Test
  public void nonPublicAutowired() {
    assertTrue(SampleConfigurationNonPublic.isApplicationContextCalled());
  }

}
