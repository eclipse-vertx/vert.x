package org.vertx.java.core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface SSLSupport<T> {

  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  T setSSL(boolean ssl);

  /**
   *
   * @return Is SSL enabled?
   */
  boolean isSSL();

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and will contain the client certificate. Client certificates are
   * only required if the server requests client authentication.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  T setKeyStorePath(String path);

  /**
   *
   * @return Get the key store path
   */
  String getKeyStorePath();

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  T setKeyStorePassword(String pwd);

  /**
   *
   * @return Get the key store password
   */
  String getKeyStorePassword();

  /**
   * Set the array of custom trust managers. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   *
   * Custom trust managers allow to verify peer identity when it's not possible to do it using standard Java Key Store.<p>
   *
   * Only the first instance of a particular trust manager implementation type is used when verifying peer identity.
   * Custom trust managers specified by this method will have higher priority than TrustManagers loaded from Java Key Store
   * as specified by {@link #setTrustStorePath(java.lang.String) }.
   *
   * @return A reference to this, so multiple invocations can be chained together.
   *
   * @see SSLContext#init(javax.net.ssl.KeyManager[], javax.net.ssl.TrustManager[], java.security.SecureRandom)
   * @see #setTrustStorePath(java.lang.String)
   */
  T setTrustManagers(TrustManager[] trustManagerFactory);

  /**
   *
   * @return The array of custom trust managers
   */
  TrustManager[] getTrustManagers();

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   *
   * The trust store is a standard Java Key Store, and should contain the certificates of any servers that the client trusts.
   *
   * Only the first instance of a particular trust manager implementation type is used when verifying peer identity.
   * Custom trust managers specified by {@link #setTrustManagers(javax.net.ssl.TrustManager[]) } method will have higher priority
   * than TrustManagers loaded from Java Key Store as specified by this method.
   *
   * @return A reference to this, so multiple invocations can be chained together.
   *
   * @see SSLContext#init(javax.net.ssl.KeyManager[], javax.net.ssl.TrustManager[], java.security.SecureRandom)
   * @see #setTrustManagers(javax.net.ssl.TrustManager[])
   */
  T setTrustStorePath(String path);

  /**
   *
   * @return Get the trust store path
   */
  String getTrustStorePath();

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  T setTrustStorePassword(String pwd);

  /**
   *
   * @return Get trust store password
   */
  String getTrustStorePassword();
}
