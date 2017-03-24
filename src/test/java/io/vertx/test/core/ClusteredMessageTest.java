package io.vertx.test.core;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.net.impl.ServerID;
import org.junit.Test;

public class ClusteredMessageTest extends VertxTestBase {    
    @Test
    public void testMessagePrefixLengthConstant() throws Exception {
        MessageCodec<Object, Object> emptyMessageCodec = new EmptyMessageCodec<>();
        ClusteredMessage testMessage = new ClusteredMessage<>(
                new ServerID(1234, "foo.com"),
                "", "", MultiMap.caseInsensitiveMultiMap(), new Object(), emptyMessageCodec,
                true,
                null
        );
        
        int estimate = testMessage.estimateMessagePrefixLength();
        Buffer encoded = testMessage.encodeToWire();
        
        assertTrue(encoded.length() <= estimate);
        assertEquals(estimate, encoded.length());
        assertErrorRate(estimate, encoded.length(), 0.10);
    }
    
    @Test
    public void testMessagePrefixLengthEstimation() throws Exception {
        MessageCodec<Object, Object> fixedMessageCodec = new FixedLengthMessageCodec<>("averageCodecName", 256);
        
        CaseInsensitiveHeaders testHeaders = new CaseInsensitiveHeaders();
        testHeaders.add("Test-Header", "Headers did nothing wrong");
        testHeaders.add("Authorization", "Bearer ABCDEF");
        
        ClusteredMessage testMessage = new ClusteredMessage<>(
                new ServerID(1234, "foo.com"),
                "", "", testHeaders, new Object(), fixedMessageCodec,
                true,
                null
        );
        
        int estimate = testMessage.estimateMessagePrefixLength();
        Buffer encoded = testMessage.encodeToWire();
        
        assertTrue(encoded.length() <= estimate);
        assertErrorRate(estimate, encoded.length(), 0.20);
    }
    
    private void assertErrorRate(int expected, int actual, double errorRate) {
        double actualError = Math.abs((double) expected / actual - 1.0);
        System.out.println(actual + " " + expected + " " + actualError);
        assertTrue("Expected value has error rate of " + actualError + " instead of expected " + errorRate, actualError < errorRate);
    }
    
    private class EmptyMessageCodec<S, R> implements MessageCodec<S, R> {
        @Override
        public void encodeToWire(Buffer buffer, S s) {

        }

        @Override
        public R decodeFromWire(int pos, Buffer buffer) {
            return null;
        }

        @Override
        public R transform(S s) {
            return null;
        }

        @Override
        public String name() {
            return "";
        }

        @Override
        public byte systemCodecID() {
            return -1;
        }

        @Override
        public int expectedLength(S s) {
            return 0;
        }
    }
    
    private class FixedLengthMessageCodec<S, R> implements MessageCodec<S, R> {
        private final String name;
        private final int length;
        
        public FixedLengthMessageCodec(String name, int length) {
            this.name = name;
            this.length = length;
        }
        
        @Override
        public void encodeToWire(Buffer buffer, S s) {
            
        }

        @Override
        public R decodeFromWire(int pos, Buffer buffer) {
            return null;
        }

        @Override
        public R transform(S s) {
            return null;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public byte systemCodecID() {
            return -1;
        }

        @Override
        public int expectedLength(S s) {
            return length;
        }
    }
}
