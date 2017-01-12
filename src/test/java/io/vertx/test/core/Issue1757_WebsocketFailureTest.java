package io.vertx.test.core;

import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.NetServer;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class Issue1757_WebsocketFailureTest {
    final BlockingQueue<Throwable> resultQueue = new ArrayBlockingQueue<Throwable>(10);
    final Exception serverGotCloseException = new Exception();

    void addResult(Throwable result) {
        try {
            resultQueue.put(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void test1757_1() throws Throwable {
        doTest(true);
    }

    @Test
    public void test1757_2() throws Throwable {
        doTest(false);
    }

    private void doTest(boolean keepAliveInOptions) throws Throwable {
        final Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new AbstractVerticle() {
            @Override
            public void start() throws Exception {
                vertx.createNetServer().connectHandler(sock -> {
                    final Buffer fullReq = Buffer.buffer(230);
                    sock.handler(b -> {
                        fullReq.appendBuffer(b);
                        String reqPart = b.toString();
                        if (fullReq.toString().contains("\r\n\r\n")) {
                            try {
                                String content = "0123456789";
                                content = content + content;
                                content = content + content + content + content + content;
                                String resp = "HTTP/1.1 200 OK\r\n";
                                if (keepAliveInOptions) {
                                    resp += "Connection: close\r\n";
                                }
                                resp += "Content-Length: 100\r\n\r\n" + content;
                                sock.write(Buffer.buffer(resp.getBytes("ASCII")));
                            } catch (UnsupportedEncodingException e) {
                                addResult(e);
                            }
                        }
                    });
                    sock.closeHandler(v -> {
                        addResult(serverGotCloseException);
                    });
                }).listen(ar -> {
                    if (ar.failed()) {
                        addResult(ar.cause());
                        return;
                    }
                    NetServer server = ar.result();
                    int port = server.actualPort();

                    HttpClientOptions opts = new HttpClientOptions().setKeepAlive(keepAliveInOptions);
                    vertx.createHttpClient(opts).websocket(port, "localhost", "/", ws -> {
                        addResult(new AssertionError("Websocket unexpectedly connected"));
                        ws.close();
                    }, t -> {
                        addResult(t);
                    });
                });
            }
        });
        try {
            boolean serverGotClose = false;
            boolean clientGotCorrectException = false;
            while (!serverGotClose || !clientGotCorrectException) {
                Throwable result = resultQueue.poll(20, TimeUnit.SECONDS);
                if (result == null) {
                    throw new AssertionError("Timed out waiting for expected state, current: serverGotClose = " + serverGotClose + ", clientGotCorrectException = " + clientGotCorrectException);
                } else if (result == serverGotCloseException) {
                    serverGotClose = true;
                } else if (result instanceof WebSocketHandshakeException
                        && result.getMessage().equals("Websocket connection attempt returned HTTP status code 200")) {
                    clientGotCorrectException = true;
                } else {
                    throw result;
                }
            }
        } finally {
            vertx.close();
        }
    }
}
