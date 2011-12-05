package org.vertx.java.core.http.ws;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Handshake {

  void fillInRequest(HttpClientRequest req, String hostHeader) throws Exception;

  void onComplete(HttpClientResponse response, CompletionHandler<Void> doneHandler) throws Exception;

  HttpResponse generateResponse(HttpRequest request) throws Exception;

  ChannelHandler getEncoder(boolean server);

  ChannelHandler getDecoder();
}
