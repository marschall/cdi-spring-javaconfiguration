package com.github.marschall.cdispringjavaconfig;

import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SampleConfigurationNull.class)
public class SampleConfigurationNullTest {
  
  @Autowired
  private Pojo pojo;

  @Test
  public void nullPojo() {
    assertNull(this.pojo);
  }

}
