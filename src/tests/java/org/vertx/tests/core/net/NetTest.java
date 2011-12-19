/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.tests.core.net;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetTest extends TestBase {

  private static final Logger log = Logger.getLogger(NetTest.class);

  @Test
  public void testConnect() throws Exception {

    final int connectCount = 10;
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicInteger totalConnectCount = new AtomicInteger(0);


    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final NetServer server = new NetServer();

        final long actorId = Vertx.instance.registerHandler(new Handler<String>() {
          public void handle(String msg) {

            server.close(new SimpleHandler() {
              public void handle() {
                latch.countDown();
              }
            });
          }
        });

        server.connectHandler(new Handler<NetSocket>() {
          public void handle(NetSocket sock) {
            if (totalConnectCount.incrementAndGet() == 2 * connectCount) {
              Vertx.instance.sendToHandler(actorId, "foo");
            }
          }
        }).listen(8181);


        NetClient client = new NetClient();

        for (int i = 0; i < connectCount; i++) {
          client.connect(8181, new Handler<NetSocket>() {
            public void handle(NetSocket sock) {
              sock.close();
              if (totalConnectCount.incrementAndGet() == 2 * connectCount) {
                Vertx.instance.sendToHandler(actorId, "foo");
              }
            }
          });
        }
      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));

    throwAssertions();
  }

  @Test
  /*
  Test setting all the server params
  Actually quite hard to test this meaningfully
   */
  public void testServerParams() throws Exception {
    //TODO
  }

  @Test
  /*
  Test setting all the client params
  Actually quite hard to test this meaningfully
   */
  public void testClientParams() throws Exception {
    //TODO
  }

  @Test
  public void testCloseHandlerCloseFromClient() throws Exception {
    testCloseHandler(true);
  }

  @Test
  public void testCloseHandlerCloseFromServer() throws Exception {
    testCloseHandler(false);
  }

  @Test
  public void testSendDataClientToServerString() throws Exception {
    testSendData(true, true);
  }

  @Test
  public void testSendDataServerToClientString() throws Exception {
    testSendData(false, true);
  }

  @Test
  public void testSendDataClientToServerBytes() throws Exception {
    testSendData(true, false);
  }

  @Test
  public void testSendDataServerToClientBytes() throws Exception {
    testSendData(false, false);
  }

  @Test
  /*
  Test writing with a completion
   */
  public void testWriteWithCompletion() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final NetServer server = new NetServer();

        final Buffer sentBuff = Buffer.create(0);
        final Buffer receivedBuff = Buffer.create(0);

        final long actorId = Vertx.instance.registerHandler(new Handler<String>() {
          public void handle(String msg) {
            azzert(Utils.buffersEqual(sentBuff, receivedBuff));
            server.close(new SimpleHandler() {
              public void handle() {
                latch.countDown();
              }
            });
          }
        });

        server.connectHandler(new Handler<NetSocket>() {
          public void handle(NetSocket sock) {
            sock.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                receivedBuff.appendBuffer(data);
                if (receivedBuff.length() == numSends * sendSize) {
                  Vertx.instance.sendToHandler(actorId, "foo");
                }
              }
            });
          }
        }).listen(8181);

        NetClient client = new NetClient().connect(8181, new Handler<NetSocket>() {
          public void handle(NetSocket sock) {
            final ContextChecker checker = new ContextChecker();
            doWrite(sentBuff, sock, numSends, sendSize, checker);
          }
        });
      }
    });


    azzert(latch.await(5, TimeUnit.SECONDS));

    throwAssertions();
  }

  @Test
  public void testSendFileClientToServer() throws Exception {
    testSendFile(true);
  }

  @Test
  public void testSendFileServerToClient() throws Exception {
    testSendFile(false);
  }

  private void testSendFile(final boolean clientToServer) throws Exception {
    final String path = "foo.txt";

    final String content = Utils.randomAlphaString(10000);
    final File file = setupFile(path, content);
    final CountDownLatch latch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {
        Handler<NetSocket> sender = new Handler<NetSocket>() {
          public void handle(final NetSocket sock) {
            String fileName = "./" + path;
            sock.sendFile(fileName);
          }
        };

        final NetServer server = new NetServer();

        final long actorId = Vertx.instance.registerHandler(new Handler<String>() {
          public void handle(String msg) {
            server.close(new SimpleHandler() {
              public void handle() {
                latch.countDown();
              }
            });
          }
        });

        Handler<NetSocket> receiver = new Handler<NetSocket>() {
          public void handle(NetSocket sock) {
            final Buffer buff = Buffer.create(0);
            sock.dataHandler(new Handler<Buffer>() {
              public void handle(final Buffer data) {
                buff.appendBuffer(data);
                if (buff.length() == file.length()) {
                  azzert(content.equals(buff.toString()));
                  Vertx.instance.sendToHandler(actorId, "foo");
                }
              }
            });
          }
        };

        Handler<NetSocket> serverHandler = clientToServer ? receiver : sender;
        Handler<NetSocket> clientHandler = clientToServer ? sender : receiver;

        server.connectHandler(serverHandler).listen(8181);
        new NetClient().connect(8181, clientHandler);
      }
    });

    assert latch.await(5, TimeUnit.SECONDS);
    throwAssertions();
    file.delete();
  }

  //Recursive - we don't write the next packet until we get the completion back from the previous write
  private void doWrite(final Buffer sentBuff, final NetSocket sock, int count, final int sendSize,
                       final ContextChecker checker) {
    Buffer b = Utils.generateRandomBuffer(sendSize);
    sentBuff.appendBuffer(b);
    count--;
    final int c = count;
    if (count == 0) {
      sock.write(b);
    } else {
      sock.write(b, new SimpleHandler() {
        public void handle() {
          checker.check();
          doWrite(sentBuff, sock, c, sendSize, checker);
        }
      });
    }
  }

  private void testSendData(final boolean clientToServer, final boolean string) throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);
    final int numSends = 10;
    final int sendSize = 100;

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final NetServer server = new NetServer();

        final Buffer sentBuff = Buffer.create(0);
        final Buffer receivedBuff = Buffer.create(0);

        final long actorId = Vertx.instance.registerHandler(new Handler<String>() {
          public void handle(String msg) {
            azzert(Utils.buffersEqual(sentBuff, receivedBuff));
            server.close(new SimpleHandler() {
              public void handle() {
                latch.countDown();
              }
            });
          }
        });

        Handler<NetSocket> receiver = new Handler<NetSocket>() {
          public void handle(final NetSocket sock) {
            final ContextChecker checker = new ContextChecker();
            sock.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                checker.check();
                receivedBuff.appendBuffer(data);
                if (receivedBuff.length() == numSends * sendSize) {
                  sock.close();
                  Vertx.instance.sendToHandler(actorId, "foo");
                }
              }
            });
          }
        };

        Handler<NetSocket> sender = new Handler<NetSocket>() {
          public void handle(NetSocket sock) {
            for (int i = 0; i < numSends; i++) {
              if (string) {
                byte[] bytes = new byte[sendSize];
                Arrays.fill(bytes, (byte) 'X');
                try {
                  String s = new String(bytes, "UTF-8");
                  sentBuff.appendBytes(bytes);
                  sock.write(s);
                } catch (Exception e) {
                  e.printStackTrace();
                }
              } else {
                Buffer b = Utils.generateRandomBuffer(sendSize);
                sentBuff.appendBuffer(b);
                sock.write(b);
              }
            }
          }
        };
        Handler<NetSocket> serverHandler = clientToServer ? receiver : sender;
        Handler<NetSocket> clientHandler = clientToServer ? sender : receiver;

        server.connectHandler(serverHandler).listen(8181);
        new NetClient().connect(8181, clientHandler);
      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }


  private void testCloseHandler(final boolean closeClient) throws Exception {
    final int connectCount = 10;

    final AtomicInteger clientCloseCount = new AtomicInteger(0);
    final AtomicInteger serverCloseCount = new AtomicInteger(0);
    final CountDownLatch clientCloseLatch = new CountDownLatch(1);
    final CountDownLatch serverCloseLatch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final NetServer server = new NetServer();

        final long actorId = Vertx.instance.registerHandler(new Handler<String>() {
          public void handle(String msg) {
            log.info("**closing on server");
            server.close(new SimpleHandler() {
              public void handle() {
                serverCloseLatch.countDown();
              }
            });
          }
        });

        server.connectHandler(new Handler<NetSocket>() {
          public void handle(final NetSocket sock) {
            final ContextChecker checker = new ContextChecker();
            sock.closedHandler(new SimpleHandler() {
              public void handle() {
                checker.check();
                if (serverCloseCount.incrementAndGet() == connectCount) {
                  Vertx.instance.sendToHandler(actorId, "foo");
                }
              }
            });
            if (!closeClient) sock.close();
          }
        }).listen(8181);

        NetClient client = new NetClient();

        for (int i = 0; i < connectCount; i++) {
          client.connect(8181, new Handler<NetSocket>() {
            public void handle(NetSocket sock) {
              final ContextChecker checker = new ContextChecker();
              sock.closedHandler(new SimpleHandler() {
                public void handle() {
                  checker.check();
                  if (clientCloseCount.incrementAndGet() == connectCount) {
                    clientCloseLatch.countDown();
                  }
                }
              });
              if (closeClient) sock.close();
            }
          });
        }
      }
    });

    azzert(serverCloseLatch.await(5, TimeUnit.SECONDS));
    azzert(clientCloseLatch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }
}
