/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.dns;

/**
 * Exception which is used to notify the {@link org.vertx.java.core.AsyncResult}
 * if the DNS query returns a {@link DnsResponseCode} which indicates and error.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class DnsException extends Exception {

  private DnsResponseCode code;

  public DnsException(DnsResponseCode code) {
    if (code == null) {
      throw new NullPointerException("code");
    }
    this.code = code;
  }

  /**
   * The {@link DnsResponseCode} which caused this {@link DnsException} to be created.
   */
  public DnsResponseCode code() {
    return code;
  }
}
