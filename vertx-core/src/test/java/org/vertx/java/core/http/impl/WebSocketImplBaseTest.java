package org.vertx.java.core.http.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.WebSocketFrame;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrameInternal;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.net.impl.ConnectionBase;

public class WebSocketImplBaseTest {

  /**
   * 
   * Testing plan:
   * 
   * === handleFrame ===
   * 
   * assumptions: frames are coming in the correct order, i.e. TEXT/BINARY is
   * followed by a CONTINUATION (if needed); no CONTINUATION without appropriate
   * TEXT/BINARY or another CONTINNUATION frames comes; no CONTINUATION directly
   * after a final frame comes. All these assumptions are assured by the Netty
   * WebSocketFrameDecoder
   * 
   * Note: only processing for the dataHandler is tested since the code for the
   * frameHandler code has not been changed
   * 
   * - an empty frame message -> handle on the dataHandler is invoked with an
   * empty buffer
   * 
   * - one final frame message -> handle on the dataHandler is invoked
   * 
   * - several final frame messages -> handle on the dataHandler is invoked once
   * per message
   * 
   * - one not final frame -> handle on the dataHandler is not invoked
   * 
   * - one multi-frame message is sent -> handle on the dataHandler is invoked
   * with the complete data
   * 
   * - several multi-frame messages are sent -> handle on the dataHandler is
   * invoked once per complete message
   * 
   * 
   * === getUTF8LongestPrefix ===
   * 
   * - str.length == 0 -> empty string
   * 
   * - limit == 0 -> empty string
   * 
   * - str.length == limit -> one complete string
   * 
   * - str.length == limit, but the last char is a two bytes char (in utf-8) ->
   * string without the last char
   * 
   * - str.length == limit - 1 -> complete string
   * 
   * - str.length == limit - 1, but the last char is a two bytes char (in utf-8)
   * -> complete string
   * 
   * - str.length == limit + 1 -> prefix of the length limit
   * 
   * === chunkMessage(String) ===
   * 
   * - str.length == 0 -> zero length string
   * 
   * - str.length == chunk_size - 1 -> one item list
   * 
   * - str.length == chunk size -> one item list
   * 
   * - str.length == chunk_size + 1 -> two items list
   * 
   * 
   * === writeTextFrameInternal ===
   * 
   * - sending null message -> an empty final text frame is sent
   * 
   * - sending an empty message -> an empty final text frame is sent
   * 
   * - sending one text message. msg.length = chunk_size - 1 -> one final frame
   * is sent
   * 
   * - sending several final text messages. msg.length = chunk_size -> several
   * final frames are written
   * 
   * - sending several text messages. msg.length == chunk_size + 1 -> non-final,
   * final, non-final, final
   * 
   * 
   * === chunkMessage(byte[]) ===
   * 
   * - array.length == 0 -> zero length string
   * 
   * - array.length == chunk_size - 1 -> one item list
   * 
   * - array.length == chunk size -> one item list
   * 
   * - array.length == chunk_size + 1 -> two items list
   * 
   * === writeBinaryFrameInternal ===
   * 
   * - sending null message -> an empty final binary frame is sent
   * 
   * - sending an empty message -> an empty final binary frame is sent
   * 
   * - sending one text message. msg.length = chunk_size - 1 -> one final binary
   * frame is sent
   * 
   * - sending several final text messages. msg.length = chunk_size -> several
   * final binary frames are written
   * 
   * - sending several binary messages. msg.length == chunk_size + 1 ->
   * non-final, final, non-final, final
   * 
   */

  private TestWebSocketImplBase webSocketImplBase;

  @Before
  public void init() {
    webSocketImplBase = new TestWebSocketImplBase(new MockConnection());
    webSocketImplBase.dataHandler = new MockDataHandler();
    webSocketImplBase.closed = false;
  }

  @Test
  public void testHandleFrame_emptyBinaryFrame() {
    WebSocketFrameInternal frame = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY);
    webSocketImplBase.handleFrame(frame);
    assertTrue(((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.size() == 1);
    assertEquals("", ((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.get(0).toString());
  }

  @Test
  public void testHandleFrame_finalTextFrame() {
    WebSocketFrameInternal frame = new DefaultWebSocketFrame("test_frame");
    webSocketImplBase.handleFrame(frame);
    assertTrue(((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.size() == 1);
    assertEquals("test_frame", ((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.get(0).toString());
  }

  @Test
  public void testHandleFrame_severalFinalBinaryFrame() {
    WebSocketFrameInternal frame0 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, Unpooled.copiedBuffer("test_frame_0".getBytes()));
    webSocketImplBase.handleFrame(frame0);
    WebSocketFrameInternal frame1 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, Unpooled.copiedBuffer("test_frame_1".getBytes()));
    webSocketImplBase.handleFrame(frame1);
    assertTrue(((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.size() == 2);
    assertEquals("test_frame_0", ((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.get(0));
    assertEquals("test_frame_1", ((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.get(1));
  }

  @Test
  public void testHandleFrame_oneNotFinalTextFrame() {
    WebSocketFrameInternal frame0 = new DefaultWebSocketFrame("test_frame_0", false);
    webSocketImplBase.handleFrame(frame0);
    assertTrue(((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.size() == 0);
  }

  @Test
  public void testHandleFrame_multiFrameBinaryMessage() {
    WebSocketFrameInternal frame0 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, Unpooled.copiedBuffer("test_frame_0".getBytes()),
        false);
    webSocketImplBase.handleFrame(frame0);
    WebSocketFrameInternal frame1 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION,
        Unpooled.copiedBuffer("test_frame_1".getBytes()), false);
    webSocketImplBase.handleFrame(frame1);
    WebSocketFrameInternal frame2 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION,
        Unpooled.copiedBuffer("test_frame_2".getBytes()), true);
    webSocketImplBase.handleFrame(frame2);
    assertTrue(((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.size() == 1);
    assertEquals("test_frame_0test_frame_1test_frame_2", ((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.get(0));
  }

  @Test
  public void testHandleFrame_twoMultiFrameTextMessages() {
    WebSocketFrameInternal frame00 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, Unpooled.copiedBuffer("test_frame_00".getBytes()),
        false);
    webSocketImplBase.handleFrame(frame00);
    WebSocketFrameInternal frame01 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION, Unpooled.copiedBuffer("test_frame_01"
        .getBytes()), false);
    webSocketImplBase.handleFrame(frame01);
    WebSocketFrameInternal frame02 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION, Unpooled.copiedBuffer("test_frame_02"
        .getBytes()), true);
    webSocketImplBase.handleFrame(frame02);
    WebSocketFrameInternal frame10 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, Unpooled.copiedBuffer("test_frame_10".getBytes()),
        false);
    webSocketImplBase.handleFrame(frame10);
    WebSocketFrameInternal frame11 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION, Unpooled.copiedBuffer("test_frame_11"
        .getBytes()), false);
    webSocketImplBase.handleFrame(frame11);
    WebSocketFrameInternal frame12 = new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION, Unpooled.copiedBuffer("test_frame_12"
        .getBytes()), true);
    webSocketImplBase.handleFrame(frame12);
    assertEquals(2, ((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.size());
    assertEquals("test_frame_00test_frame_01test_frame_02", ((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.get(0));
    assertEquals("test_frame_10test_frame_11test_frame_12", ((MockDataHandler) webSocketImplBase.dataHandler).receivedMessages.get(1));
  }

  @Test
  public void testGetUTF8LongestPrefix_emptyMessage() {
    String prefix = webSocketImplBase.getUTF8LongestPrefix("", 3);
    assertEquals("", prefix);
  }
  
  @Test
  public void testGetUTF8LongestPrefix_zeroLimit() {
    String prefix = webSocketImplBase.getUTF8LongestPrefix("abc", 0);
    assertEquals("", prefix);
  }
  
  @Test
  public void testGetUTF8LongestPrefix_messageLengthEqualsLimit() {
    String prefix = webSocketImplBase.getUTF8LongestPrefix("abc", 3);
    assertEquals("abc", prefix);
  }

  @Test
  public void testGetUTF8LongestPrefix_messageLengthEqualsLimitLastCharacterTwoBytes() {
    String prefix = webSocketImplBase.getUTF8LongestPrefix("abcä", 4);
    assertEquals("abc", prefix);
  }

  @Test
  public void testGetUTF8LongestPrefix_messageLengthEqualsLimitMinusOne() {
    String prefix = webSocketImplBase.getUTF8LongestPrefix("abc", 4);
    assertEquals("abc", prefix);
  }

  @Test
  public void testGetUTF8LongestPrefix_messageLengthEqualsLimitMinusOneLastCharTwoBytes() {
    String prefix = webSocketImplBase.getUTF8LongestPrefix("abcä", 5);
    assertEquals("abcä", prefix);
  }

  @Test
  public void testGetUTF8LongestPrefix_messageLengthEqualsLimitPlusOne() {
    String prefix = webSocketImplBase.getUTF8LongestPrefix("abcd", 3);
    assertEquals("abc", prefix);
  }

  @Test
  public void testWriteTextFrameInternal_nullMessage() throws UnsupportedEncodingException {
    webSocketImplBase.writeTextFrameInternal(null);
    assertEquals(1, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(wsFrame.isFinalFrame());
    assertEquals("", wsFrame.textData());
  }

  @Test
  public void testWriteTextFrameInternal_emptyMessage() throws UnsupportedEncodingException {
    webSocketImplBase.writeTextFrameInternal("");
    assertEquals(1, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(wsFrame.isFinalFrame());
    assertEquals("", wsFrame.textData());
  }

  @Test
  public void testWriteTextFrameInternal_messageLengthEqualsChunkMinusOne() throws UnsupportedEncodingException {
    webSocketImplBase.setChunkSize(4);
    webSocketImplBase.writeTextFrameInternal("abc");
    assertEquals(1, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(wsFrame.isFinalFrame());
    assertEquals("abc", wsFrame.textData());
  }

  @Test
  public void testWriteTextFrameInternal_severalMessagesLengthEqualsChunk() throws UnsupportedEncodingException {
    webSocketImplBase.setChunkSize(3);
    webSocketImplBase.writeTextFrameInternal("abc");
    webSocketImplBase.writeTextFrameInternal("def");
    assertEquals(2, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(wsFrame.isFinalFrame());
    assertEquals("abc", wsFrame.textData());
    DefaultWebSocketFrame wsFrame1 = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(1);
    assertTrue(wsFrame1.isFinalFrame());
    assertEquals("def", wsFrame1.textData());
  }

  @Test
  public void testWriteTextFrameInternal_severalMessagesLengthEqualsChunkPlusOne() throws UnsupportedEncodingException {
    webSocketImplBase.setChunkSize(3);
    webSocketImplBase.writeTextFrameInternal("abcd");
    webSocketImplBase.writeTextFrameInternal("efgh");
    assertEquals(4, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(!wsFrame.isFinalFrame());
    assertEquals("abc", wsFrame.textData());
    DefaultWebSocketFrame wsFrame1 = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(1);
    assertTrue(wsFrame1.isFinalFrame());
    assertEquals("d", wsFrame1.textData());

    DefaultWebSocketFrame wsFrame3 = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(2);
    assertTrue(!wsFrame3.isFinalFrame());
    assertEquals("efg", wsFrame3.textData());
    DefaultWebSocketFrame wsFrame4 = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(3);
    assertTrue(wsFrame4.isFinalFrame());
    assertEquals("h", wsFrame4.textData());
  }

  @Test
  public void testChunkMessageString_emptyString() {
    List<String> chunks = webSocketImplBase.chunkMessage("");
    assertTrue(chunks.isEmpty());
  }

  @Test
  public void testChunkMessageString_stringLengthEqualsChunkSizeMinusOne() {
    webSocketImplBase.setChunkSize(4);
    List<String> chunks = webSocketImplBase.chunkMessage("abc");
    assertEquals("abc", chunks.get(0));
  }

  @Test
  public void testChunkMessageString_stringLengthEqualsChunkSize() {
    webSocketImplBase.setChunkSize(4);
    List<String> chunks = webSocketImplBase.chunkMessage("abcd");
    assertEquals("abcd", chunks.get(0));
  }

  @Test
  public void testChunkMessageString_stringLengthEqualsChunkSizePlusOne() {
    webSocketImplBase.setChunkSize(4);
    List<String> chunks = webSocketImplBase.chunkMessage("abcde");
    assertEquals("abcd", chunks.get(0));
    assertEquals("e", chunks.get(1));
  }

  @Test
  public void testChunkMessageBytes_emptyArray() {
    byte[][] chunks = webSocketImplBase.chunkMessage(new byte[0]);
    assertEquals(0, chunks.length);
  }

  @Test
  public void testChunkMessageBytes_stringLengthEqualsChunkSizeMinusOne() {
    webSocketImplBase.setChunkSize(4);
    byte[][] chunks = webSocketImplBase.chunkMessage(new byte[] { (byte) 0, (byte) 1, (byte) 2 });
    assertTrue(Arrays.equals(new byte[] { (byte) 0, (byte) 1, (byte) 2 }, chunks[0]));
  }

  @Test
  public void testChunkMessageBytes_stringLengthEqualsChunkSize() {
    webSocketImplBase.setChunkSize(4);
    byte[][] chunks = webSocketImplBase.chunkMessage(new byte[] { (byte) 0, (byte) 1, (byte) 2, (byte) 3 });
    assertEquals(1, chunks.length);
    assertTrue(Arrays.equals(new byte[] { (byte) 0, (byte) 1, (byte) 2, (byte) 3 }, chunks[0]));
  }

  @Test
  public void testChunkMessageBytes_stringLengthEqualsChunkSizePlusOne() {
    webSocketImplBase.setChunkSize(3);
    byte[][] chunks = webSocketImplBase.chunkMessage(new byte[] { (byte) 0, (byte) 1, (byte) 2, (byte) 3 });
    assertEquals(2, chunks.length);
    assertTrue(Arrays.equals(new byte[] { (byte) 0, (byte) 1, (byte) 2 }, chunks[0]));
    assertTrue(Arrays.equals(new byte[] { (byte) 3 }, chunks[1]));
  }

  @Test
  public void testWriteBinaryFrameInternal_nullMessage() throws UnsupportedEncodingException {
    webSocketImplBase.writeBinaryFrameInternal(null);
    assertEquals(1, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(wsFrame.isFinalFrame());
    assertEquals("", wsFrame.textData());
  }

  @Test
  public void testWriteBinaryFrameInternal_emptyMessage() throws UnsupportedEncodingException {
    webSocketImplBase.writeBinaryFrameInternal(new Buffer());
    assertEquals(1, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(wsFrame.isFinalFrame());
    assertEquals("", wsFrame.textData());
  }

  @Test
  public void testWriteBinaryFrameInternal_messageLengthEqualsChunkMinusOne() throws UnsupportedEncodingException {
    webSocketImplBase.setChunkSize(4);
    webSocketImplBase.writeBinaryFrameInternal(new Buffer("abc"));
    assertEquals(1, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(wsFrame.isFinalFrame());
    assertEquals("abc", wsFrame.textData());
  }

  @Test
  public void testWriteBinaryFrameInternal_severalMessagesLengthEqualsChunk() throws UnsupportedEncodingException {
    webSocketImplBase.setChunkSize(3);
    webSocketImplBase.writeBinaryFrameInternal(new Buffer("abc"));
    webSocketImplBase.writeBinaryFrameInternal(new Buffer("def"));
    assertEquals(2, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(wsFrame.isFinalFrame());
    assertEquals("abc", wsFrame.textData());
    DefaultWebSocketFrame wsFrame1 = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(1);
    assertTrue(wsFrame1.isFinalFrame());
    assertEquals("def", wsFrame1.textData());
  }

  @Test
  public void testWriteBinaryFrameInternal_severalMessagesLengthEqualsChunkPlusOne() throws UnsupportedEncodingException {
    webSocketImplBase.setChunkSize(3);
    webSocketImplBase.writeBinaryFrameInternal(new Buffer("abcd"));
    webSocketImplBase.writeBinaryFrameInternal(new Buffer("efgh"));
    assertEquals(4, ((MockConnection) webSocketImplBase.conn).writtenObjects.size());
    DefaultWebSocketFrame wsFrame = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(0);
    assertTrue(!wsFrame.isFinalFrame());
    assertEquals("abc", wsFrame.textData());
    DefaultWebSocketFrame wsFrame1 = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(1);
    assertTrue(wsFrame1.isFinalFrame());
    assertEquals("d", wsFrame1.textData());

    DefaultWebSocketFrame wsFrame3 = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(2);
    assertTrue(!wsFrame3.isFinalFrame());
    assertEquals("efg", wsFrame3.textData());
    DefaultWebSocketFrame wsFrame4 = (DefaultWebSocketFrame) ((MockConnection) webSocketImplBase.conn).writtenObjects.get(3);
    assertTrue(wsFrame4.isFinalFrame());
    assertEquals("h", wsFrame4.textData());
  }

  private static class MockDataHandler implements Handler<Buffer> {

    List<String> receivedMessages = new ArrayList<>();

    @Override
    public void handle(Buffer event) {
      receivedMessages.add(event.toString());
    }

  }

  private static class TestWebSocketImplBase extends WebSocketImplBase<Object> {

    protected TestWebSocketImplBase(MockConnection mockConnection) {
      super(new DefaultVertx(), mockConnection);
    }

    @Override
    public Object writeBinaryFrame(Buffer data) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object writeTextFrame(String str) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object closeHandler(Handler<Void> handler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object frameHandler(Handler<WebSocketFrame> handler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object endHandler(Handler<Void> endHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object dataHandler(Handler<Buffer> handler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object pause() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object resume() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object exceptionHandler(Handler<Throwable> handler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object write(Buffer data) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object setWriteQueueMaxSize(int maxSize) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object drainHandler(Handler<Void> handler) {
      throw new UnsupportedOperationException();
    }

  }

  private static class MockConnection extends ConnectionBase {

    List<Object> writtenObjects = new ArrayList<>();

    protected MockConnection() {
      super(null, null, null);
    }

    @Override
    protected void handleInterestedOpsChanged() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture write(Object obj) {
      writtenObjects.add(obj);
      return null;
    }

  }

}
