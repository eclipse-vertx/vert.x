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
import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.PemKeyCertOptions;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP2ServerExamplesVertxHandler {

  protected NetServerOptions createNetServerOptions() {
    NetServerOptions options = new NetServerOptions();
    options.setPort(8090);

    options.setUseAlpn(true).setSsl(true);

    SelfSignedCertificate ssc = null;
    try {
      ssc = new SelfSignedCertificate();
    } catch (CertificateException e) {
      throw new RuntimeException(e);
    }
    options.setKeyCertOptions(new PemKeyCertOptions()
      .setCertPath(ssc.certificate().getAbsolutePath())
      .setKeyPath(ssc.privateKey().getAbsolutePath())
    );


    return options;
  }

  public void example02Server(Vertx vertx) throws Exception {
    NetServer server = vertx.createNetServer(createNetServerOptions());


    server.connectHandler(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.messageHandler(msg -> {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[]arr = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(arr);
        System.out.println("new String(arr) = " + new String(arr));
        if (!byteBuf.isDirect()) throw new RuntimeException();
        if (1 != byteBuf.refCnt()) throw new RuntimeException();
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeCharSequence("OK", StandardCharsets.UTF_8);
        soi.writeMessage(buffer).onSuccess(v -> {
//          if (0 != byteBuf.refCnt()) throw new RuntimeException();
          System.out.println("OK");
        });
      });
    });
    server.exceptionHandler(Throwable::printStackTrace);

    server.listen();
  }

  public static void main(String[] args) throws Exception {
    VertxOptions options = new VertxOptions()
      .setBlockedThreadCheckInterval(1_000_000_000);

    Vertx vertx = Vertx.vertx(options);
    new HTTP2ServerExamplesVertxHandler().example02Server(vertx);
  }
}
