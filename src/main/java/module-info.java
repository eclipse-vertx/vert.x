module io.vertx.core {

  requires com.fasterxml.jackson.core;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.codec.dns;
  requires io.netty.codec.haproxy;
  requires io.netty.codec.http;
  requires io.netty.codec.http2;
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
  requires static io.netty.transport.classes.epoll;
  requires static io.netty.transport.classes.kqueue;
  requires static io.netty.transport.unix.common;

  // Annotation processing

  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;

  // Compile time only

  requires static org.apache.logging.log4j;
  requires static org.slf4j;
  requires static vertx.docgen;

  // For resting

  requires static com.fasterxml.jackson.annotation; // For testing
  requires static java.management; // For testing

  // SPI

  uses io.vertx.core.spi.VertxServiceProvider;
  uses io.vertx.core.spi.VerticleFactory;

  // API

  exports io.vertx.core;
  exports io.vertx.core.net;
  exports io.vertx.core.json;
  exports io.vertx.core.buffer;
  exports io.vertx.core.eventbus;
  exports io.vertx.core.shareddata;
  exports io.vertx.core.dns;
  exports io.vertx.core.http;
  exports io.vertx.core.tracing;
  exports io.vertx.core.metrics;
  exports io.vertx.core.streams;
  exports io.vertx.core.spi;
  exports io.vertx.core.file;
  exports io.vertx.core.spi.tracing;
  exports io.vertx.core.spi.metrics;

  // Internal API

  exports io.vertx.core.internal;
  exports io.vertx.core.internal.logging;
  exports io.vertx.core.internal.buffer;
  exports io.vertx.core.internal.net;
  exports io.vertx.core.internal.pool;
}
