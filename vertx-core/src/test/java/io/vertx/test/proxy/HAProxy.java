package io.vertx.test.proxy;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.*;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class HAProxy extends TestProxyBase {
  private static final Logger log = LoggerFactory.getLogger(HAProxy.class);
  private static final String HOST = "localhost";
  private static final int PORT = 11080;
  private final SocketAddress remoteAddress;
  private final Buffer header;
  private NetServer server;
  private NetClient client;

  //Used to test unknown protocol
  private SocketAddress connectionRemoteAddress;
  private SocketAddress connectionLocalAddress;

  public HAProxy(SocketAddress remoteAddress, Buffer header) {
    this.remoteAddress = remoteAddress;
    this.header = header;
  }

  public HAProxy(String host, int port, Buffer header) {
    this(SocketAddress.inetSocketAddress(port, host), header);
  }

  @Override
  public int defaultPort() {
    return PORT;
  }

  @Override
  protected Future<NetServer> start0(Vertx vertx) {
    NetServerOptions options = createNetServerOptions();
    options.setHost(HOST).setPort(PORT);
    server = vertx.createNetServer(options);
    client = vertx.createNetClient(createNetClientOptions());
    server.connectHandler(socket -> {
      socket.pause();
      client.connect(remoteAddress).onComplete(result -> {
        if (result.succeeded()) {
          log.debug("connected, writing header");
          NetSocket clientSocket = result.result();
          connectionRemoteAddress = clientSocket.remoteAddress();
          connectionLocalAddress = clientSocket.localAddress();
          clientSocket.write(header).onSuccess(u -> {
            log.debug("starting pump");
            socket.closeHandler(v -> clientSocket.close());
            clientSocket.closeHandler(v -> socket.close());
            socket.pipeTo(clientSocket);
            clientSocket.pipeTo(socket);
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

    return server.listen();
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

  public SocketAddress getConnectionRemoteAddress() {
    return connectionRemoteAddress;
  }

  public SocketAddress getConnectionLocalAddress() {
    return connectionLocalAddress;
  }

  public static Buffer createVersion1TCP4ProtocolHeader(SocketAddress remote, SocketAddress local) {
    return Buffer.buffer(String.format("PROXY TCP4 %s %s %d %d\r\n", remote.hostAddress(), local.hostAddress(), remote.port(), local.port()));
  }

  public static Buffer createVersion1TCP6ProtocolHeader(SocketAddress remote, SocketAddress local) {
    return Buffer.buffer(String.format("PROXY TCP6 %s %s %d %d\r\n", remote.hostAddress(), local.hostAddress(), remote.port(), local.port()));
  }

  public static Buffer createVersion1UnknownProtocolHeader() {
    return Buffer.buffer("PROXY UNKNOWN\r\n");
  }

  public static Buffer createVersion2TCP4ProtocolHeader(SocketAddress remote, SocketAddress local) {
    return createIPv4IPv6ProtocolHeader((byte) 0x11, remote, local);
  }

  public static Buffer createVersion2TCP6ProtocolHeader(SocketAddress remote, SocketAddress local) {
    return createIPv4IPv6ProtocolHeader((byte) 0x21, remote, local);
  }

  public static Buffer createVersion2UDP4ProtocolHeader(SocketAddress remote, SocketAddress local) {
    return createIPv4IPv6ProtocolHeader((byte) 0x12, remote, local);
  }

  public static Buffer createVersion2UDP6ProtocolHeader(SocketAddress remote, SocketAddress local) {
    return createIPv4IPv6ProtocolHeader((byte) 0x22, remote, local);
  }

  public static Buffer createVersion2UnixStreamProtocolHeader(SocketAddress remote, SocketAddress local) {
    return createUnixStreamDatagramProtocolHeader((byte) 0x31, remote, local);
  }

  public static Buffer createVersion2UnixDatagramProtocolHeader(SocketAddress remote, SocketAddress local) {
    return createUnixStreamDatagramProtocolHeader((byte) 0x32, remote, local);
  }

  public static Buffer createVersion2UnknownProtocolHeader() {
    return createVersion2ProtocolHeader((byte) 0x00, Buffer.buffer());
  }

  private static Buffer createUnixStreamDatagramProtocolHeader(byte protocolByte, SocketAddress remote, SocketAddress local) {
    Buffer addresses = Stream.of(remote.path(), local.path())
      .map(s -> {
        byte[] result = new byte[108];
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, result, 0, Math.min(bytes.length, result.length));
        return result;
      })
      .reduce(Buffer.buffer(), Buffer::appendBytes, (b0, b1) -> b1);
    return createVersion2ProtocolHeader(protocolByte, addresses);
  }

  private static Buffer createIPv4IPv6ProtocolHeader(byte protocolByte, SocketAddress remote, SocketAddress local) {
    try {
      InetAddress remoteInetAddress = InetAddress.getByName(remote.hostAddress());
      InetAddress localInetAddress = InetAddress.getByName(local.hostAddress());
      return createVersion2ProtocolHeader(
        protocolByte,
        Buffer.buffer()
          .appendBytes(remoteInetAddress.getAddress())
          .appendBytes(localInetAddress.getAddress())
          .appendUnsignedShort(remote.port())
          .appendUnsignedShort(local.port())
      );
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }


  private static Buffer createVersion2ProtocolHeader(byte protocolByte, Buffer address) {
    byte[] header = new byte[15];
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
    header[13] = protocolByte;

    header[14] = 0x00;        // Remaining Bytes

    return Buffer.buffer(header)
      .appendByte((byte) address.length())
      .appendBuffer(address);
  }
}
