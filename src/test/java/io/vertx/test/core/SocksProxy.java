package io.vertx.test.core;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pump;

/**
 * SOCKS5 Proxy
 * <p>
 * A simple SOCKS5 proxy for testing SOCKS functionality. Currently we only support tcp connect and
 * username/password auth, which is enough to make the currently implemented client tests to pass.
 *
 * <p>
 * Usually the server will be started in @Before and stopped in @After for a unit test using HttpClient or NetClient
 * with the setProxyOptions method.
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class SocksProxy extends TestProxyBase {

  private static final Logger log = LoggerFactory.getLogger(SocksProxy.class);

  private static final Buffer clientInit = Buffer.buffer(new byte[] { 5, 1, 0 });
  private static final Buffer serverReply = Buffer.buffer(new byte[] { 5, 0 });
  private static final Buffer clientRequest = Buffer.buffer(new byte[] { 5, 1, 0, 3 });
  private static final Buffer connectResponse = Buffer.buffer(new byte[] { 5, 0, 0, 1, 0x7f, 0, 0, 1, 0x27, 0x10 });
  private static final Buffer errorResponse = Buffer.buffer(new byte[] { 5, 4, 0, 1, 0, 0, 0, 0, 0, 0 });

  private static final Buffer clientInitAuth = Buffer.buffer(new byte[] { 5, 2, 0, 2 });
  private static final Buffer serverReplyAuth = Buffer.buffer(new byte[] { 5, 2 });
  private static final Buffer authSuccess = Buffer.buffer(new byte[] { 1, 0 });
  private static final Buffer authFailed = Buffer.buffer(new byte[] { 1, 1 });

  private static final int PORT = 11080;

  private NetServer server;

  public SocksProxy(String username) {
    super(username);
  }

  /**
   * Start the server.
   * 
   * @param vertx
   *          Vertx instance to use for creating the server and client
   * @param finishedHandler
   *          will be called when the start has started
   */
  @Override
  public void start(Vertx vertx, Handler<Void> finishedHandler) {
    NetServerOptions options = new NetServerOptions();
    options.setHost("localhost").setPort(PORT);
    server = vertx.createNetServer(options);
    server.connectHandler(socket -> {
      socket.handler(buffer -> {
        Buffer expectedInit = username == null ? clientInit : clientInitAuth;
        if (!buffer.equals(expectedInit)) {
          throw new IllegalStateException("expected " + toHex(expectedInit) + ", got " + toHex(buffer));
        }
        boolean useAuth = buffer.equals(clientInitAuth);
        log.debug("got request: " + toHex(buffer));

        final Handler<Buffer> handler = buffer2 -> {
          if (!buffer2.getBuffer(0, clientRequest.length()).equals(clientRequest)) {
            throw new IllegalStateException("expected " + toHex(clientRequest) + ", got " + toHex(buffer2));
          }
          int stringLen = buffer2.getUnsignedByte(4);
          log.debug("string len " + stringLen);
          if (buffer2.length() != 7 + stringLen) {
            throw new IllegalStateException("format error in client request, got " + toHex(buffer2));
          }
          String host = buffer2.getString(5, 5 + stringLen);
          int port = buffer2.getUnsignedShort(5 + stringLen);
          log.debug("got request: " + toHex(buffer2));
          log.debug("connect: " + host + ":" + port);
          socket.handler(null);
          lastUri = host + ":" + port;

          if (forceUri != null) {
            host = forceUri.substring(0, forceUri.indexOf(':'));
            port = Integer.valueOf(forceUri.substring(forceUri.indexOf(':') + 1));
          }
          log.debug("connecting to " + host + ":" + port);
          NetClient netClient = vertx.createNetClient(new NetClientOptions());
          netClient.connect(port, host, result -> {
            if (result.succeeded()) {
              log.debug("writing: " + toHex(connectResponse));
              socket.write(connectResponse);
              log.debug("connected, starting pump");
              NetSocket clientSocket = result.result();
              socket.closeHandler(v -> clientSocket.close());
              clientSocket.closeHandler(v -> socket.close());
              Pump.pump(socket, clientSocket).start();
              Pump.pump(clientSocket, socket).start();
            } else {
              log.error("exception", result.cause());
              socket.handler(null);
              log.debug("writing: " + toHex(errorResponse));
              socket.write(errorResponse);
              socket.close();
            }
          });
        };

        if (useAuth) {
          socket.handler(buffer3 -> {
            log.debug("auth handler");
            log.debug("got request: " + toHex(buffer3));
            Buffer authReply = Buffer.buffer(new byte[] { 1, (byte) username.length() });
            authReply.appendString(username);
            authReply.appendByte((byte) username.length());
            authReply.appendString(username);
            if (!buffer3.equals(authReply)) {
              log.debug("expected " + toHex(authReply) + ", got " + toHex(buffer3));
              socket.handler(null);
              log.debug("writing: " + toHex(authFailed));
              socket.write(authFailed);
              socket.close();
            } else {
              socket.handler(handler);
              log.debug("writing: " + toHex(authSuccess));
              socket.write(authSuccess);
            }
          });
          log.debug("writing: " + toHex(serverReplyAuth));
          socket.write(serverReplyAuth);
        } else {
          socket.handler(handler);
          log.debug("writing: " + toHex(serverReply));
          socket.write(serverReply);
        }
      });
    });
    server.listen(result -> {
      log.debug("socks5 server started");
      finishedHandler.handle(null);
    });
  }

  private String toHex(Buffer buffer) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < buffer.length(); i++) {
      sb.append(String.format("%02X ", buffer.getByte(i)));
    }
    return sb.toString();
  }

  /**
   * Stop the server.
   *
   * <p>Doesn't wait for the close operation to finish
   */
  @Override
  public void stop() {
    if (server != null) {
      server.close();
      server = null;
    }
  }

  @Override
  public int getPort() {
    return PORT;
  }
}
