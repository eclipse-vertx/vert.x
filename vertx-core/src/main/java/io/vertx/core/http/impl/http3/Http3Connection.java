package io.vertx.core.http.impl.http3;

import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.List;

public abstract class Http3Connection implements HttpConnection {

  final ContextInternal context;
  final QuicConnectionInternal connection;

  public Http3Connection(QuicConnectionInternal connection) {
    this.context = connection.context();
    this.connection = connection;
  }

  @Override
  public final SocketAddress remoteAddress() {
    return connection.remoteAddress();
  }

  @Override
  public final SocketAddress remoteAddress(boolean real) {
    return connection.remoteAddress();
  }

  @Override
  public final SocketAddress localAddress() {
    return connection.localAddress();
  }

  @Override
  public final SocketAddress localAddress(boolean real) {
    return connection.localAddress();
  }

  @Override
  public final boolean isSsl() {
    return true;
  }

  @Override
  public final SSLSession sslSession() {
    return connection.sslSession();
  }
}
