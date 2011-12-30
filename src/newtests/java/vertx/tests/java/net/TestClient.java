package vertx.tests.java.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestClientBase;
import org.vertx.tests.Utils;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private static final Logger log = Logger.getLogger(TestClient.class);

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

  // TODO test all client and server params, TCP no delay etc

  public void testEchoBytes() {
    final ContextChecker check = new ContextChecker(tu);
    client.connect(8080, new Handler<NetSocket>() {
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
              tu.azzert(Utils.buffersEqual(sent, received));
              tu.testComplete("testEcho");
            }
          }
        });

        //Now send some data
        for (int i = 0; i < numChunks; i++) {
          Buffer buff = Utils.generateRandomBuffer(chunkSize);
          sent.appendBuffer(buff);
          socket.write(buff);
        }
      }
    });
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

        final String str = Utils.randomUnicodeString(1000);
        final Buffer sentBuff = enc == null ? Buffer.create(str) : Buffer.create(str, enc);

        //We will receive the buffer in fragments which may not be valid strings (since multi-byte chars)
        final Buffer received = Buffer.create(0);

        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            check.check();
            received.appendBuffer(buffer);
            if (received.length() == sentBuff.length()) {
              String rec = enc == null ? received.toString() : received.toString(enc);
              tu.azzert("Expected:" + str + " Received:" + rec, str.equals(rec));
              tu.testComplete("testEcho");
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
            tu.testComplete("testConnect");
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
        tu.azzert("Connect should not be called", false);
      }
    });
  }

  public void testConnectInvalidHost() {
    final ContextChecker check = new ContextChecker(tu);
    client.exceptionHandler(createNoConnectHandler(check));
    client.connect(8080, "somehost", new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        tu.azzert("Connect should not be called", false);
      }
    });
  }

  public void testClientCloseHandlers() {
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
            tu.testComplete("testClientCloseHandler");
          }
        });
        sock.close();
      }
    });
  }

  public void testServerCloseHandlers() {
    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        sock.close();
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
        final Buffer buff = Utils.generateRandomBuffer(10000);
        Vertx.instance.setPeriodic(0, new Handler<Long>() {
          public void handle(Long id) {
            sock.write(buff);
            if (sock.writeQueueFull()) {
              Vertx.instance.cancelTimer(id);
              sock.drainHandler(new SimpleHandler() {
                public void handle() {
                  check.check();
                  tu.azzert(!sock.writeQueueFull());
                  tu.testComplete("testClientDrainHandler");
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
        tu.azzert(e instanceof ConnectException);
        tu.azzert(e.getMessage().equals("Connection refused"));
        tu.testComplete("testConnectInvalidPort");
      }
    };
  }

  //Recursive - we don't write the next packet until we get the completion back from the previous write
  void doWrite(final Buffer sentBuff, final NetSocket sock, int count, final int sendSize,
               final ContextChecker checker) {
    Buffer b = Utils.generateRandomBuffer(sendSize);
    sentBuff.appendBuffer(b);
    count--;
    final int c = count;
    if (count == 0) {

      sock.write(b, new SimpleHandler() {
        public void handle() {
          checker.check();
          tu.testComplete("testWriteWithCompletion");
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
