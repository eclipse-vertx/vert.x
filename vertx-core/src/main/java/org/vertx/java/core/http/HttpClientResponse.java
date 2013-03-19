/*
 * Copyright 2011-2012 the original author or authors.
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

package org.vertx.java.core.http;

import org.vertx.java.core.http.impl.HttpReadStreamBase;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Represents a client-side HTTP response.<p>
 * An instance of this class is provided to the user via a {@link org.vertx.java.core.Handler}
 * instance that was specified when one of the HTTP method operations, or the
 * generic {@link HttpClient#request(String, String, org.vertx.java.core.Handler)}
 * method was called on an instance of {@link HttpClient}.<p>
 * It implements {@link org.vertx.java.core.streams.ReadStream} so it can be used with
 * {@link org.vertx.java.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class HttpClientResponse extends HttpReadStreamBase {

  private static final Logger log = LoggerFactory.getLogger(HttpClientResponse.class);

  protected HttpClientResponse(int statusCode, String statusMessage) {
    this.statusCode = statusCode;
    this.statusMessage = statusMessage;
  }

  /**
   * The HTTP status code of the response
   */
  public final int statusCode;

  /**
   * The HTTP status message of the response
   */
  public final String statusMessage;

  /**
   * @return The HTTP headers
   */
  public abstract Map<String, String> headers();

  /**
   * @return The HTTP trailers
   */
  public abstract Map<String, String> trailers();

  /**
   * @return The Set-Cookie headers (including trailers)
   */
  public abstract List<String> cookies();
}
