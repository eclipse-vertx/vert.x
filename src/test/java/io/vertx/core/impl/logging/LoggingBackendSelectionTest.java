/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.logging;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;
import static org.junit.Assert.assertEquals;


/**
 * @author Thomas Segismont
 */
public class LoggingBackendSelectionTest {

  private ClassLoader originalTccl;
  private TestClassLoader testClassLoader;

  @Before
  public void setup() {
    originalTccl = Thread.currentThread().getContextClassLoader();
    testClassLoader = new TestClassLoader(originalTccl);
    Thread.currentThread().setContextClassLoader(testClassLoader);
  }

  @After
  public void tearDown() {
    Thread.currentThread().setContextClassLoader(originalTccl);
    System.clearProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME);
  }

  @Test
  public void syspropPriority() throws Exception {
    System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");
    assertEquals("Log4j2", loggingBackend());
  }

  @Test
  public void vertxJulFilePriority() throws Exception {
    assertEquals("JUL", loggingBackend());
  }

  @Test
  public void SLF4JPriority() throws Exception {
    testClassLoader.hideVertxJulFile = true;
    assertEquals("SLF4J", loggingBackend());
  }

  @Test
  public void Log4j2Priority() throws Exception {
    testClassLoader.hideVertxJulFile = true;
    testClassLoader.hiddenPackages.add("org.slf4j");
    assertEquals("Log4j2", loggingBackend());
  }

  @Test
  public void JULDefault() throws Exception {
    testClassLoader.hideVertxJulFile = true;
    testClassLoader.hiddenPackages.add("org.slf4j");
    testClassLoader.hiddenPackages.add("org.apache.logging");
    assertEquals("JUL", loggingBackend());
  }

  private String loggingBackend() throws Exception {
    Class<?> factoryClass = testClassLoader.loadClass("io.vertx.core.impl.logging.LoggerFactory");
    Method factoryMethod = factoryClass.getMethod("getLogger", String.class);
    Object loggerAdapter = factoryMethod.invoke(null, "whatever");
    Field adaptedField = loggerAdapter.getClass().getDeclaredField("adapted");
    adaptedField.setAccessible(true);
    Object adapted = adaptedField.get(loggerAdapter);
    String backendClass = adapted.getClass().getSimpleName();
    return backendClass.substring(0, backendClass.indexOf("LogDelegate"));
  }

  private static class TestClassLoader extends ClassLoader {

    boolean hideVertxJulFile;
    Set<String> hiddenPackages = new HashSet<>();

    TestClassLoader(ClassLoader parent) {
      super(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (hiddenPackages.stream().anyMatch(name::startsWith)) {
        throw new ClassNotFoundException(name);
      }
      if (name.startsWith("io.vertx.core.impl.logging") || name.startsWith("io.vertx.core.logging")) {
        URL url = getResource(name.replace('.', '/') + ".class");
        if (url == null) {
          throw new ClassNotFoundException(name);
        }
        try (InputStream in = url.openStream()) {
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          byte[] buff = new byte[256];
          int l;
          while ((l = in.read(buff)) != -1) {
            baos.write(buff, 0, l);;
          }
          byte[] bytes = baos.toByteArray();
          Class<?> clazz = defineClass(name, bytes, 0, bytes.length);
          if (resolve) {
            resolveClass(clazz);
          }
          return clazz;
        } catch (IOException e) {
          throw new ClassNotFoundException(name, e);
        }
      }
      return super.loadClass(name, resolve);
    }

    @Override
    public URL getResource(String name) {
      if (hideVertxJulFile && "vertx-default-jul-logging.properties".equals(name)) {
        return null;
      }
      return super.getResource(name);
    }
  }
}
