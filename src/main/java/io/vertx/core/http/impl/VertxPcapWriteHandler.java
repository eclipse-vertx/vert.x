package io.vertx.core.http.impl;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.pcap.PcapWriteHandler;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A handler that simply delegates to the built in {@link PcapWriteHandler}.
 * Vert.x needs this because the handler might not have been added to the processing pipeline
 * when the {@code channelRead} method is invoked, and thus the necessary setup is performed
 * in the {@code channelRegistered} method.
 * Furthermore, we want to support capturing the output of multiple Netty pipelines into a single file,
 * so for example both the output of an HTTP server and an HTTP Client can be inspected via the same file.
 */
public class VertxPcapWriteHandler extends ChannelDuplexHandler implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(VertxPcapWriteHandler.class);

  /**
   * The idea of this map is to control the usage of each throughout the entire Vert.x application.
   * When the same file is configured for multiple pipelines, we want each pipeline to write to the same
   * OutputStream, but we only want to close it when the last pipeline has been closed.
   */
  private static final ConcurrentMap<String, Metadata> fileToMetadata = new ConcurrentHashMap<>();

  private final PcapWriteHandler delegate;
  private final String pcapCaptureFile;

  public VertxPcapWriteHandler(String pcapCaptureFile) {
    this.pcapCaptureFile = pcapCaptureFile;
    Metadata metadata = fileToMetadata.computeIfAbsent(pcapCaptureFile, Metadata::new);
    // pcap contains a global header section that should only be written by the first handler
    int openedCount = metadata.openedCount.getAndIncrement();
    this.delegate = new PcapWriteHandler(metadata.outputStream, false, openedCount == 0);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    delegate.channelActive(ctx);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    delegate.channelActive(ctx);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    delegate.channelRead(ctx, msg);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    delegate.write(ctx, msg, promise);
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    int openedCount = fileToMetadata.get(pcapCaptureFile).openedCount.decrementAndGet();
    if (openedCount == 0) {
      delegate.handlerRemoved(ctx);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    delegate.exceptionCaught(ctx, cause);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  private static class Metadata {

    final AtomicInteger openedCount;
    final OutputStream outputStream;

    Metadata(String pcapFile) {
      openedCount = new AtomicInteger(0);
      outputStream = getOutputStream(pcapFile);
    }

    private OutputStream getOutputStream(String pcapFile) {
      try {
        return new FileOutputStream(pcapFile);
      } catch (FileNotFoundException e) {
        log.warn("Unable to open capture file for writing, so no capture information will be recorded.", e);
        return NullOutputStream.INSTANCE;
      }
    }

    private static class NullOutputStream extends OutputStream {

      static final NullOutputStream INSTANCE = new NullOutputStream();

      private NullOutputStream() {
      }

      @Override
      public void write(int b) throws IOException {

      }
    }
  }

}
