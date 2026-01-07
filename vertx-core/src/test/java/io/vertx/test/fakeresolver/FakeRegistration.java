package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.endpoint.ServerEndpoint;

import java.util.ArrayList;
import java.util.List;

public class FakeRegistration {

  List<SocketAddress> servers;
  volatile FakeState<?> state;

  FakeRegistration(List<SocketAddress> servers) {
    this.servers = servers;
  }

  public FakeState<?> state() {
    return state;
  }

  public void update(List<SocketAddress> update) {
    servers = update;
    FakeState<?> s = state;
    if (s != null) {
      s.refresh();
    }
  }

  public List<FakeServerEndpoint> endpoints() {
    FakeState<?> s = state;
    if (s != null) {
      List<FakeServerEndpoint> list = new ArrayList<>();
      for (Object o : ((Iterable) s.endpoints)) {
        ServerEndpoint instance = (ServerEndpoint) o;
        list.add((FakeServerEndpoint) instance.unwrap());
      }
      return list;
    } else {
      return null;
    }
  }
}
