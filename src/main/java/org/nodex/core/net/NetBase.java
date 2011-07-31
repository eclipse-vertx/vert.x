/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.net;

import javax.net.ssl.SSLContext;

class NetBase {

  protected boolean ssl;
  protected String keyStorePath;
  protected String keyStorePassword;
  protected String trustStorePath;
  protected String trustStorePassword;
  protected boolean trustAll;

  protected SSLContext context;

  public boolean isSsl() {
    return ssl;
  }

  public String getKeyStorePath() {
    return keyStorePath;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public String getTrustStorePath() {
    return trustStorePath;
  }

  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  public boolean isTrustAll() {
    return trustAll;
  }

  protected void checkSSL() {
    if (ssl) {
      context = TLSHelper.createContext(keyStorePath, keyStorePassword, trustStorePath, trustStorePassword, trustAll);
    }
  }
}
