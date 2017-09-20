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
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example1}
 * ----
 *
 * === Configuring a TCP server
 *
 * If you don't want the default, a server can be configured by passing in a {@link io.vertx.core.net.NetServerOptions}
 * instance when creating it:
 *
 * [source,$lang]
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
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example3}
 * ----
 *
 * Or to specify the host and port in the call to listen, ignoring what is configured in the options:
 *
 * [source,$lang]
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
 * [source,$lang]
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
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example5_1}
 * ----
 *
 * === Getting notified of incoming connections
 *
 * To be notified when a connection is made you need to set a {@link io.vertx.core.net.NetServer#connectHandler(io.vertx.core.Handler)}:
 *
 * [source,$lang]
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
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example7}
 * ----
 *
 * === Writing data to a socket
 *
 * You write to a socket using one of {@link io.vertx.core.net.NetSocket#write}.
 *
 * [source,$lang]
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
 * [source,$lang]
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
 * === Sending files or resources from the classpath
 *
 * Files and classpath resources can be written to the socket directly using {@link io.vertx.core.net.NetSocket#sendFile}. This can be a very
 * efficient way to send files, as it can be handled by the OS kernel directly where supported by the operating system.
 *
 * Please see the chapter about <<classpath, serving files from the classpath>> for restrictions of the
 * classpath resolution or disabling it.
 *
 * [source,$lang]
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
 * [source,$lang]
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
 * [source,$lang]
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
 * [source,$lang]
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
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example13}
 * ----
 *
 * === Configuring a TCP client
 *
 * If you don't want the default, a client can be configured by passing in a {@link io.vertx.core.net.NetClientOptions}
 * instance when creating it:
 *
 * [source,$lang]
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
 * [source,$lang]
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
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example16}
 * ----
 *
 * By default, multiple connection attempts are disabled.
 *
 * [[logging_network_activity]]
 * === Logging network activity
 *
 * For debugging purposes, network activity can be logged:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#exampleNetworkActivityLoggingOnServer}
 * ----
 *
 * for the client
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#exampleNetworkActivityLoggingOnClient}
 * ----
 *
 * Network activity is logged by Netty with the `DEBUG` level and with the `io.netty.handler.logging.LoggingHandler`
 * name. When using network activity logging there are a few things to keep in mind:
 *
 * - logging is not performed by Vert.x logging but by Netty
 * - this is *not* a production feature
 *
 * You should read the <<netty-logging>> section.
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
 * SSL/TLS is enabled with  {@link io.vertx.core.net.NetServerOptions#setSsl(boolean) ssl}.
 *
 * By default it is disabled.
 *
 * ==== Specifying key/certificate for the server
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
 * The password for the key store should also be provided:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example17}
 * ----
 *
 * Alternatively you can read the key store yourself as a buffer and provide that directly:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example18}
 * ----
 *
 * Key/certificate in PKCS#12 format (http://en.wikipedia.org/wiki/PKCS_12), usually with the `.pfx`  or the `.p12`
 * extension can also be loaded in a similar fashion than JKS key stores:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example19}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example20}
 * ----
 *
 * Another way of providing server private key and certificate separately using `.pem` files.
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example21}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example22}
 * ----
 *
 * PKCS8, PKCS1 and X.509 certificates wrapped in a PEM block formats are supported.
 *
 * WARNING: keep in mind that pem configuration, the private key is not crypted.
 *
 * ==== Specifying trust for the server
 *
 * SSL/TLS servers can use a certificate authority in order to verify the identity of the clients.
 *
 * Certificate authorities can be configured for servers in several ways:
 *
 * Java trust stores can be managed with the http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/keytool.html[keytool]
 * utility which ships with the JDK.
 *
 * The password for the trust store should also be provided:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example23}
 * ----
 *
 * Alternatively you can read the trust store yourself as a buffer and provide that directly:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example24}
 * ----
 *
 * Certificate authority in PKCS#12 format (http://en.wikipedia.org/wiki/PKCS_12), usually with the `.pfx`  or the `.p12`
 * extension can also be loaded in a similar fashion than JKS trust stores:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example25}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example26}
 * ----
 *
 * Another way of providing server certificate authority using a list `.pem` files.
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example27}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example28}
 * ----
 *
 * ==== Enabling SSL/TLS on the client
 *
 * Net Clients can also be easily configured to use SSL. They have the exact same API when using SSL as when using standard sockets.
 *
 * To enable SSL on a NetClient the function setSSL(true) is called.
 *
 * ==== Client trust configuration
 *
 * If the {@link io.vertx.core.net.ClientOptionsBase#setTrustAll trustALl} is set to true on the client, then the client will
 * trust all server certificates. The connection will still be encrypted but this mode is vulnerable to 'man in the middle' attacks. I.e. you can't
 * be sure who you are connecting to. Use this with caution. Default value is false.
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example29}
 * ----
 *
 * If {@link io.vertx.core.net.ClientOptionsBase#setTrustAll trustAll} is not set then a client trust store must be
 * configured and should contain the certificates of the servers that the client trusts.
 *
 * By default, host verification is disabled on the client.
 * To enable host verification, set the algorithm to use on your client (only HTTPS and LDAPS is currently supported):
 *
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example46}
 * ----
 *
 * Likewise server configuration, the client trust can be configured in several ways:
 *
 * The first method is by specifying the location of a Java trust-store which contains the certificate authority.
 *
 * It is just a standard Java key store, the same as the key stores on the server side. The client
 * trust store location is set by using the function {@link io.vertx.core.net.JksOptions#setPath path} on the
 * {@link io.vertx.core.net.JksOptions jks options}. If a server presents a certificate during connection which is not
 * in the client trust store, the connection attempt will not succeed.
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example30}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example31}
 * ----
 *
 * Certificate authority in PKCS#12 format (http://en.wikipedia.org/wiki/PKCS_12), usually with the `.pfx`  or the `.p12`
 * extension can also be loaded in a similar fashion than JKS trust stores:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example32}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example33}
 * ----
 *
 * Another way of providing server certificate authority using a list `.pem` files.
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example34}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example35}
 * ----
 *
 * ==== Specifying key/certificate for the client
 *
 * If the server requires client authentication then the client must present its own certificate to the server when
 * connecting. The client can be configured in several ways:
 *
 * The first method is by specifying the location of a Java key-store which contains the key and certificate.
 * Again it's just a regular Java key store. The client keystore location is set by using the function
 * {@link io.vertx.core.net.JksOptions#setPath(java.lang.String) path} on the
 * {@link io.vertx.core.net.JksOptions jks options}.
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example36}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example37}
 * ----
 *
 * Key/certificate in PKCS#12 format (http://en.wikipedia.org/wiki/PKCS_12), usually with the `.pfx`  or the `.p12`
 * extension can also be loaded in a similar fashion than JKS key stores:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example38}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example39}
 * ----
 *
 * Another way of providing server private key and certificate separately using `.pem` files.
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example40}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example41}
 * ----
 *
 * Keep in mind that pem configuration, the private key is not crypted.
 *
 * ==== Self-signed certificates for testing and development purposes
 *
 * CAUTION: Do not use this in production settings, and note that the generated keys are very insecure.
 *
 * It is very often the case that self-signed certificates are required, be it for unit / integration tests or for
 * running a development version of an application.
 *
 * {@link io.vertx.core.net.SelfSignedCertificate} can be used to provide self-signed PEM certificate helpers and
 * give {@link io.vertx.core.net.KeyCertOptions} and {@link io.vertx.core.net.TrustOptions} configurations:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example48}
 * ----
 *
 * The client can also be configured to trust all certificates:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example49}
 * ----
 *
 * Note that self-signed certificates also work for other TCP protocols like HTTPS:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example50}
 * ----
 *
 * ==== Revoking certificate authorities
 *
 * Trust can be configured to use a certificate revocation list (CRL) for revoked certificates that should no
 * longer be trusted. The {@link io.vertx.core.net.NetClientOptions#addCrlPath(java.lang.String) crlPath} configures
 * the crl list to use:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example42}
 * ----
 *
 * Buffer configuration is also supported:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example43}
 * ----
 *
 * ==== Configuring the Cipher suite
 *
 * By default, the TLS configuration will use the Cipher suite of the JVM running Vert.x. This Cipher suite can be
 * configured with a suite of enabled ciphers:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example44}
 * ----
 *
 * Cipher suite can be specified on the {@link io.vertx.core.net.NetServerOptions} or {@link io.vertx.core.net.NetClientOptions} configuration.
 *
 * ==== Configuring TLS protocol versions
 *
 * By default, the TLS configuration will use the following protocol versions: SSLv2Hello, TLSv1, TLSv1.1 and TLSv1.2. Protocol versions can be
 * configured by explicitly adding enabled protocols:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#example45}
 * ----
 *
 * Protocol versions can be specified on the {@link io.vertx.core.net.NetServerOptions} or {@link io.vertx.core.net.NetClientOptions} configuration.
 *
 * ==== SSL engine
 *
 * The engine implementation can be configured to use https://www.openssl.org[OpenSSL] instead of the JDK implementation.
 * OpenSSL provides better performances and CPU usage than the JDK engine, as well as JDK version independence.
 *
 * The engine options to use is
 *
 * - the {@link io.vertx.core.net.TCPSSLOptions#getSslEngineOptions()} options when it is set
 * - otherwise {@link io.vertx.core.net.JdkSSLEngineOptions}
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#exampleSSLEngine}
 * ----
 *
 * ==== Server Name Indication (SNI)
 *
 * Server Name Indication (SNI) is a TLS extension by which a client specifies an hostname attempting to connect: during
 * the TLS handshake the clients gives a server name and the server can use it to respond with a specific certificate
 * for this server name instead of the default deployed certificate.
 *
 * When SNI is active the server uses
 *
 * * the certificate CN or SAN DNS (Subject Alternative Name with DNS) to do an exact match, e.g `www.example.com`
 * * the certificate CN or SAN DNS certificate to match a wildcard name, e.g `*.example.com`
 * * otherwise the first certificate when the client does not present a server name or the presenter server name cannot be matched
 *
 * You can enable SNI on the server by setting {@link io.vertx.core.net.NetServerOptions#setSni(boolean)} to `true` and
 * configured the server with multiple key/certificate pairs.
 *
 * Java KeyStore files or PKCS12 files can store multiple key/cert pairs out of the box.
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#configureSNIServer}
 * ----
 *
 * {@link io.vertx.core.net.PemKeyCertOptions} can be configured to hold multiple entries:
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#configureSNIServerWithPems}
 * ----
 *
 * The client implicitly sends the connecting host as an SNI server name for Fully Qualified Domain Name (FQDN).
 *
 * You can provide an explicit server name when connecting a socket
 *
 * [source,$lang]
 * ----
 * {@link examples.NetExamples#useSNIInClient}
 * ----
 *
 * It can be used for different purposes:
 *
 * * present a server name different than the server host
 * * present a server name while connecting to an IP
 * * force to present a server name when using shortname
 *
 * ==== Application-Layer Protocol Negotiation (ALPN)
 *
 * Application-Layer Protocol Negotiation (ALPN) is a TLS extension for application layer protocol negotiation. It is used by
 * HTTP/2: during the TLS handshake the client gives the list of application protocols it accepts and the server responds
 * with a protocol it supports.
 *
 * Java 8 does not supports ALPN out of the box, so ALPN should be enabled by other means:
 *
 * - _OpenSSL_ support
 * - _Jetty-ALPN_ support
 *
 * The engine options to use is
 *
 * - the {@link io.vertx.core.net.TCPSSLOptions#getSslEngineOptions()} options when it is set
 * - {@link io.vertx.core.net.JdkSSLEngineOptions} when ALPN is available for JDK
 * - {@link io.vertx.core.net.OpenSSLEngineOptions} when ALPN is available for OpenSSL
 * - otherwise it fails
 *
 * ===== OpenSSL ALPN support
 *
 * OpenSSL provides native ALPN support.
 *
 * OpenSSL requires to configure {@link io.vertx.core.net.TCPSSLOptions#setOpenSslEngineOptions(OpenSSLEngineOptions)}
 * and use http://netty.io/wiki/forked-tomcat-native.html[netty-tcnative] jar on the classpath. Using tcnative may require
 * OpenSSL to be installed on your OS depending on the tcnative implementation.
 *
 * ===== Jetty-ALPN support
 *
 * Jetty-ALPN is a small jar that overrides a few classes of Java 8 distribution to support ALPN.
 *
 * The JVM must be started with the _alpn-boot-${version}.jar_ in its `bootclasspath`:
 *
 * ----
 * -Xbootclasspath/p:/path/to/alpn-boot${version}.jar
 * ----
 *
 * where ${version} depends on the JVM version, e.g. _8.1.7.v20160121_ for _OpenJDK 1.8.0u74_ . The complete
 * list is available on the http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html[Jetty-ALPN page].
 *
 * The main drawback is that the version depends on the JVM.
 *
 * To solve this problem the _https://github.com/jetty-project/jetty-alpn-agent[Jetty ALPN agent]_ can be use instead. The agent is a JVM agent that will chose the correct
 * ALPN version for the JVM running it:
 *
 * ----
 * -javaagent:/path/to/alpn/agent
 * ----
 *
 * === Using a proxy for client connections
 *
 * The {@link io.vertx.core.net.NetClient} supports either a HTTP/1.x _CONNECT_, _SOCKS4a_ or _SOCKS5_ proxy.
 *
 * The proxy can be configured in the {@link io.vertx.core.net.NetClientOptions} by setting a
 * {@link io.vertx.core.net.ProxyOptions} object containing proxy type, hostname, port and optionally username and password.
 *
 * Here's an example:
 *
 * [source,$lang]
 *
 * ----
 * {@link examples.NetExamples#example47}
 * ----
 *
 * The DNS resolution is always done on the proxy server, to achieve the functionality of a SOCKS4 client, it is necessary
 * to resolve the DNS address locally.
 */
@Document(fileName = "net.adoc")
package io.vertx.core.net;

import io.vertx.docgen.Document;

