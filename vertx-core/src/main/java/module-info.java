module io.vertx.core {

  requires io.vertx.core.logging;
  requires com.fasterxml.jackson.core;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.codec.compression;
  requires io.netty.codec.dns;
  requires io.netty.codec.http;
  requires io.netty.codec.http2;
  requires io.netty.incubator.codec.http3;
  requires io.netty.incubator.codec.classes.quic;
  requires io.netty.common;
  requires io.netty.handler;
  requires io.netty.handler.proxy;
  requires io.netty.resolver;
  requires io.netty.resolver.dns;
  requires io.netty.transport;
  requires java.compiler;
  requires java.logging;
  requires java.naming;

  // Optional

  requires static com.fasterxml.jackson.databind;
  requires static io.netty.transport.classes.io_uring;
  requires static io.netty.transport.classes.epoll;
  requires static io.netty.transport.classes.kqueue;
  requires static io.netty.transport.unix.common;
  requires static io.netty.codec.haproxy;

  // Annotation processing

  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;
  requires static io.vertx.docgen;

  // Compile time only

  requires static org.apache.logging.log4j;
  requires static org.slf4j;

  // Uses

  uses io.vertx.core.spi.VertxServiceProvider;
  uses io.vertx.core.spi.VerticleFactory;
  uses io.vertx.core.spi.JsonFactory;

  // API

  exports io.vertx.core;
  exports io.vertx.core.datagram;
  exports io.vertx.core.net;
  exports io.vertx.core.net.endpoint;
  exports io.vertx.core.json;
  exports io.vertx.core.json.jackson;
  exports io.vertx.core.json.pointer;
  exports io.vertx.core.buffer;
  exports io.vertx.core.eventbus;
  exports io.vertx.core.shareddata;
  exports io.vertx.core.dns;
  exports io.vertx.core.http;
  exports io.vertx.core.parsetools;
  exports io.vertx.core.tracing;
  exports io.vertx.core.metrics;
  exports io.vertx.core.streams;
  exports io.vertx.core.spi;
  exports io.vertx.core.file;
  exports io.vertx.core.transport;

  // SPI

  exports io.vertx.core.spi.tracing;
  exports io.vertx.core.spi.metrics;
  exports io.vertx.core.spi.context.storage;
  exports io.vertx.core.spi.context.executor;
  exports io.vertx.core.spi.cluster;
  exports io.vertx.core.spi.file;
  exports io.vertx.core.spi.json;
  exports io.vertx.core.spi.observability;
  exports io.vertx.core.spi.endpoint;
  exports io.vertx.core.spi.transport;
  exports io.vertx.core.spi.tls;

  // Internal API

  exports io.vertx.core.internal;
  exports io.vertx.core.internal.http;
  exports io.vertx.core.internal.buffer;
  exports io.vertx.core.internal.net;
  exports io.vertx.core.internal.net.endpoint;
  exports io.vertx.core.internal.pool;
  exports io.vertx.core.internal.tls;
  exports io.vertx.core.internal.threadchecker;
  exports io.vertx.core.internal.concurrent;
  exports io.vertx.core.internal.resource;

  // Testing

  exports io.vertx.core.impl to io.vertx.core.tests;
  exports io.vertx.core.impl.cpu to io.vertx.core.tests;
  exports io.vertx.core.impl.future to io.vertx.core.tests;
  exports io.vertx.core.impl.utils to io.vertx.core.tests;
  exports io.vertx.core.net.impl to io.vertx.core.tests;
  exports io.vertx.core.shareddata.impl to io.vertx.core.tests;
  exports io.vertx.core.buffer.impl to io.vertx.core.tests;
  exports io.vertx.core.streams.impl to io.vertx.core.tests;
  exports io.vertx.core.eventbus.impl to io.vertx.core.tests;
  exports io.vertx.core.eventbus.impl.clustered to io.vertx.core.tests;
  exports io.vertx.core.spi.cluster.impl to io.vertx.core.tests;
  exports io.vertx.core.file.impl to io.vertx.core.tests;
  exports io.vertx.core.http.impl to io.vertx.core.tests;
  exports io.vertx.core.http.impl.headers to io.vertx.core.tests;
  exports io.vertx.core.http.impl.ws to io.vertx.core.tests;
  exports io.vertx.core.json.pointer.impl to io.vertx.core.tests;
  exports io.vertx.core.impl.transports to io.vertx.core.tests;
  exports io.vertx.core.net.impl.pkcs1 to io.vertx.core.tests;
  exports io.vertx.core.spi.cluster.impl.selector to io.vertx.core.tests;
  exports io.vertx.core.impl.verticle to io.vertx.core.tests;
  exports io.vertx.core.impl.deployment to io.vertx.core.tests;

}
