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

package io.vertx.core.net;

import io.netty.handler.ssl.SslProvider;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.SslEngineUtils;
import io.vertx.core.spi.tls.DefaultSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Configures a {@link TCPSSLOptions} to use the JDK ssl engine implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class JdkSSLEngineOptions extends SSLEngineOptions {
  private static Boolean jdkAlpnAvailable;
  private static Boolean jdkPqcAvailable;

  /**
   * @return if alpn support is available via the JDK SSL engine
   */
  public static synchronized boolean isAlpnAvailable() {
    if (jdkAlpnAvailable == null) {
      boolean available = false;
      try {
        // Public method on SSLEngine
        SSLEngine.class.getDeclaredMethod("getApplicationProtocol");
        available = true;
      } catch (Exception ignore1) {
        // JDK provides ALPN
        try {
          // Always use bootstrap class loader.
          JdkSSLEngineOptions.class.getClassLoader().loadClass("sun.security.ssl.ALPNExtension");
           available = true;
        } catch (Exception ignore2) {
          // alpn-boot was not loaded.
        }
      } finally {
        jdkAlpnAvailable = available;
      }
    }
    return jdkAlpnAvailable;
  }

  /**
   * @return if PQC key exchange is available via the JDK SSL engine, i.e. the JDK supports
   * at least one of the PQ-compliant named groups (X25519MLKEM768, SecP256r1MLKEM768, SecP384r1MLKEM1024)
   */
  public static synchronized boolean isPqcAvailable() {
    if (jdkPqcAvailable == null) {
      boolean available = false;
      try {
        SSLContext ctx = SSLContext.getDefault();
        SSLParameters params = ctx.getDefaultSSLParameters();
        Method getNamedGroups = SSLParameters.class.getDeclaredMethod("getNamedGroups");
        String[] groups = (String[]) getNamedGroups.invoke(params);
        available = groups != null && Arrays.stream(groups).anyMatch(SslEngineUtils.getPqCompliantGroups()::contains);
      } catch (Exception ignore) {
      }
      jdkPqcAvailable = available;
    }
    return jdkPqcAvailable;
  }

  private boolean pooledHeapBuffers = false;

  public JdkSSLEngineOptions() {
  }

  public JdkSSLEngineOptions(JsonObject json) {
    super(json);
    pooledHeapBuffers = json.getBoolean("pooledHeapBuffers", false);
  }

  public JdkSSLEngineOptions(JdkSSLEngineOptions that) {
    super(that);
    pooledHeapBuffers = that.pooledHeapBuffers;
  }

  /**
   * Set whether to use pooled heap buffers. Default is {@code false}, but it is recommended to use pooled buffers
   */
  public JdkSSLEngineOptions setPooledHeapBuffers(boolean pooledHeapBuffers) {
    this.pooledHeapBuffers = pooledHeapBuffers;
    return this;
  }

  public boolean isPooledHeapBuffers() {
    return pooledHeapBuffers;
  }

  @Override
  public JdkSSLEngineOptions setUseWorkerThread(boolean useWorkerThread) {
    return (JdkSSLEngineOptions) super.setUseWorkerThread(useWorkerThread);
  }

  public JsonObject toJson() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("pooledHeapBuffers", pooledHeapBuffers);
    return jsonObject;
  }

  @Override
  public JdkSSLEngineOptions copy() {
    return new JdkSSLEngineOptions(this);
  }

  @Override
  public SslContextFactory sslContextFactory() {
    return new DefaultSslContextFactory(SslProvider.JDK, false);
  }
}
