package org.vertx.java.core.datagram;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

public interface DatagramSocket {

  DatagramSocket write(Buffer buffer, InetSocketAddress remote, Handler<AsyncResult<Void>> handler);
  DatagramSocket write(Buffer buffer, Handler<AsyncResult<Void>> handler);

  DatagramSocket bind(InetSocketAddress local, Handler<AsyncResult<DatagramSocket>> handler);
  DatagramSocket connect(InetSocketAddress remote, Handler<AsyncResult<DatagramSocket>> handler);

  DatagramSocket disconnect(Handler<AsyncResult<Void>> handler);

  void close(Handler<AsyncResult<Void>> handler);


  /**
   * Return {@code true} if the {@link DatagramSocket} is connected to the remote peer. In this case it's possible to
   * use {@link #write(org.vertx.java.core.buffer.Buffer, org.vertx.java.core.Handler)} and so not need to specify
   * a remote address.
   */
  boolean isConnected();

  /**
   * Joins a multicast group and notifies the {@link io.netty.channel.ChannelFuture} once the operation completes.
   */
  DatagramSocket joinGroup(InetAddress multicastAddress, Handler<AsyncResult<Void>> handler);


  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  DatagramSocket joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<Void>> handler);


  /**
   * Joins the specified multicast group at the specified interface and notifies the {@link Handler}
   * once the operation completes.
   */
  DatagramSocket joinGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<Void>> handler);

  /**
   * Leaves a multicast group and notifies the {@link Handler} once the operation completes.
   */
  DatagramSocket leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<Void>> handler);

  /**
   * Leaves a multicast group on a specified local interface and notifies the {@link Handler} once the
   * operation completes.
   */
  DatagramSocket leaveGroup(
          InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<Void>> handler);


  /**
   * Leave the specified multicast group at the specified interface using the specified source and notifies
   * the {@link Handler} once the operation completes.
   */
  DatagramSocket leaveGroup(
          InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
          Handler<AsyncResult<Void>> handler);

  /**
   * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface and notifies
   * the {@link Handler} once the operation completes.
   */
  DatagramSocket block(
          InetAddress multicastAddress, NetworkInterface networkInterface,
          InetAddress sourceToBlock, Handler<AsyncResult<Void>> handler);


  /**
   * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Handler} once
   * the operation completes.
   */
  DatagramSocket block(
          InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<Void>> handler);

}
