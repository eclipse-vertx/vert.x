/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 *
 * == Streams
 *
 * There are several objects in Vert.x that allow items to be read from and written.
 *
 * In previous versions the {@link io.vertx.core.streams} package was manipulating {@link io.vertx.core.buffer.Buffer}
 * objects exclusively. From now, streams are not anymore coupled to buffers and work with any kind of objects.
 *
 * In Vert.x, calls to write item return immediately and writes are internally queued.
 *
 * It's not hard to see that if you write to an object faster than it can actually write the data to
 * its underlying resource then the write queue could grow without bound - eventually resulting in
 * exhausting available memory.
 *
 * To solve this problem a simple flow control capability is provided by some objects in the Vert.x API.
 *
 * Any flow control aware object that can be written-to implements {@link io.vertx.core.streams.ReadStream},
 * and any flow control object that can be read-from is said to implement {@link io.vertx.core.streams.WriteStream}.
 *
 * Let's take an example where we want to read from a `ReadStream` and write the data to a `WriteStream`.
 *
 * A very simple example would be reading from a {@link io.vertx.core.net.NetSocket} on a server and writing back to the
 * same `NetSocket` - since `NetSocket` implements both `ReadStream` and `WriteStream`, but you can
 * do this between any `ReadStream` and any `WriteStream`, including HTTP requests and response,
 * async files, WebSockets, etc.
 *
 * A naive way to do this would be to directly take the data that's been read and immediately write it
 * to the `NetSocket`, for example:
 *
 * [source,$lang]
 * ----
 * {@link examples.StreamsExamples#pump1(io.vertx.core.Vertx)}
 * ----
 *
 * There's a problem with the above example: If data is read from the socket faster than it can be
 * written back to the socket, it will build up in the write queue of the `NetSocket`, eventually
 * running out of RAM. This might happen, for example if the client at the other end of the socket
 * wasn't reading very fast, effectively putting back-pressure on the connection.
 *
 * Since `NetSocket` implements `WriteStream`, we can check if the `WriteStream` is full before
 * writing to it:
 *
 * [source,$lang]
 * ----
 * {@link examples.StreamsExamples#pump2(io.vertx.core.Vertx)}
 * ----
 *
 * This example won't run out of RAM but we'll end up losing data if the write queue gets full. What we
 * really want to do is pause the `NetSocket` when the write queue is full. Let's do that:
 *
 * [source,$lang]
 * ----
 * {@link examples.StreamsExamples#pump3(io.vertx.core.Vertx)}
 * ----
 *
 * We're almost there, but not quite. The `NetSocket` now gets paused when the file is full, but we also need to unpause
 * it when the write queue has processed its backlog:
 *
 * [source,$lang]
 * ----
 * {@link examples.StreamsExamples#pump4(io.vertx.core.Vertx)}
 * ----
 *
 * And there we have it. The {@link io.vertx.core.streams.WriteStream#drainHandler} event handler will
 * get called when the write queue is ready to accept more data, this resumes the `NetSocket` which
 * allows it to read more data.
 *
 * It's very common to want to do this when writing Vert.x applications, so we provide a helper class
 * called {@link io.vertx.core.streams.Pump} which does all this hard work for you. You just feed it the `ReadStream` and
 * the `WriteStream` and it tell it to start:
 *
 * [source,$lang]
 * ----
 * {@link examples.StreamsExamples#pump5(io.vertx.core.Vertx)}
 * ----
 *
 * Which does exactly the same thing as the more verbose example.
 *
 * Let's look at the methods on `ReadStream` and `WriteStream` in more detail:
 *
 * === ReadStream
 *
 * `ReadStream` is implemented by {@link io.vertx.core.http.HttpClientResponse}, {@link io.vertx.core.datagram.DatagramSocket},
 * {@link io.vertx.core.http.HttpClientRequest}, {@link io.vertx.core.http.HttpServerFileUpload},
 * {@link io.vertx.core.http.HttpServerRequest}, {@link io.vertx.core.http.HttpServerRequestStream},
 * {@link io.vertx.core.eventbus.MessageConsumer}, {@link io.vertx.core.net.NetSocket}, {@link io.vertx.core.net.NetSocketStream},
 * {@link io.vertx.core.http.WebSocket}, {@link io.vertx.core.http.WebSocketStream}, {@link io.vertx.core.TimeoutStream},
 * {@link io.vertx.core.file.AsyncFile}.
 *
 * Functions:
 *
 * - {@link io.vertx.core.streams.ReadStream#handler}:
 * set a handler which will receive items from the ReadStream.
 * - {@link io.vertx.core.streams.ReadStream#pause}:
 * pause the handler. When paused no items will be received in the handler.
 * - {@link io.vertx.core.streams.ReadStream#resume}:
 * resume the handler. The handler will be called if any item arrives.
 * - {@link io.vertx.core.streams.ReadStream#exceptionHandler}:
 * Will be called if an exception occurs on the ReadStream.
 * - {@link io.vertx.core.streams.ReadStream#endHandler}:
 * Will be called when end of stream is reached. This might be when EOF is reached if the ReadStream represents a file,
 * or when end of request is reached if it's an HTTP request, or when the connection is closed if it's a TCP socket.
 *
 * === WriteStream
 *
 * `WriteStream` is implemented by {@link io.vertx.core.http.HttpClientRequest}, {@link io.vertx.core.http.HttpServerResponse}
 * {@link io.vertx.core.http.WebSocket}, {@link io.vertx.core.net.NetSocket}, {@link io.vertx.core.file.AsyncFile},
 * {@link io.vertx.core.datagram.PacketWritestream} and {@link io.vertx.core.eventbus.MessageProducer}
 *
 * Functions:
 *
 * - {@link io.vertx.core.streams.WriteStream#write}:
 * write an object to the WriteStream. This method will never block. Writes are queued internally and asynchronously
 * written to the underlying resource.
 * - {@link io.vertx.core.streams.WriteStream#setWriteQueueMaxSize}:
 * set the number of object at which the write queue is considered _full_, and the method {@link io.vertx.core.streams.WriteStream#writeQueueFull()}
 * returns `true`. Note that, when the write queue is considered full, if write is called the data will still be accepted
 * and queued. The actual number depends on the stream implementation, for {@link io.vertx.core.buffer.Buffer} the size
 * represents the actual number of bytes written and not the number of buffers.
 * - {@link io.vertx.core.streams.WriteStream#writeQueueFull}:
 * returns `true` if the write queue is considered full.
 * - {@link io.vertx.core.streams.WriteStream#exceptionHandler}:
 * Will be called if an exception occurs on the `WriteStream`.
 * - {@link io.vertx.core.streams.WriteStream#drainHandler}:
 * The handler will be called if the `WriteStream` is considered no longer full.
 *
 * === Pump
 *
 * Instances of Pump have the following methods:
 *
 * - {@link io.vertx.core.streams.Pump#start}:
 * Start the pump.
 * - {@link io.vertx.core.streams.Pump#stop}:
 * Stops the pump. When the pump starts it is in stopped mode.
 * - {@link io.vertx.core.streams.Pump#setWriteQueueMaxSize}:
 * This has the same meaning as {@link io.vertx.core.streams.WriteStream#setWriteQueueMaxSize} on the `WriteStream`.
 *
 * A pump can be started and stopped multiple times.
 *
 * When a pump is first created it is _not_ started. You need to call the `start()` method to start it.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Document(fileName = "streams.adoc")
package io.vertx.core.streams;

import io.vertx.docgen.Document;