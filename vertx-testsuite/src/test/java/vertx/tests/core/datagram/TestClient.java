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

package vertx.tests.core.datagram;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.*;
import org.vertx.java.core.datagram.DatagramPacket;
import org.vertx.java.testframework.TestClientBase;
import org.vertx.java.testframework.TestUtils;

import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class TestClient extends TestClientBase {
  private DatagramChannel client;
  private BoundDatagramChannel server;

  public void testEchoConnected() {
    final DatagramEndpoint endpoint = vertx.createDatagramEndpoint();

    endpoint.bind(new InetSocketAddress("localhost", 1234), new AsyncResultHandler<BoundDatagramChannel>() {
      @Override
      public void handle(AsyncResult<BoundDatagramChannel> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());
        final Buffer buffer = TestUtils.generateRandomBuffer(128);
        server = event.result();
        endpoint.connect(server.localAddress(), new AsyncResultHandler<ConnectedDatagramChannel>() {
          @Override
          public void handle(AsyncResult<ConnectedDatagramChannel> event) {
            tu.checkThread();
            tu.azzert(event.succeeded());

            final ConnectedDatagramChannel connected = event.result();
            client = connected;
            tu.azzert(connected.remoteAddress().equals(server.localAddress()));

            server.dataHandler(new Handler<DatagramPacket>() {
              @Override
              public void handle(DatagramPacket event) {
                tu.checkThread();
                tu.azzert(event.sender().equals(connected.localAddress()));
                tu.azzert(event.data().equals(buffer));
                server.write(event.data(), event.sender(), new AsyncResultHandler<BoundDatagramChannel>() {
                  @Override
                  public void handle(AsyncResult<BoundDatagramChannel> event) {
                    tu.checkThread();
                    tu.azzert(event.succeeded());
                  }
                });
              }
            });
            connected.dataHandler(new Handler<Buffer>() {
              @Override
              public void handle(Buffer event) {
                tu.checkThread();
                tu.azzert(event.equals(buffer));
                tu.testComplete();
              }
            });
            connected.exceptionHandler(new Handler<Throwable>() {
              @Override
              public void handle(Throwable event) {
                event.printStackTrace();
                tu.azzert(false);
              }
            });
            connected.write(buffer);
          }
        });
      }
    });
  }

  public void testEchoBound() {
    final DatagramEndpoint endpoint = vertx.createDatagramEndpoint();

    endpoint.bind(new InetSocketAddress("localhost", 1234), new AsyncResultHandler<BoundDatagramChannel>() {
      @Override
      public void handle(AsyncResult<BoundDatagramChannel> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());
        final Buffer buffer = TestUtils.generateRandomBuffer(128);
        server = event.result();
        endpoint.bind(new InetSocketAddress("localhost", 1235), new AsyncResultHandler<BoundDatagramChannel>() {
          @Override
          public void handle(AsyncResult<BoundDatagramChannel> event) {
            tu.checkThread();
            tu.azzert(event.succeeded());
            final BoundDatagramChannel client = event.result();
            TestClient.this.client = client;

            server.dataHandler(new Handler<DatagramPacket>() {
              @Override
              public void handle(DatagramPacket event) {

                tu.checkThread();
                tu.azzert(event.sender().equals(client.localAddress()));
                tu.azzert(event.data().equals(buffer));
                server.write(event.data(), event.sender(), new AsyncResultHandler<BoundDatagramChannel>() {
                  @Override
                  public void handle(AsyncResult<BoundDatagramChannel> event) {
                    tu.checkThread();
                    tu.azzert(event.succeeded());
                  }
                });
              }
            });
            client.dataHandler(new Handler<DatagramPacket>() {
              @Override
              public void handle(DatagramPacket event) {
                tu.checkThread();
                tu.azzert(event.sender().equals(server.localAddress()));
                tu.azzert(event.data().equals(buffer));
                tu.testComplete();
              }
            });

            client.write(buffer, server.localAddress(), new AsyncResultHandler<BoundDatagramChannel>() {
              @Override
              public void handle(AsyncResult<BoundDatagramChannel> event) {
                tu.azzert(event.succeeded());
              }
            });
          }
        });
      }
    });
  }

  public void testConfigureAfterBind() {
    final DatagramEndpoint endpoint = vertx.createDatagramEndpoint();

    endpoint.bind(new InetSocketAddress("localhost", 1234), new AsyncResultHandler<BoundDatagramChannel>() {
      @Override
      public void handle(AsyncResult<BoundDatagramChannel> event) {
        tu.checkThread();
        server = event.result();

        checkConfigure(endpoint);
      }
    });
  }

  public void testConfigureAfterConnect() {
    final DatagramEndpoint endpoint = vertx.createDatagramEndpoint();

    endpoint.connect(new InetSocketAddress("localhost", 1235), new AsyncResultHandler<ConnectedDatagramChannel>() {
      @Override
      public void handle(AsyncResult<ConnectedDatagramChannel> event) {
        tu.checkThread();
        client = event.result();

        checkConfigure(endpoint);
      }
    });
  }

  private void checkConfigure(DatagramEndpoint endpoint)  {
    try {
      endpoint.setBroadcast(true);
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setInterface(InetAddress.getLocalHost());
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    } catch (UnknownHostException ex) {
      // ignore
    }

    try {
      endpoint.setLoopbackModeDisabled(true);
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setNetworkInterface(NetworkInterface.getNetworkInterfaces().nextElement());
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    } catch (SocketException e) {
      // ignore
    }

    try {
      endpoint.setReceiveBufferSize(1024);
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setReuseAddress(true);
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setSendBufferSize(1024);
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setTimeToLive(2);
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setTrafficClass(1);
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setProtocolFamily(StandardProtocolFamily.INET);
      tu.azzert(false);
    } catch (IllegalStateException e) {
      // expected
    }

    tu.testComplete();
  }

  public void testMulticastJoinLeave() throws Exception {
    final DatagramEndpoint endpoint = vertx.createDatagramEndpoint();
    final NetworkInterface iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));
    endpoint.setNetworkInterface(iface);
    endpoint.setProtocolFamily(StandardProtocolFamily.INET);
    endpoint.bind(new InetSocketAddress("127.0.0.1", 1234), new AsyncResultHandler<BoundDatagramChannel>() {
      @Override
      public void handle(AsyncResult<BoundDatagramChannel> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());
        final Buffer buffer = TestUtils.generateRandomBuffer(128);
        server = event.result();

        endpoint.bind(new InetSocketAddress("127.0.0.1", 1235), new AsyncResultHandler<BoundDatagramChannel>() {
          @Override
          public void handle(AsyncResult<BoundDatagramChannel> event) {
            tu.checkThread();
            tu.azzert(event.succeeded());
            final BoundDatagramChannel client = event.result();
            TestClient.this.client = client;


            String group = "230.0.0.1";
            final InetSocketAddress groupAddress = new InetSocketAddress(group, server.localAddress().getPort());

            client.dataHandler(new Handler<DatagramPacket>() {
              @Override
              public void handle(DatagramPacket event) {
                tu.checkThread();
                tu.azzert(event.sender().equals(server.localAddress()));
                tu.azzert(event.data().equals(buffer));
                tu.testComplete();
              }
            });

            client.joinGroup(groupAddress, iface, new AsyncResultHandler<BoundDatagramChannel>() {
              @Override
              public void handle(AsyncResult<BoundDatagramChannel> event) {
                tu.azzert(event.succeeded());
                server.write(buffer, groupAddress, new AsyncResultHandler<BoundDatagramChannel>() {
                  @Override
                  public void handle(AsyncResult<BoundDatagramChannel> event) {
                    tu.azzert(event.succeeded());

                    // leave group
                    client.leaveGroup(groupAddress, iface, new AsyncResultHandler<BoundDatagramChannel>() {
                      @Override
                      public void handle(AsyncResult<BoundDatagramChannel> event) {
                        tu.azzert(event.succeeded());

                        final AtomicBoolean received = new AtomicBoolean(false);
                        client.dataHandler(new Handler<DatagramPacket>() {
                          @Override
                          public void handle(DatagramPacket event) {
                            // Should not receive any more event as it left the group
                            received.set(true);
                          }
                        });
                        server.write(buffer, groupAddress, new AsyncResultHandler<BoundDatagramChannel>() {
                          @Override
                          public void handle(AsyncResult<BoundDatagramChannel> event) {
                            tu.azzert(event.succeeded());

                            // schedule a timer which will check in 1 second if we received a message after the group
                            // was left before
                            vertx.setTimer(1000, new Handler<Long>() {
                              @Override
                              public void handle(Long event) {
                                tu.azzert(!received.get());
                                tu.testComplete();
                              }
                            });
                          }
                        });
                      }
                    });
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  public void testMulticastJoinBlock() throws Exception {
    final DatagramEndpoint endpoint = vertx.createDatagramEndpoint();
    final NetworkInterface iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));
    endpoint.setNetworkInterface(iface);
    endpoint.setProtocolFamily(StandardProtocolFamily.INET);
    endpoint.bind(new InetSocketAddress("127.0.0.1", 1234), new AsyncResultHandler<BoundDatagramChannel>() {
      @Override
      public void handle(AsyncResult<BoundDatagramChannel> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());
        final Buffer buffer = TestUtils.generateRandomBuffer(128);
        server = event.result();

        endpoint.bind(new InetSocketAddress("127.0.0.1", 1235), new AsyncResultHandler<BoundDatagramChannel>() {
          @Override
          public void handle(AsyncResult<BoundDatagramChannel> event) {
            tu.checkThread();
            tu.azzert(event.succeeded());
            final BoundDatagramChannel client = event.result();
            TestClient.this.client = client;


            String group = "230.0.0.1";
            final InetSocketAddress groupAddress = new InetSocketAddress(group, server.localAddress().getPort());

            client.dataHandler(new Handler<DatagramPacket>() {
              @Override
              public void handle(DatagramPacket event) {
                tu.checkThread();
                tu.azzert(event.sender().equals(server.localAddress()));
                tu.azzert(event.data().equals(buffer));
                tu.testComplete();
              }
            });

            client.joinGroup(groupAddress, iface, new AsyncResultHandler<BoundDatagramChannel>() {
              @Override
              public void handle(AsyncResult<BoundDatagramChannel> event) {
                tu.azzert(event.succeeded());
                server.write(buffer, groupAddress, new AsyncResultHandler<BoundDatagramChannel>() {
                  @Override
                  public void handle(AsyncResult<BoundDatagramChannel> event) {
                    tu.azzert(event.succeeded());

                    client.block(groupAddress.getAddress(), iface, server.localAddress().getAddress(), new AsyncResultHandler<BoundDatagramChannel>() {
                      @Override
                      public void handle(AsyncResult<BoundDatagramChannel> event) {
                        tu.azzert(event.succeeded());

                        final AtomicBoolean received = new AtomicBoolean(false);
                        client.dataHandler(new Handler<DatagramPacket>() {
                          @Override
                          public void handle(DatagramPacket event) {
                            // Should not receive any more event as it left the group
                            received.set(true);
                          }
                        });
                        server.write(buffer, groupAddress, new AsyncResultHandler<BoundDatagramChannel>() {
                          @Override
                          public void handle(AsyncResult<BoundDatagramChannel> event) {
                            tu.azzert(event.succeeded());

                            // schedule a timer which will check in 1 second if we received a message after the group
                            // was left before
                            vertx.setTimer(1000, new Handler<Long>() {
                              @Override
                              public void handle(Long event) {
                                tu.azzert(!received.get());
                                tu.testComplete();
                              }
                            });
                          }
                        });
                      }
                    });
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
    if (client != null) {
      client.close(new AsyncResultHandler<Void>() {
        @Override
        public void handle(AsyncResult<Void> event) {
          tu.checkThread();
          stopServer();
        }
      });
    } else {
      stopServer();
    }

  }

  private void stopServer() {
    if (server != null) {
      server.close(new AsyncResultHandler<Void>() {
        @Override
        public void handle(AsyncResult<Void> event) {
          tu.checkThread();
          TestClient.super.stop();
        }
      });
    } else {
      TestClient.super.stop();
    }
  }
}
