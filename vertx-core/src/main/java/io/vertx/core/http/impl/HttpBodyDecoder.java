package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;

public interface HttpBodyDecoder {

  int handle(ByteBuf content);

  void next();

  void end();

}
