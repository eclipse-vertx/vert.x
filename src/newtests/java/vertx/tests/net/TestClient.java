package vertx.tests.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestClientBase;
import org.vertx.java.newtests.TestUtils;
import org.vertx.java.tests.net.JavaNetTest;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

 // private static final Logger log = Logger.getLogger(TestClient.class);

  private NetClient client;

  @Override
  public void start() {
    super.start();
    client = new NetClient();
    tu.appReady();
  }

  @Override
  public void stop() {
    client.close();
    super.stop();
  }

  // The tests

  public void testClientDefaults() {
    tu.azzert(!client.isSSL());
    tu.azzert(client.getKeyStorePassword() == null);
    tu.azzert(client.getKeyStorePath() == null);
    tu.azzert(client.getTrustStorePassword() == null);
    tu.azzert(client.getTrustStorePath() == null);
    tu.azzert(client.isReuseAddress() == null);
    tu.azzert(client.isSoLinger() == null);
    tu.azzert(client.isTCPKeepAlive());
    tu.azzert(client.isTCPNoDelay());
    tu.azzert(client.getReconnectAttempts() == 0);
    tu.azzert(client.getReconnectInterval() == 1000);
    tu.azzert(client.getReceiveBufferSize() == null);
    tu.azzert(client.getSendBufferSize() == null);
    tu.azzert(client.getTrafficClass() == null);
    tu.testComplete();
  }

  public void testClientAttributes() {

    tu.azzert(client.setSSL(false) == client);
    tu.azzert(!client.isSSL());

    tu.azzert(client.setSSL(true) == client);
    tu.azzert(client.isSSL());

    String pwd = TestUtils.randomUnicodeString(10);
    tu.azzert(client.setKeyStorePassword(pwd) == client);
    tu.azzert(client.getKeyStorePassword().equals(pwd));

    String path = TestUtils.randomUnicodeString(10);
    tu.azzert(client.setKeyStorePath(path) == client);
    tu.azzert(client.getKeyStorePath().equals(path));

    pwd = TestUtils.randomUnicodeString(10);
    tu.azzert(client.setTrustStorePassword(pwd) == client);
    tu.azzert(client.getTrustStorePassword().equals(pwd));

    path = TestUtils.randomUnicodeString(10);
    tu.azzert(client.setTrustStorePath(path) == client);
    tu.azzert(client.getTrustStorePath().equals(path));

    tu.azzert(client.setReuseAddress(true) == client);
    tu.azzert(client.isReuseAddress());
    tu.azzert(client.setReuseAddress(false) == client);
    tu.azzert(!client.isReuseAddress());

    tu.azzert(client.setSoLinger(true) == client);
    tu.azzert(client.isSoLinger());
    tu.azzert(client.setSoLinger(false) == client);
    tu.azzert(!client.isSoLinger());

    tu.azzert(client.setTCPKeepAlive(true) == client);
    tu.azzert(client.isTCPKeepAlive());
    tu.azzert(client.setTCPKeepAlive(false) == client);
    tu.azzert(!client.isTCPKeepAlive());

    tu.azzert(client.setTCPNoDelay(true) == client);
    tu.azzert(client.isTCPNoDelay());
    tu.azzert(client.setTCPNoDelay(false) == client);
    tu.azzert(!client.isTCPNoDelay());

    int reconnectAttempts = new Random().nextInt(1000) + 1;
    tu.azzert(client.setReconnectAttempts(reconnectAttempts) == client);
    tu.azzert(client.getReconnectAttempts() == reconnectAttempts);

    try {
      client.setReconnectAttempts(-2);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int reconnectDelay = new Random().nextInt(1000) + 1;
    tu.azzert(client.setReconnectInterval(reconnectDelay) == client);
    tu.azzert(client.getReconnectInterval() == reconnectDelay);

    try {
      client.setReconnectInterval(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setReconnectInterval(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }


    int rbs = new Random().nextInt(1024 * 1024) + 1;
    tu.azzert(client.setReceiveBufferSize(rbs) == client);
    tu.azzert(client.getReceiveBufferSize() == rbs);

    try {
      client.setReceiveBufferSize(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setReceiveBufferSize(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int sbs = new Random().nextInt(1024 * 1024);
    tu.azzert(client.setSendBufferSize(sbs) == client);
    tu.azzert(client.getSendBufferSize() == sbs);

    try {
      client.setSendBufferSize(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setSendBufferSize(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int trafficClass = new Random().nextInt(10000000);
    tu.azzert(client.setTrafficClass(trafficClass) == client);
    tu.azzert(client.getTrafficClass() == trafficClass);

    tu.testComplete();

  }

  public void testServerDefaults() {
    NetServer server = new NetServer();
    tu.azzert(!server.isSSL());
    tu.azzert(server.getKeyStorePassword() == null);
    tu.azzert(server.getKeyStorePath() == null);
    tu.azzert(server.getTrustStorePassword() == null);
    tu.azzert(server.getTrustStorePath() == null);
    tu.azzert(server.isReuseAddress());
    tu.azzert(server.isSoLinger() == null);
    tu.azzert(server.isTCPKeepAlive());
    tu.azzert(server.isTCPNoDelay());
    tu.azzert(server.getReceiveBufferSize() == null);
    tu.azzert(server.getSendBufferSize() == null);
    tu.azzert(server.getTrafficClass() == null);
    tu.testComplete();
  }

  public void testServerAttributes() {

    NetServer server = new NetServer();

    tu.azzert(server.setSSL(false) == server);
    tu.azzert(!server.isSSL());

    tu.azzert(server.setSSL(true) == server);
    tu.azzert(server.isSSL());


    String pwd = TestUtils.randomUnicodeString(10);
    tu.azzert(server.setKeyStorePassword(pwd) == server);
    tu.azzert(server.getKeyStorePassword().equals(pwd));

    String path = TestUtils.randomUnicodeString(10);
    tu.azzert(server.setKeyStorePath(path) == server);
    tu.azzert(server.getKeyStorePath().equals(path));

    pwd = TestUtils.randomUnicodeString(10);
    tu.azzert(server.setTrustStorePassword(pwd) == server);
    tu.azzert(server.getTrustStorePassword().equals(pwd));

    path = TestUtils.randomUnicodeString(10);
    tu.azzert(server.setTrustStorePath(path) == server);
    tu.azzert(server.getTrustStorePath().equals(path));

    tu.azzert(server.setReuseAddress(true) == server);
    tu.azzert(server.isReuseAddress());
    tu.azzert(server.setReuseAddress(false) == server);
    tu.azzert(!server.isReuseAddress());

    tu.azzert(server.setSoLinger(true) == server);
    tu.azzert(server.isSoLinger());
    tu.azzert(server.setSoLinger(false) == server);
    tu.azzert(!server.isSoLinger());

    tu.azzert(server.setTCPKeepAlive(true) == server);
    tu.azzert(server.isTCPKeepAlive());
    tu.azzert(server.setTCPKeepAlive(false) == server);
    tu.azzert(!server.isTCPKeepAlive());

    tu.azzert(server.setTCPNoDelay(true) == server);
    tu.azzert(server.isTCPNoDelay());
    tu.azzert(server.setTCPNoDelay(false) == server);
    tu.azzert(!server.isTCPNoDelay());

    int rbs = new Random().nextInt(1024 * 1024) + 1;
    tu.azzert(server.setReceiveBufferSize(rbs) == server);
    tu.azzert(server.getReceiveBufferSize() == rbs);

    try {
      server.setReceiveBufferSize(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      server.setReceiveBufferSize(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int sbs = new Random().nextInt(1024 * 1024);
    tu.azzert(server.setSendBufferSize(sbs) == server);
    tu.azzert(server.getSendBufferSize() == sbs);

    try {
      server.setSendBufferSize(0);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      server.setSendBufferSize(-1);
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int trafficClass = new Random().nextInt(10000000);
    tu.azzert(server.setTrafficClass(trafficClass) == server);
    tu.azzert(server.getTrafficClass() == trafficClass);

    tu.testComplete();

  }

  private Handler<NetSocket> getEchoHandler(final ContextChecker check) {
    return new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        check.check();
        final int numChunks = 100;
        final int chunkSize = 100;

        final Buffer received = Buffer.create(0);
        final Buffer sent = Buffer.create(0);

        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            check.check();
            received.appendBuffer(buffer);
            if (received.length() == sent.length()) {
              tu.azzert(TestUtils.buffersEqual(sent, received));
              tu.testComplete();
            }
          }
        });

        //Now send some data
        for (int i = 0; i < numChunks; i++) {
          Buffer buff = TestUtils.generateRandomBuffer(chunkSize);
          sent.appendBuffer(buff);
          socket.write(buff);
        }
      }
    };
  }

  public void testEchoBytes() {
    final ContextChecker check = new ContextChecker(tu);
    client.connect(8080, getEchoHandler(check));
  }

  public void testEchoStringDefaultEncoding() {
    echoString(null);
  }

  public void testEchoStringUTF8() {
    echoString("UTF-8");
  }

  public void testEchoStringUTF16() {
    echoString("UTF-16");
  }

  void echoString(final String enc) {
    final ContextChecker check = new ContextChecker(tu);
    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket socket) {

        check.check();

        final String str = TestUtils.randomUnicodeString(1000);
        final Buffer sentBuff = enc == null ? Buffer.create(str) : Buffer.create(str, enc);

        //We will receive the buffer in fragments which may not be valid strings (since multi-byte chars)
        final Buffer received = Buffer.create(0);

        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            check.check();
            received.appendBuffer(buffer);
            if (received.length() == sentBuff.length()) {
              String rec = enc == null ? received.toString() : received.toString(enc);
              tu.azzert(str.equals(rec), "Expected:" + str + " Received:" + rec);
              tu.testComplete();
            }
          }
        });

        if (enc == null) {
          socket.write(str);
        } else {
          socket.write(str, enc);
        }
      }
    });
  }

  public void testConnectDefaultHost() {
    connect(8080, null);
  }

  public void testConnectLocalHost() {
    connect(8080, "localhost");
  }

  void connect(int port, String host) {
    final ContextChecker check = new ContextChecker(tu);
    final int numConnections = 100;
    final AtomicInteger connCount = new AtomicInteger(0);
    for (int i = 0; i < numConnections; i++) {
      Handler<NetSocket> handler =  new Handler<NetSocket>() {
        public void handle(NetSocket sock) {
          check.check();
          sock.close();
          if (connCount.incrementAndGet() == numConnections) {
            tu.testComplete();
          }
        }
      };
      if (host == null) {
        client.connect(port, handler);
      } else  {
        client.connect(port, host, handler);
      }
    }
  }

  public void testConnectInvalidPort() {
    final ContextChecker check = new ContextChecker(tu);
    client.exceptionHandler(createNoConnectHandler(check));
    client.connect(9998, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        tu.azzert(false, "Connect should not be called");
      }
    });
  }

  public void testConnectInvalidHost() {
    final ContextChecker check = new ContextChecker(tu);
    client.exceptionHandler(createNoConnectHandler(check));
    client.connect(8080, "somehost", new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        tu.azzert(false, "Connect should not be called");
      }
    });
  }

  public void testClientCloseHandlersCloseFromClient() {
    clientCloseHandlers(true);
  }

  public void testClientCloseHandlersCloseFromServer() {
    clientCloseHandlers(false);
  }

  void clientCloseHandlers(final boolean closeFromClient) {
    final ContextChecker check = new ContextChecker(tu);
    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        check.check();
        final AtomicInteger counter = new AtomicInteger(0);
        sock.endHandler(new SimpleHandler() {
          public void handle() {
            check.check();
            tu.azzert(counter.incrementAndGet() == 1);
          }
        });
        sock.closedHandler(new SimpleHandler() {
          public void handle() {
            check.check();
            tu.azzert(counter.incrementAndGet() == 2);
            tu.testComplete();
          }
        });
        if (closeFromClient) {
          sock.close();
        }
      }
    });
  }

  public void testServerCloseHandlersCloseFromClient() {
    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        sock.close();
      }
    });
  }

  public void testServerCloseHandlersCloseFromServer() {
    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
      }
    });
  }


  public void testClientDrainHandler() {
    final ContextChecker check = new ContextChecker(tu);
    client.connect(8080, new Handler<NetSocket>() {

      public void handle(final NetSocket sock) {
        check.check();
        tu.azzert(!sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);
        final Buffer buff = TestUtils.generateRandomBuffer(10000);
        Vertx.instance.setPeriodic(0, new Handler<Long>() {
          public void handle(Long id) {
            sock.write(buff);
            if (sock.writeQueueFull()) {
              Vertx.instance.cancelTimer(id);
              sock.drainHandler(new SimpleHandler() {
                public void handle() {
                  check.check();
                  tu.azzert(!sock.writeQueueFull());
                  tu.testComplete();
                }
              });

              // Tell the server to resume
              EventBus.instance.send(new Message("server_resume"));
            }
          }
        });
      }
    });
  }

  public void testServerDrainHandler() {
    final ContextChecker check = new ContextChecker(tu);
    client.connect(8080, new Handler<NetSocket>() {
      public void handle(final NetSocket sock) {
        check.check();
        sock.pause();
        setHandlers(sock, check);
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
          }
        });
      }
    });
  }

  public void testWriteWithCompletion() {
    final ContextChecker check = new ContextChecker(tu);
    final int numSends = 10;
    final int sendSize = 100;
    final Buffer sentBuff = Buffer.create(0);

    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        check.check();
        doWrite(sentBuff, sock, numSends, sendSize, check);
      }
    });
  }

  public void testReconnectAttemptsInfinite() {
    reconnectAttempts(-1);
  }

  public void testReconnectAttemptsMany() {
    reconnectAttempts(100000);
  }

  void reconnectAttempts(int attempts) {
    client.setReconnectAttempts(-1);
    client.setReconnectInterval(10);

    final ContextChecker check = new ContextChecker(tu);

    //The server delays starting for a a few seconds, but it should still connect
    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        check.check();
        tu.testComplete();
      }
    });
  }

  public void testReconnectAttemptsNotEnough() {
    client.setReconnectAttempts(10);
    client.setReconnectInterval(10);

    final ContextChecker check = new ContextChecker(tu);

    client.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        check.check();
        tu.testComplete();
      }
    });

    //The server delays starting for a a few seconds, and it should run out of attempts before that
    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        check.check();
        tu.azzert(false, "Should not connect");
      }
    });
  }

  public void testTLSClientTrustAll() {
    tls();
  }

  public void testTLSClientTrustServerCert() {
    tls();
  }

  public void testTLSClientUntrustedServer() {
    tls();
  }

  public void testTLSClientCertNotRequired() {
    tls();
  }

  public void testTLSClientCertRequired() {
    tls();
  }

  public void testTLSClientCertRequiredNoClientCert() {
    tls();
  }

  public void testTLSClientCertClientNotTrusted() {
    tls();
  }

  void tls() {
    JavaNetTest.TLSTestParams params = SharedData.<String, JavaNetTest.TLSTestParams>getMap("TLSTest").get("params");

    client.setSSL(true);

    if (params.clientTrustAll) {
      client.setTrustAll(true);
    }

    if (params.clientTrust) {
      client.setTrustStorePath("./src/tests/keystores/client-truststore.jks")
          .setTrustStorePassword("wibble");
    }
    if (params.clientCert) {
      client.setKeyStorePath("./src/tests/keystores/client-keystore.jks")
          .setKeyStorePassword("wibble");
    }

    final ContextChecker check = new ContextChecker(tu);

    final boolean shouldPass = params.shouldPass;

    client.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        e.printStackTrace();
        if (shouldPass) {
          tu.azzert(false, "Should not throw exception");
        } else {
          tu.testComplete();
        }
      }
    });

    client.connect(4043, new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        check.check();
        if (!shouldPass) {
          tu.azzert(false, "Should not connect");
          return;
        }
        final int numChunks = 100;
        final int chunkSize = 100;

        final Buffer received = Buffer.create(0);
        final Buffer sent = Buffer.create(0);

        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            check.check();
            received.appendBuffer(buffer);
            if (received.length() == sent.length()) {
              tu.azzert(TestUtils.buffersEqual(sent, received));
              tu.testComplete();
            }
          }
        });

        //Now send some data
        for (int i = 0; i < numChunks; i++) {
          Buffer buff = TestUtils.generateRandomBuffer(chunkSize);
          sent.appendBuffer(buff);
          socket.write(buff);
        }
      }
    });
  }

  public void testSharedServersMultipleInstances1() {
    System.out.println("in testSharedServersMultipleInstances");
    // Create a bunch of connections
    final int numConnections = SharedData.<String, Integer>getMap("params").get("numConnections");
    final AtomicInteger counter = new AtomicInteger(0);
    for (int i = 0; i < numConnections; i++) {
      client.connect(8080, "localhost", new Handler<NetSocket>() {
        public void handle(NetSocket sock) {
          sock.closedHandler(new SimpleHandler() {
            public void handle() {
              int count = counter.incrementAndGet();
              if (count == numConnections) {
                System.out.println("Test complete");
                tu.testComplete();
              }
            }
          });
        }
      });
    }
  }

  public void testSharedServersMultipleInstances2() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances1StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances2StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances1StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances2StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }

  void setHandlers(final NetSocket sock, final ContextChecker check) {
    final Handler<Message> resumeHandler = new Handler<Message>() {
      public void handle(Message message) {
        check.check();
        sock.resume();
      }
    };
    EventBus.instance.registerHandler("client_resume", resumeHandler);
    sock.closedHandler(new SimpleHandler() {
      public void handle() {
        check.check();
        EventBus.instance.unregisterHandler("client_resume", resumeHandler);
      }
    });
  }

  Handler<Exception> createNoConnectHandler(final ContextChecker check) {
    return new Handler<Exception>() {
      public void handle(Exception e) {
        check.check();
        tu.testComplete();
      }
    };
  }

  // Recursive - we don't write the next packet until we get the completion back from the previous write
  void doWrite(final Buffer sentBuff, final NetSocket sock, int count, final int sendSize,
               final ContextChecker checker) {
    Buffer b = TestUtils.generateRandomBuffer(sendSize);
    sentBuff.appendBuffer(b);
    count--;
    final int c = count;
    if (count == 0) {

      sock.write(b, new SimpleHandler() {
        public void handle() {
          checker.check();
          tu.testComplete();
        }
      });
    } else {
      sock.write(b, new SimpleHandler() {
        public void handle() {
          checker.check();
          doWrite(sentBuff, sock, c, sendSize, checker);
        }
      });
    }
  }
}
