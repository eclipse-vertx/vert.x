/**
 * == The Event Bus
 * :toc: left
 * <p>
 * The {@link io.vertx.core.eventbus.EventBus event bus} is the *nervous system* of Vert.x.
 * <p>
 * There is a single event bus instance for every Vert.x instance and it is obtained using the method {@link io.vertx.core.Vertx#eventBus}.
 * <p>
 * The event bus allows different parts of your application to communicate with each other irrespective of what language they are written in,
 * and whether they're in the same Vert.x instance, or in a different Vert.x instance.
 * <p>
 * It can even be bridged to allow client side JavaScript running in a browser to communicate on the same event bus.
 * <p>
 * The event bus forms a distributed peer-to-peer messaging system spanning multiple server nodes and multiple browsers.
 * <p>
 * The event bus supports publish/subscribe, point to point, and request-response messaging.
 * <p>
 * The event bus API is very simple. It basically involves registering handlers, unregistering handlers and
 * sending and publishing messages.
 * <p>
 * First some theory:
 * <p>
 * === The Theory
 * <p>
 * ==== Addressing
 * <p>
 * Messages are sent on the event bus to an *address*.
 * <p>
 * Vert.x doesn't bother with any fancy addressing schemes. In Vert.x an address is simply a string.
 * Any string is valid. However it is wise to use some kind of scheme, e.g. using periods to demarcate a namespace.
 * <p>
 * Some examples of valid addresses are +europe.news.feed1+, +acme.games.pacman+, +sausages+, and +X+.
 * <p>
 * ==== Handlers
 * <p>
 * Messages are received in handlers. You register a handler at an address.
 * <p>
 * Many different handlers can be registered at the same address.
 * <p>
 * A single handler can be registered at many different addresses.
 * <p>
 * ==== Publish / subscribe messaging
 * <p>
 * The event bus supports *publishing* messages.
 * <p>
 * Messages are published to an address. Publishing means delivering the message
 * to all handlers that are registered at that address.
 * <p>
 * This is the familiar *publish/subscribe* messaging pattern.
 * <p>
 * ==== Point to point and Request-Response messaging
 * <p>
 * The event bus also supports *point to point* messaging.
 * <p>
 * Messages are sent to an address. Vert.x will then route it to just one of the handlers registered at that address.
 * <p>
 * If there is more than one handler registered at the address,
 * one will be chosen using a non-strict round-robin algorithm.
 * <p>
 * With point to point messaging, an optional reply handler can be specified when sending the message.
 * <p>
 * When a message is received by a recipient, and has been handled, the recipient can optionally decide to reply to
 * the message. If they do so the reply handler will be called.
 * <p>
 * When the reply is received back at the sender, it too can be replied to. This can be repeated ad-infinitum,
 * and allows a dialog to be set-up between two different verticles.
 * <p>
 * This is a common messaging pattern called the *request-response* pattern.
 * <p>
 * ==== Best-effort delivery
 * <p>
 * Vert.x does it's best to deliver messages and won't consciously throw them away. This is called *best-effort* delivery.
 * <p>
 * However, in case of failure of all or parts of the event bus, there is a possibility messages will be lost.
 * <p>
 * If your application cares about lost messages, you should code your handlers to be idempotent, and your senders
 * to retry after recovery.
 * <p>
 * ==== Types of messages
 * <p>
 * Out of the box Vert.x allows any primitive/simple type, String, or {@link io.vertx.core.buffer.Buffer buffers} to
 * be sent as messages.
 * <p>
 * However it's a convention and common practice in Vert.x to send messages as http://json.org/[JSON]
 * <p>
 * JSON is very easy to create, read and parse in all the languages that Vert.x supports so it has become a kind of
 * _lingua franca_ for Vert.x.
 * <p>
 * However you are not forced to use JSON if you don't want to.
 * <p>
 * The event bus is very flexible and also supports sending arbitrary objects over the event bus.
 * You do this by defining a {@link io.vertx.core.eventbus.MessageCodec codec} for the objects you want to send.
 * <p>
 * === The Event Bus API
 * <p>
 * Let's jump into the API
 * <p>
 * ==== Getting the event bus
 * <p>
 * You get a reference to the event bus as follows:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example0_5}
 * ----
 * <p>
 * There is a single instance of the event bus per Vert.x instance.
 * <p>
 * ==== Registering Handlers
 * <p>
 * This simplest way to register a handler is using {@link io.vertx.core.eventbus.EventBus#consumer(String, io.vertx.core.Handler)}.
 * Here's an example:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example1}
 * ----
 * <p>
 * When a message arrives for your handler, your handler will be called, passing in the {@link io.vertx.core.eventbus.Message message}.
 * <p>
 * The object returned from call to +consumer()+ is an instance of {@link io.vertx.core.eventbus.MessageConsumer}
 * <p>
 * This object can subsequently be used to unregister the handler, or use the handler as a stream.
 * <p>
 * Alternatively you can use {@link io.vertx.core.eventbus.EventBus#consumer(String, io.vertx.core.Handler)} to
 * to return a +MessageConsumer+ with no handler set, and then set the handler on that. For example:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example2}
 * ----
 * <p>
 * When registering a handler on a clustered event bus, it can take some time for the registration to reach all
 * nodes of the cluster.
 * <p>
 * If you want to be notified when this has completed, you can register a {@link io.vertx.core.eventbus.MessageConsumer#completionHandler completion handler}
 * on the +MessageConsumer+ object.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example3}
 * ----
 * <p>
 * ==== Un-registering Handlers
 * <p>
 * To unregister a handler, call {@link io.vertx.core.eventbus.MessageConsumer#unregister}.
 * <p>
 * If you are on a clustered event bus, un-registering can take some time to propagate across the nodes, if you want to
 * be notified when this is complete use {@link io.vertx.core.eventbus.MessageConsumer#unregister(io.vertx.core.Handler)}.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example4}
 * ----
 * <p>
 * ==== Publishing messages
 * <p>
 * Publishing a message is simple. Just use {@link io.vertx.core.eventbus.EventBus#publish} specifying the
 * address to publish it to.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example5}
 * ----
 * <p>
 * That message will then be delivered to all handlers registered against the address +news.uk.sport+.
 * <p>
 * ==== Sending messages
 * <p>
 * Sending a message will result in only one handler registered at the address receiving the message.
 * This is the point to point messaging pattern. The handler is chosen in a non-strict round-robin fashion.
 * <p>
 * You can send a message with {@link io.vertx.core.eventbus.EventBus#send}
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example6}
 * ----
 * <p>
 * include::override/eventbus_headers.adoc[]
 * <p>
 * ==== Message ordering
 * <p>
 * Vert.x will deliver messages to any particular handler in the same order they were sent from any particular sender.
 * <p>
 * ==== The Message object
 * <p>
 * The object you receive in a message handler is a {@link io.vertx.core.eventbus.Message}.
 * <p>
 * The {@link io.vertx.core.eventbus.Message#body} of the message corresponds to the object that was sent or published.
 * <p>
 * The headers of the message are available with {@link io.vertx.core.eventbus.Message#headers}.
 * <p>
 * ==== Acknowledging messages / sending replies
 * <p>
 * When using {@link io.vertx.core.eventbus.EventBus#send} the event bus attempts to deliver the message to a
 * {@link io.vertx.core.eventbus.MessageConsumer} registered with the event bus.
 * <p>
 * In some cases it's useful for the sender to know when the consumer has received the message and "processed" it.
 * <p>
 * To acknowledge that the message has been processed the consumer can reply to the message by calling {@link io.vertx.core.eventbus.Message#reply}.
 * <p>
 * When this happens it causes a reply to be sent back to the sender and the reply handler is invoked with the reply.
 * <p>
 * An example will make this clear:
 * <p>
 * The receiver:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example8}
 * ----
 * <p>
 * The sender:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example9}
 * ----
 * <p>
 * The reply can contain a message body which can contain useful information.
 * <p>
 * What the "processing" actually means is application defined and depends entirely on what the message consumer does
 * and is not something that the Vert.x event bus itself knows or cares about.
 * <p>
 * Some examples:
 * <p>
 * * A simple message consumer which implements a service which returns the time of the day would acknowledge with a message
 * containing the time of day in the reply body
 * * A message consumer which implements a persistent queue, might acknowledge with `true` if the message was successfully
 * persisted in storage, or `false` if not.
 * * A message consumer which processes an order might acknowledge with `true` when the order has been successfully processed
 * so it can be deleted from the database
 * <p>
 * ==== Sending with timeouts
 * <p>
 * When sending a message with a reply handler you can specify a timeout in the {@link io.vertx.core.eventbus.DeliveryOptions}.
 * <p>
 * If a reply is not received within that time, the reply handler will be called with a failure.
 * <p>
 * The default timeout is 30 seconds.
 * <p>
 * ==== Send Failures
 * <p>
 * Message sends can fail for other reasons, including:
 * <p>
 * * There are no handlers available to send the message to
 * * The recipient has explicitly failed the message using {@link io.vertx.core.eventbus.Message#fail}
 * <p>
 * In all cases the reply handler will be called with the specific failure.
 * <p>
 * include::override/eventbus.adoc[]
 * <p>
 * ==== Clustered Event Bus
 * <p>
 * The event bus doesn't just exist in a single Vert.x instance. By clustering different Vert.x instances together on
 * your network they can form a single, distributed, event bus.
 * <p>
 * ==== Clustering programmatically
 * <p>
 * If you're creating your Vert.x instance programmatically you get a clustered event bus by configuring the Vert.x
 * instance as clustered;
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example12}
 * ----
 * <p>
 * You should also make sure you have a {@link io.vertx.core.spi.cluster.ClusterManager} implementation on your classpath,
 * for example the default {@code HazelcastClusterManager}.
 * <p>
 * ==== Clustering on the command line
 * <p>
 * You can run Vert.x clustered on the command line with
 * <p>
 * vertx run my-verticle.js -cluster
 * <p>
 * === Automatic clean-up in verticles
 * <p>
 * If you're registering event bus handlers from inside verticles, those handlers will be automatically unregistered
 * when the verticle is undeployed.
 * <p>
 * == Configuring the event bus
 * <p>
 * The event bus can be configured. It is particularly useful when the event bus is clustered. Under the hood
 * the event bus uses TCP connections to send and receive message, so the
 * {@link io.vertx.core.eventbus.EventBusOptions} let you configure all aspects of these TCP connections. As
 * the event bus acts as a server and client, the configuration is close to
 * {@link io.vertx.core.net.NetClientOptions} and {@link io.vertx.core.net.NetServerOptions}.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example13}
 * ----
 * <p>
 * The previous snippet depicts how you can use SSL connections for the event bus, instead of plain TCP
 * connections.
 * <p>
 * **WARNING**: to enforce the security in clustered mode, you **must** configure the
 * cluster manager to use encryption or enforce security. Refer to the documentation of the cluster
 * manager for further details.
 * <p>
 * The event bus configuration needs to be consistent in all the cluster nodes.
 * <p>
 * The {@link io.vertx.core.eventbus.EventBusOptions} also lets you specify whether or not the event bus is
 * clustered, the port and host, as you would do with {@link io.vertx.core.VertxOptions#setClustered(boolean)},
 * {@link io.vertx.core.VertxOptions#getClusterHost()} and {@link io.vertx.core.VertxOptions#getClusterPort()}.
 * <p>
 * When used in containers, you can also configure the public host and port:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example14}
 * ----
 */
@Document(fileName = "eventbus.adoc")
package io.vertx.core.eventbus;

import io.vertx.docgen.Document;

