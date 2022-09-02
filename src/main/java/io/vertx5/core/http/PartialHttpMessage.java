package io.vertx5.core.http;

import io.netty5.handler.codec.http.HttpContent;
import io.netty5.handler.codec.http.HttpMessage;

abstract class PartialHttpMessage<R extends PartialHttpMessage<R>> implements HttpMessage, HttpContent<R> {

}
