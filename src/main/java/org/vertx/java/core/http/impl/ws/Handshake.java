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

package org.vertx.java.core.http.impl.ws;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Handshake {

  void fillInRequest(HttpClientRequest req, String hostHeader) throws Exception;

  void onComplete(HttpClientResponse response, AsyncResultHandler<Void> doneHandler) throws Exception;

  HttpResponse generateResponse(HttpRequest request, String serverOrigin) throws Exception;

  ChannelHandler getEncoder(boolean server);

  ChannelHandler getDecoder();
}
