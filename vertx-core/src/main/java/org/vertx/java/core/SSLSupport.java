/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core;

import javax.net.ssl.SSLContext;

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
   * Set the SSL context explicitly.  This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL context has to be properly initialized.
   * Only use this method if you have very special requirements concerning your key managers, trust managers and/or
   * corresponding stores.
   */
  T setSSLContext(SSLContext sslContext);

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
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of any servers that the client trusts.
   * @return A reference to this, so multiple invocations can be chained together.
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
