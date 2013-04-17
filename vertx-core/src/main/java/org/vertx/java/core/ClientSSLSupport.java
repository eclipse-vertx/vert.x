package org.vertx.java.core;

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
public interface ClientSSLSupport<T> extends SSLSupport<T> {

  /**
   * If you want an SSL client to trust *all* server certificates rather than match them
   * against those in its trust store, you can set this to true.<p>
   * Use this with caution as you may be exposed to "main in the middle" attacks
   * @param trustAll Set to true if you want to trust all server certificates
   */
  T setTrustAll(boolean trustAll);

  /**
   *
   * @return true if this client will trust all server certificates.
   */
  boolean isTrustAll();
}
