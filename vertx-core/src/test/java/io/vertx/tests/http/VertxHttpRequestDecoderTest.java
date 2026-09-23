package io.vertx.tests.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.vertx.core.http.Http1ServerConfig;
import io.vertx.core.http.impl.http1.VertxHttpRequestDecoder;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase2;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class VertxHttpRequestDecoderTest extends VertxTestBase2 {

  private final VertxHttpRequestDecoder decoder = new VertxHttpRequestDecoder(new Http1ServerConfig());
  EmbeddedChannel channel = new EmbeddedChannel(
    decoder,
    new ChannelDuplexHandler() {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
          HttpRequest request = (HttpRequest) msg;
          ctx.writeAndFlush(request);
        }
      }
    });

  @Test
  public void testContentTypeInterning() {
    assertSame(HttpHeaderNames.CONTENT_TYPE, decodeHeader("content-type"));
    assertSame(decoder.contentTypeAsciiString(), decodeHeader("Content-Type"));
    String header = "content-type";
    checkCaseVariation(header.toCharArray(), 0);
    checkCaseMutation(header.toCharArray(), 0);
  }

  @Test
  public void testContentLengthInterning() {
    assertSame(HttpHeaderNames.CONTENT_LENGTH, decodeHeader("content-length"));
    assertSame(decoder.contentLengthAsciiString(), decodeHeader("Content-Length"));
    String header = "content-length";
    checkCaseVariation(header.toCharArray(), 0);
    checkCaseMutation(header.toCharArray(), 0);
  }

  @Test
  public void testHostInterning() {
    assertSame(HttpHeaderNames.HOST, decodeHeader("host"));
    assertSame(decoder.hostAsciiString(), decodeHeader("Host"));
    String header = "host";
    checkCaseVariation(header.toCharArray(), 0);
    checkCaseMutation(header.toCharArray(), 0);
  }

  @Test
  public void testConnectionInterning() {
    assertSame(HttpHeaderNames.CONNECTION, decodeHeader("connection"));
    assertSame(decoder.connectionAsciiString(), decodeHeader("Connection"));
    String header = "connection";
    checkCaseVariation(header.toCharArray(), 0);
    checkCaseMutation(header.toCharArray(), 0);
  }

  @Test
  public void testAcceptInterning() {
    assertSame(HttpHeaderNames.ACCEPT, decodeHeader("accept"));
    assertSame(decoder.acceptAsciiString(), decodeHeader("Accept"));
    String header = "accept";
    checkCaseVariation(header.toCharArray(), 0);
    checkCaseMutation(header.toCharArray(), 0);
  }

  private void checkCaseVariation(char[] header, int pos) {
    if (pos < header.length) {
      char c = header[pos];
      header[pos] = Character.toLowerCase(c);
      checkCaseVariation(header, pos + 1);
      header[pos] = Character.toUpperCase(c);
      checkCaseVariation(header, pos + 1);
    } else {
      String s = new String(header);
      CharSequence decoded = decodeHeader(s);
      assertEquals(decoded.toString(), s);
    }
  }

  private void checkCaseMutation(char[] header, int pos) {
    if (pos < header.length) {
      char c = header[pos];
      if (Character.isLetter(c)) {
        header[pos] = (char)('a' + TestUtils.randomPositiveInt() % 26);
        String s = new String(header);
        CharSequence decoded = decodeHeader(s);
        assertEquals(decoded.toString(), s);
        header[pos] = c;
      }
      checkCaseMutation(header, pos + 1);
    }
  }

  private CharSequence decodeHeader(String header) {
    channel.writeInbound(Unpooled.copiedBuffer(
      "GET / HTTP/1.1\r\n" +
        header + ": 0\r\n" +
        "\r\n", StandardCharsets.UTF_8));
    HttpRequest request = channel.readOutbound();
    HttpHeaders headers = request.headers();
    Iterator<Map.Entry<CharSequence, CharSequence>> it = headers.iteratorCharSequence();
    Map.Entry<CharSequence, CharSequence> entry = it.next();
    return entry.getKey();
  }
}
