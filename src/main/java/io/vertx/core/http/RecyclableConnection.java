package io.vertx.core.http;

import io.vertx.core.http.impl.pool.ConnectionListener;

public interface RecyclableConnection {
  void clean();

  void setConnectionListener(ConnectionListener connectionListener);
}
