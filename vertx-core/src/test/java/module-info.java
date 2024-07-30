import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.test.fakecluster.FakeClusterManager;
import io.vertx.tests.deployment.ClasspathVerticleFactory;

open module io.vertx.tests {
  requires io.vertx.codegen.api;
  requires io.vertx.core;
  requires io.vertx.core.logging;
  requires assertj.core;
  requires org.hamcrest;
  requires junit;
  requires java.management;
  requires java.logging;

  requires org.slf4j;

  requires apacheds.protocol.dns;
  requires apacheds.i18n;
  requires mina.core;
  requires apacheds.protocol.shared;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.databind;
  requires io.netty.common;
  requires io.netty.buffer;
  requires io.netty.transport;
  requires io.netty.handler;
  requires io.netty.codec;
  requires io.netty.codec.http;
  requires io.netty.codec.haproxy;
  requires io.netty.codec.http2;
  requires io.netty.incubator.codec.http3;
  requires io.netty.resolver.dns;
  requires jmh.core;
  requires org.apache.logging.log4j.core;

  provides VertxServiceProvider with FakeClusterManager;
  provides VerticleFactory with ClasspathVerticleFactory, io.vertx.tests.vertx.AccessEventBusFromInitVerticleFactory;

}
