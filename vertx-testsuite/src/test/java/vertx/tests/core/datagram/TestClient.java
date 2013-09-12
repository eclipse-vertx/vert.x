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
  private DatagramSupport peer1;
  private DatagramServer peer2;

  public void testSendReceive() {
    peer1 = vertx.createDatagramClient();
    peer2 = vertx.createDatagramServer(null);
    peer2.exceptionHandler(new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        tu.azzert(false);
      }
    });
    peer2.listen("127.0.0.1", 1234, new AsyncResultHandler<DatagramServer>() {
      @Override
      public void handle(AsyncResult<DatagramServer> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());
        final Buffer buffer = TestUtils.generateRandomBuffer(128);

        peer2.dataHandler(new Handler<DatagramPacket>() {
          @Override
          public void handle(DatagramPacket event) {
            tu.checkThread();
            tu.azzert(event.data().equals(buffer));
            tu.testComplete();

          }
        });
        peer1.send(buffer, "127.0.0.1", 1234, new AsyncResultHandler<DatagramClient>() {
          @Override
          public void handle(AsyncResult<DatagramClient> event) {
            tu.checkThread();
            tu.azzert(event.succeeded());
          }
        });
      }
    });
  }

  public void testEcho() {
    peer1 = vertx.createDatagramServer(null);
    peer2 = vertx.createDatagramServer(null);
    final DatagramServer peer1 = (DatagramServer) this.peer1;
    peer1.exceptionHandler(new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        tu.azzert(false);
      }
    });
    peer2.exceptionHandler(new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        tu.azzert(false);
      }
    });
    peer2.listen("127.0.0.1", 1234, new AsyncResultHandler<DatagramServer>() {
      @Override
      public void handle(AsyncResult<DatagramServer> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());

        final Buffer buffer = TestUtils.generateRandomBuffer(128);
        peer2.dataHandler(new Handler<DatagramPacket>() {
          @Override
          public void handle(DatagramPacket event) {
            tu.checkThread();
            tu.azzert(event.sender().equals(new InetSocketAddress("127.0.0.1", 1235)));
            tu.azzert(event.data().equals(buffer));
            peer2.send(event.data(), "127.0.0.1", 1235, new AsyncResultHandler<DatagramServer>() {
              @Override
              public void handle(AsyncResult<DatagramServer> event) {
                tu.checkThread();
                tu.azzert(event.succeeded());
              }
            });
          }
        });

        peer1.listen("127.0.0.1", 1235, new AsyncResultHandler<DatagramServer>() {
          @Override
          public void handle(AsyncResult<DatagramServer> event) {

            peer1.dataHandler(new Handler<DatagramPacket>() {
              @Override
              public void handle(DatagramPacket event) {
                tu.checkThread();
                tu.azzert(event.data().equals(buffer));
                tu.azzert(event.sender().equals(new InetSocketAddress("127.0.0.1", 1234)));
                tu.testComplete();
              }
            });

            peer1.send(buffer, "127.0.0.1", 1234, new AsyncResultHandler<DatagramServer>() {
              @Override
              public void handle(AsyncResult<DatagramServer> event) {
                tu.checkThread();
                tu.azzert(event.succeeded());
              }
            });
          }
        });
      }
    });
  }

  public void testSendAfterCloseFails() {
    peer1 = vertx.createDatagramClient();
    peer2 = vertx.createDatagramServer(null);
    peer1.close(new AsyncResultHandler<Void>() {
      @Override
      public void handle(AsyncResult<Void> event) {
        peer1.send("Test", "127.0.0.1", 1234, new AsyncResultHandler<DatagramClient>() {
          @Override
          public void handle(AsyncResult<DatagramClient> event) {
            tu.azzert(event.failed());
            peer1 = null;

            peer2.close(new AsyncResultHandler<Void>() {
              @Override
              public void handle(AsyncResult<Void> event) {
                peer2.send("Test", "127.0.0.1", 1234, new AsyncResultHandler<DatagramServer>() {
                  @Override
                  public void handle(AsyncResult<DatagramServer> event) {
                    tu.azzert(event.failed());
                    peer2 = null;
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

  public void testBroadcast() {
    peer1 = vertx.createDatagramClient();
    peer2 = vertx.createDatagramServer(null);
    peer2.exceptionHandler(new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        tu.azzert(false);
      }
    });
    peer2.setBroadcast(true);
    peer1.setBroadcast(true);

    peer2.listen(new InetSocketAddress(1234), new AsyncResultHandler<DatagramServer>() {
      @Override
      public void handle(AsyncResult<DatagramServer> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());
        final Buffer buffer = TestUtils.generateRandomBuffer(128);

        peer2.dataHandler(new Handler<DatagramPacket>() {
          @Override
          public void handle(DatagramPacket event) {
            tu.checkThread();
            tu.azzert(event.data().equals(buffer));
            tu.testComplete();

          }
        });
        peer1.send(buffer, "255.255.255.255", 1234, new AsyncResultHandler<DatagramClient>() {
          @Override
          public void handle(AsyncResult<DatagramClient> event) {
            tu.checkThread();
            tu.azzert(event.succeeded());
          }
        });
      }
    });
  }

  public void testBroadcastFailsIfNotConfigured() {
    peer1 = vertx.createDatagramClient();
    peer1.send("test", "255.255.255.255", 1234, new AsyncResultHandler<DatagramClient>() {
      @Override
      public void handle(AsyncResult<DatagramClient> event) {
        tu.checkThread();
        tu.azzert(event.failed());
        tu.testComplete();
      }
    });
  }


  /*
  public void testConfigureAfterBind() {
    final DatagramEndpoint endpoint = vertx.createDatagramEndpoint();

    endpoint.bind(new InetSocketAddress("localhost", 1234), new AsyncResultHandler<DatagramServer>() {
      @Override
      public void handle(AsyncResult<DatagramServer> event) {
        tu.checkThread();
        server = event.result();

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
  */
  public void testMulticastJoinLeave() throws Exception {
    final Buffer buffer = TestUtils.generateRandomBuffer(128);
    final InetAddress groupAddress = InetAddress.getByName("230.0.0.1");

    peer1 = vertx.createDatagramClient();
    final DatagramClient peer1 = (DatagramClient) this.peer1;
    peer2 = vertx.createDatagramServer(StandardProtocolFamily.INET);

    peer2.dataHandler(new Handler<DatagramPacket>() {
      @Override
      public void handle(DatagramPacket event) {
        tu.checkThread();
        //tu.azzert(event.sender().equals(server.localAddress()));
        tu.azzert(event.data().equals(buffer));
        tu.testComplete();
      }
    });

    peer2.listen("127.0.0.1", 1234, new AsyncResultHandler<DatagramServer>() {
      @Override
      public void handle(AsyncResult<DatagramServer> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());


        peer2.joinGroup(groupAddress, new AsyncResultHandler<DatagramServer>() {
          @Override
          public void handle(AsyncResult<DatagramServer> event) {
            tu.azzert(event.succeeded());
            peer1.send(buffer, groupAddress.getHostAddress(), 1234, new AsyncResultHandler<DatagramClient>() {
              @Override
              public void handle(AsyncResult<DatagramClient> event) {
                tu.azzert(event.succeeded());

                // leave group
                peer2.leaveGroup(groupAddress, new AsyncResultHandler<DatagramServer>() {
                  @Override
                  public void handle(AsyncResult<DatagramServer> event) {
                    tu.azzert(event.succeeded());

                    final AtomicBoolean received = new AtomicBoolean(false);
                    peer2.dataHandler(new Handler<DatagramPacket>() {
                      @Override
                      public void handle(DatagramPacket event) {
                        // Should not receive any more event as it left the group
                        received.set(true);
                      }
                    });
                    peer1.send(buffer, groupAddress.getHostAddress(), 1234, new AsyncResultHandler<DatagramClient>() {
                      @Override
                      public void handle(AsyncResult<DatagramClient> event) {
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

  /*
  public void testMulticastJoinBlock() throws Exception {
    final DatagramEndpoint endpoint = vertx.createDatagramEndpoint();
    final NetworkInterface iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));
    endpoint.setNetworkInterface(iface);
    endpoint.setProtocolFamily(StandardProtocolFamily.INET);
    endpoint.bind(new InetSocketAddress("127.0.0.1", 1234), new AsyncResultHandler<DatagramServer>() {
      @Override
      public void handle(AsyncResult<DatagramServer> event) {
        tu.checkThread();
        tu.azzert(event.succeeded());
        final Buffer buffer = TestUtils.generateRandomBuffer(128);
        server = event.result();

        endpoint.bind(new InetSocketAddress("127.0.0.1", 1235), new AsyncResultHandler<DatagramServer>() {
          @Override
          public void handle(AsyncResult<DatagramServer> event) {
            tu.checkThread();
            tu.azzert(event.succeeded());
            client = event.result();

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

            client.joinGroup(groupAddress, iface, new AsyncResultHandler<DatagramServer>() {
              @Override
              public void handle(AsyncResult<DatagramServer> event) {
                tu.azzert(event.succeeded());
                server.write(buffer, groupAddress, new AsyncResultHandler<DatagramServer>() {
                  @Override
                  public void handle(AsyncResult<DatagramServer> event) {
                    tu.azzert(event.succeeded());

                    client.block(groupAddress.getAddress(), iface, server.localAddress().getAddress(), new AsyncResultHandler<DatagramServer>() {
                      @Override
                      public void handle(AsyncResult<DatagramServer> event) {
                        tu.azzert(event.succeeded());

                        final AtomicBoolean received = new AtomicBoolean(false);
                        client.dataHandler(new Handler<DatagramPacket>() {
                          @Override
                          public void handle(DatagramPacket event) {
                            // Should not receive any more event as it left the group
                            received.set(true);
                          }
                        });
                        server.write(buffer, groupAddress, new AsyncResultHandler<DatagramServer>() {
                          @Override
                          public void handle(AsyncResult<DatagramServer> event) {
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
  */
  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    if (peer1 != null) {
      peer1.close(new AsyncResultHandler<Void>() {
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
    if (peer2 != null) {
      peer2.close(new AsyncResultHandler<Void>() {
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
