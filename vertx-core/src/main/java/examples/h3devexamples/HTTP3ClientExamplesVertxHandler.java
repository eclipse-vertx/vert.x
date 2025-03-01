/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples.h3devexamples;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.Http3Utils;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP3ClientExamplesVertxHandler {
  protected NetClientOptions createNetClientOptions() {
    NetClientOptions options = new NetClientOptions();
    options
      .setHttp3(true)
      .getSslOptions()
      .setApplicationLayerProtocols(Http3Utils.supportedApplicationProtocols());
    options
      .setSslEngineOptions(new JdkSSLEngineOptions())
      .setUseAlpn(true)
      .setSsl(true)
      .setTrustAll(true)
//      .setHostnameVerificationAlgorithm("HTTPS")
      .setHostnameVerificationAlgorithm("")
//      .setTrustOptions(Trust.SERVER_JKS.get())
      .setProtocolVersion(HttpVersion.HTTP_3);


    return options;
  }

  public void example02Local(Vertx vertx) {

/*
    HttpClientOptions options = new HttpClientOptions().
      setSsl(true).
      setIdleTimeout(1).
      setReadIdleTimeout(1).
      setWriteIdleTimeout(1).
      setIdleTimeoutUnit(TimeUnit.HOURS).
      setUseAlpn(true).
      setForceSni(true).
      setVerifyHost(false).
      setTrustAll(true).
      setProtocolVersion(HttpVersion.HTTP_3);

    options
      .getSslOptions()
      .setSslHandshakeTimeout(1)
      .setSslHandshakeTimeoutUnit(TimeUnit.HOURS);
    HttpClient client = vertx.createHttpClient(options);
*/

    String path = "/";
    int port = 8090;
    String host = "localhost";

/*
    AtomicInteger requests = new AtomicInteger();

    int n = 5;

    for (int i = 0; i < n; i++) {
      int counter = i + 1;
      client.request(HttpMethod.GET, port, host, path)
        .compose(req -> req.send("Msg " + counter))
        .compose(HttpClientResponse::body)
        .onSuccess(body -> System.out.println(
          "Msg" + counter + " response body is: " + body))
        .onComplete(event -> requests.incrementAndGet())
        .onFailure(Throwable::printStackTrace)
      ;
    }
*/

    NetClient client = vertx.createNetClient(createNetClientOptions().setConnectTimeout(1000));


    client.connect(SocketAddress.inetSocketAddress(8090, "localhost")).onSuccess(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      StringBuilder part1 = new StringBuilder();
      part1.append("1".repeat(1200));
      part1.append("2".repeat(1200));
      part1.append("3".repeat(1200));
      part1.append("4".repeat(1200));

      StringBuilder part2 = new StringBuilder();
      part2.append("3".repeat(1200));
      part2.append("4".repeat(1200));

      soi.write(Buffer.buffer(part1.toString()));
//      soi.write(Buffer.buffer(part2.toString()));
      // soi.messageHandler(msg -> fail("Unexpected"));
      soi.messageHandler(msg -> {
        ByteBuf byteBuf = (ByteBuf) msg;

        byte[]arr = new byte[byteBuf.readableBytes()];
        byteBuf.copy().readBytes(arr);
        System.out.println("received ByteBuf is: " + new String(arr));


        if(!byteBuf.isDirect()) throw new RuntimeException();
        if(1 != byteBuf.refCnt()) throw new RuntimeException();
//        if(!"Hello World".equals(byteBuf.toString(StandardCharsets.UTF_8))) throw new RuntimeException();
        if(!byteBuf.release()) throw new RuntimeException();
        if(0 != byteBuf.refCnt()) throw new RuntimeException();
        System.out.println("OK");
      });
    }).onFailure(Throwable::printStackTrace);






    int n = 10000;
    int i = 0;
    while (i != n) {
      i++;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    vertx.close();

  }

  public static void main(String[] args) {
    Vertx vertx =
      Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1_000_000_000));
    new HTTP3ClientExamplesVertxHandler().example02Local(vertx);
  }
}
