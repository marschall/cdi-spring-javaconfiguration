package com.github.marschall.cdispringjavaconfig;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;


public class ProxySubclassGeneratorTest {

  @Test
  public void basicClassGeneration() throws ReflectiveOperationException, IOException {
    Class<SampleConfiguration> configurationClass = SampleConfiguration.class;
    List<Method> methods = Collections.singletonList(configurationClass.getMethod("pojo"));
    ProxySubclassGenerator generator = new ProxySubclassGenerator(configurationClass, methods);
    byte[] byteCode = generator.generate();

    String generatedClassName = generator.getClassName();
    ClassLoader classLoader = new AdHocClassLoader(configurationClass.getClassLoader(), Collections.singletonMap(generatedClassName, byteCode));

    Class<?> configurationSubclass;
    try {
      configurationSubclass = Class.forName(generatedClassName, true, classLoader);
    } catch (ClassFormatError e) {
      // TODO dump class
      throw e;
    }
    Object configuration = configurationSubclass.getConstructor().newInstance();
    Object pojo1 = configurationSubclass.getMethod("pojo").invoke(configuration);
    Object pojo2 = configurationSubclass.getMethod("pojo").invoke(configuration);
    assertSame(pojo1, pojo2);

    URL resource = classLoader.getResource(generatedClassName.replace('.', '/') + ".class");
    assertNotNull(resource);
    URLConnection connection = resource.openConnection();
    connection.connect();
    assertEquals(0L, connection.getDate());
    assertEquals(0L, connection.getLastModified());
    int contentLength = connection.getContentLength();
    assertEquals(byteCode.length, contentLength);
    assertEquals(byteCode.length, connection.getContentLengthLong());
    assertEquals("application/octet-stream", connection.getContentType());

    // TODO: get content
    try (InputStream stream = connection.getInputStream()) {
      byte[] readBack = new byte[contentLength];
      int read = stream.read(readBack);
      assertEquals(contentLength, read);
      assertEquals("stream not at end", -1, stream.read());
      assertArrayEquals(byteCode, readBack);
    }

  }

}
