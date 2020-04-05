package io.vertx.test.proxy;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.core.streams.Pump;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HAProxy {
  private static final Logger log = LoggerFactory.getLogger(HAProxy.class);
  private static final String HOST = "localhost";
  private static final int PORT = 11080;
  private final String remoteHost;
  private final int remotePort;
  private final Buffer header;
  private NetServer server;

  public HAProxy(String remoteHost, int remotePort, Buffer header) {
    this.remoteHost = remoteHost;
    this.remotePort = remotePort;
    this.header = header;
  }

  public HAProxy start(Vertx vertx) throws Exception {
    NetServerOptions options = new NetServerOptions();
    options.setHost(HOST).setPort(PORT);
    server = vertx.createNetServer(options);
    server.connectHandler(socket -> {
      socket.pause();
      NetClient netClient = vertx.createNetClient(new NetClientOptions());
      netClient.connect(remotePort, remoteHost, result -> {
        if (result.succeeded()) {
          log.debug("connected, writing header");
          NetSocket clientSocket = result.result();
          clientSocket.write(header).onSuccess(u -> {
            log.debug("starting pump");
            socket.closeHandler(v -> clientSocket.close());
            clientSocket.closeHandler(v -> socket.close());
            Pump.pump(socket, clientSocket).start();
            Pump.pump(clientSocket, socket).start();
            socket.resume();
          }).onFailure(u -> {
            log.error("exception writing header", result.cause());
            socket.close();
          });
        } else {
          log.error("exception", result.cause());
          socket.close();
        }
      });
    });

    CompletableFuture<Void> fut = new CompletableFuture<>();
    server.listen(ar -> {
      if (ar.succeeded()) {
        fut.complete(null);
      } else {
        fut.completeExceptionally(ar.cause());
      }
    });
    fut.get(10, TimeUnit.SECONDS);
    log.debug("HAProxy server started");
    return this;
  }

  public void stop() {
    if (server != null) {
      server.close();
      server = null;
    }
  }

  public String getHost() {
    return HOST;
  }

  public int getPort() {
    return PORT;
  }

  public static Buffer createVersion1ProtocolHeader(String remoteAddress, int remotePort, String localAddress, int localPort) {
    return Buffer.buffer(String.format("PROXY TCP4 %s %s %d %d\r\n", remoteAddress, localAddress, remotePort, localPort));
  }

  public static Buffer createVersion2ProtocolHeader(String remoteAddress, int remotePort, String localAddress, int localPort) {
    try {
      InetAddress remoteInetAddress = InetAddress.getByName(remoteAddress);
      InetAddress localInetAddress = InetAddress.getByName(localAddress);

      byte[] header = new byte[16];
      header[0] = 0x0D;        // Binary Prefix
      header[1] = 0x0A;        // -----
      header[2] = 0x0D;        // -----
      header[3] = 0x0A;        // -----
      header[4] = 0x00;        // -----
      header[5] = 0x0D;        // -----
      header[6] = 0x0A;        // -----
      header[7] = 0x51;        // -----
      header[8] = 0x55;        // -----
      header[9] = 0x49;        // -----
      header[10] = 0x54;        // -----
      header[11] = 0x0A;        // -----

      header[12] = 0x21;        // v2, cmd=PROXY
      header[13] = 0x11;        // TCP over IPv4

      header[14] = 0x00;        // Remaining Bytes
      header[15] = 0x0c;        // -----

      return Buffer.buffer(header)
        .appendBytes(remoteInetAddress.getAddress())
        .appendBytes(localInetAddress.getAddress())
        .appendUnsignedShort(remotePort)
        .appendUnsignedShort(localPort);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
