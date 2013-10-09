package com.github.marschall.cdispringjavaconfig;

import static org.junit.Assert.assertNotNull;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class SampleEjbTest {

  @Inject
  SampleEjb sampleEjb;

  @Deployment
  public static Archive<?> createDeployment() {
    JavaArchive ejbJar = ShrinkWrap.create(JavaArchive.class)
        .addClass(SampleEjb.class)
        .addClass(Pojo.class)
    /* .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") */;
    JavaArchive libraryJar = ShrinkWrap.create(JavaArchive.class)
        // src/main/java and src/main/resources both end up there
        .addAsDirectory("target/classes");
    return ShrinkWrap.create(EnterpriseArchive.class)
      .addAsModule(ejbJar)
      .addAsLibrary(libraryJar)
      /* spring-beans -> lib */
      /* spring-context -> lib */
      /* asm -> lib */
      ;
  }

  @Test
  public void injection() {
    assertNotNull(this.sampleEjb);
    assertNotNull(this.sampleEjb.getPojo());
  }

}
