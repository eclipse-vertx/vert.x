package io.vertx.core.streams;

import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.streams.impl.InboundReadQueue;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.function.Consumer;

public class ConnectionBaseTest extends VertxTestBase {

  static class TestConnection extends ConnectionBase implements Consumer<Object> {

    private final InboundReadQueue<Object> queue;
    private final ContextInternal testContext;
    private volatile Handler<Object> consumer;

    public TestConnection(ContextInternal context, ChannelHandlerContext chctx, ContextInternal testContext) {
      super(context, chctx);

      this.queue = new InboundReadQueue<>(this);
      this.testContext = testContext;
    }
    @Override
    public NetworkMetrics metrics() {
      return null;
    }

    @Override
    protected void handleMessage(Object msg) {
      if (!queue.add(msg)) {
        doPause();
      }
    }

    @Override
    public void accept(Object o) {
      testContext.emit(o, msg -> {
        // Ack from here when done ????
      });
    }
  }

  @Test
  public void testFoo() {




  }

}
