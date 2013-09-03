package com.github.marschall.cdispringjavaconfig;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Allows to turn {@code byte[]}s into classes.
 * <p>
 * We dynamically generate subclasses of configuration classes we can't put
 * them into existing class loaders so we have to put them into custom class
 * loaders.
 */
class AdHocClassLoader extends ClassLoader {

  static {
    registerAsParallelCapable();
  }

  // we optimize for #findClass and not #findResource so we use the class name
  // instead of the resource name as key
  private final Map<String, byte[]> additionalClasses;

  AdHocClassLoader(ClassLoader parent, Map<String, byte[]> additionalClasses) {
    super(parent);
    this.additionalClasses = additionalClasses;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    byte[] byteCode = this.additionalClasses.get(name);
    if (byteCode != null) {
      return this.defineClass(name, byteCode, 0, byteCode.length);
    } else {
      return super.findClass(name);
    }
  }

  @Override
  protected URL findResource(String name) {
    if (name.endsWith(".class")) {
      // com/acme/Application.class -> com.acme.Application
      String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
      byte[] byteCode = this.additionalClasses.get(className);
      if (byteCode != null) {
        try {
          return new URL(null, "adhoc://" + System.identityHashCode(this) + "/" + name,
              new ByteArrayURLStreamHandler(byteCode));
        } catch (MalformedURLException e) {
          throw new RuntimeException("could not create URL", e);
        }
      }
    }
    return super.findResource(name);
  }

}
