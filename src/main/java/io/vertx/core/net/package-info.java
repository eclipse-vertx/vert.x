/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 * == Writing TCP servers and clients
 *
 * Vert.x allows you to easily write non blocking TCP clients and servers.
 *
 * === Creating a TCP server
 *
 * The simplest way to create a TCP server, using all default options is as follows:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example1}
 * ----
 *
 * === Configuring a TCP server
 *
 * If you don't want the default, a server can be configured by passing in a {@link io.vertx.core.net.NetServerOptions}
 * instance when creating it:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example2}
 * ----
 *
 * === Start the Server Listening
 *
 * To tell the server to listen for incoming requests you use one of the {@link io.vertx.core.net.NetServer#listen}
 * alternatives.
 *
 * To tell the server to listen at the host and port as specified in the options:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example3}
 * ----
 *
 * Or to specify the host and port in the call to listen, ignoring what is configured in the options:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example4}
 * ----
 *
 * The default host is `0.0.0.0` which means 'listen on all available addresses' and the default port is `0`, which is a
 * special value that instructs the server to find a random unused local port and use that.
 *
 * The actual bind is asynchronous so the server might not actually be listening until some time *after* the call to
 * listen has returned.
 *
 * If you want to be notified when the server is actually listening you can provide a handler to the `listen` call.
 * For example:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example5}
 * ----
 *
 * === Listening on a random port
 *
 * If `0` is used as the listening port, the server will find an unused random port to listen on.
 *
 * To find out the real port the server is listening on you can call {@link io.vertx.core.net.NetServer#actualPort()}.
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example5_1}
 * ----
 *
 * === Getting notified of incoming connections
 *
 * To be notified when a connection is made you need to set a {@link io.vertx.core.net.NetServer#connectHandler(io.vertx.core.Handler)}:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example6}
 * ----
 *
 * When a connection is made the handler will be called with an instance of {@link io.vertx.core.net.NetSocket}.
 *
 * This is a socket-like interface to the actual connection, and allows you to read and write data as well as do various
 * other things like close the socket.
 *
 * === Reading data from the socket
 *
 * To read data from the socket you set the {@link io.vertx.core.net.NetSocket#handler(io.vertx.core.Handler)} on the
 * socket.
 *
 * This handler will be called with an instance of {@link io.vertx.core.buffer.Buffer} every time data is received on
 * the socket.
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example7}
 * ----
 *
 * === Writing data to a socket
 *
 * You write to a socket using one of {@link io.vertx.core.net.NetSocket#write}.
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example8}
 * ----
 *
 * Write operations are asynchronous and may not occur until some time after the call to write has returned.
 *
 * === Closed handler
 *
 * If you want to be notified when a socket is closed, you can set a {@link io.vertx.core.net.NetSocket#closeHandler(io.vertx.core.Handler)}
 * on it:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example9_1}
 * ----
 *
 * === Handling exceptions
 *
 * You can set an {@link io.vertx.core.net.NetSocket#exceptionHandler(io.vertx.core.Handler)} to receive any
 * exceptions that happen on the socket.
 *
 * === Event bus write handler
 *
 * Every socket automatically registers a handler on the event bus, and when any buffers are received in this handler,
 * it writes them to itself.
 *
 * This enables you to write data to a socket which is potentially in a completely different verticle or even in a
 * different Vert.x instance by sending the buffer to the address of that handler.
 *
 * The address of the handler is given by {@link io.vertx.core.net.NetSocket#writeHandlerID()}
 *
 * === Local and remote addresses
 *
 * The local address of a {@link io.vertx.core.net.NetSocket} can be retrieved using {@link io.vertx.core.net.NetSocket#localAddress()}.
 *
 * The remote address, (i.e. the address of the other end of the connection) of a {@link io.vertx.core.net.NetSocket}
 * can be retrieved using {@link io.vertx.core.net.NetSocket#remoteAddress()}.
 *
 * === Sending files
 *
 * Files can be written to the socket directly using {@link io.vertx.core.net.NetSocket#sendFile}. This can be a very
 * efficient way to send files, as it can be handled by the OS kernel directly where supported by the operating system.
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example10}
 * ----
 *
 * === Streaming sockets
 *
 * Instances of {@link io.vertx.core.net.NetSocket} are also {@link io.vertx.core.streams.ReadStream} and
 * {@link io.vertx.core.streams.WriteStream} instances so they can be used to pump data to or from other
 * read and write streams.
 *
 * See the chapter on <<streams, streams and pumps>> for more information.
 *
 * === Upgrading connections to SSL/TLS
 *
 * A non SSL/TLS connection can be upgraded to SSL/TLS using {@link io.vertx.core.net.NetSocket#upgradeToSsl(io.vertx.core.Handler)}.
 *
 * The server or client must be configured for SSL/TLS for this to work correctly. Please see the <<ssl, chapter on SSL/TLS>>
 * for more information.
 *
 * === Closing a TCP Server
 *
 * Call {@link io.vertx.core.net.NetServer#close()} to close the server. Closing the server closes any open connections
 * and releases all server resources.
 *
 * The close is actually asynchronous and might not complete until some time after the call has returned.
 * If you want to be notified when the actual close has completed then you can pass in a handler.
 *
 * This handler will then be called when the close has fully completed.
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example9}
 * ----
 *
 * === Automatic clean-up in verticles
 *
 * If you're creating TCP servers and clients from inside verticles, those servers and clients will be automatically closed
 * when the verticle is undeployed.
 *
 * === Scaling - sharing TCP servers
 *
 * The handlers of any TCP server are always executed on the same event loop thread.
 *
 * This means that if you are running on a server with a lot of cores, and you only have this one instance
 * deployed then you will have at most one core utilised on your server.
 *
 * In order to utilise more cores of your server you will need to deploy more instances of the server.
 *
 * You can instantiate more instances programmatically in your code:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example11}
 * ----
 *
 * or, if you are using verticles you can simply deploy more instances of your server verticle by using the `-instances` option
 * on the command line:
 *
 *  vertx run com.mycompany.MyVerticle -instances 10
 *
 * or when programmatically deploying your verticle
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example12}
 * ----
 *
 * Once you do this you will find the echo server works functionally identically to before, but all your cores on your
 * server can be utilised and more work can be handled.
 *
 * At this point you might be asking yourself *'How can you have more than one server listening on the
 same host and port? Surely you will get port conflicts as soon as you try and deploy more than one instance?'*
 *
 * _Vert.x does a little magic here.*_
 *
 * When you deploy another server on the same host and port as an existing server it doesn't actually try and create a
 * new server listening on the same host/port.
 *
 * Instead it internally maintains just a single server, and, as incoming connections arrive it distributes
 * them in a round-robin fashion to any of the connect handlers.
 *
 * Consequently Vert.x TCP servers can scale over available cores while each instance remains single threaded.
 *
 * === Creating a TCP client
 *
 * The simplest way to create a TCP client, using all default options is as follows:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example13}
 * ----
 *
 * === Configuring a TCP client
 *
 * If you don't want the default, a client can be configured by passing in a {@link io.vertx.core.net.NetClientOptions}
 * instance when creating it:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example14}
 * ----
 *
 * === Making connections
 *
 * To make a connection to a server you use {@link io.vertx.core.net.NetClient#connect(int, java.lang.String, io.vertx.core.Handler)},
 * specifying the port and host of the server and a handler that will be called with a result containing the
 * {@link io.vertx.core.net.NetSocket} when connection is successful or with a failure if connection failed.
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example15}
 * ----
 *
 * === Configuring connection attempts
 *
 * A client can be configured to automatically retry connecting to the server in the event that it cannot connect.
 * This is configured with {@link io.vertx.core.net.NetClientOptions#setReconnectInterval(long)} and
 * {@link io.vertx.core.net.NetClientOptions#setReconnectAttempts(int)}.
 *
 * NOTE: Currently Vert.x will not attempt to reconnect if a connection fails, reconnect attempts and interval
 * only apply to creating initial connections.
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example16}
 * ----
 *
 * By default, multiple connection attempts are disabled.
 *
 * [[ssl]]
 * === Configuring servers and clients to work with SSL/TLS
 *
 * TCP clients and servers can be configured to use http://en.wikipedia.org/wiki/Transport_Layer_Security[Transport Layer Security]
 * - earlier versions of TLS were known as SSL.
 *
 * The APIs of the servers and clients are identical whether or not SSL/TLS is used, and it's enabled by configuring
 * the {@link io.vertx.core.net.NetClientOptions} or {@link io.vertx.core.net.NetServerOptions} instances used
 * to create the servers or clients.
 *
 * ==== Enabling SSL/TLS on the server
 *
 * SSL/TLS is enabled with  {@link io.vertx.core.net.NetServerOptions#setSsl(boolean)}.
 *
 * By default it is disabled.
 *
 * === Specifying key/certificate for the server
 *
 * SSL/TLS servers usually provide certificates to clients in order verify their identity to clients.
 *
 * Certificates/keys can be configured for servers in several ways:
 *
 * The first method is by specifying the location of a Java key-store which contains the certificate and private key.
 *
 * Java key stores can be managed with the http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/keytool.html[keytool]
 * utility which ships with the JDK.
 *
 * The password for the keystore should also be provided:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example17}
 * ----
 *
 * Alternatively you can read the key store yourself as a buffer and provide that directly:
 *
 * [source,java]
 * ----
 * {@link examples.NetExamples#example18}
 * ----
 *
 * Another way of providing server private key and certificate is using `.PEM` files
 *
 * TODO all the other ways of configuring SSL/TLS
 *
 *
 *
 *
 */
@Document(fileName = "net.adoc")
package io.vertx.core.net;

import io.vertx.docgen.Document;

