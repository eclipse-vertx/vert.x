/**
 * == The Event Bus
 * :toc: left
 *
 * The {@link io.vertx.core.eventbus.EventBus event bus} is the *nervous system* of Vert.x.
 *
 * There is a single event bus instance for every Vert.x instance and it is obtained using the method {@link io.vertx.core.Vertx#eventBus}.
 *
 * The event bus allows different parts of your application to communicate with each other irrespective of what language they are written in,
 * and whether they're in the same Vert.x instance, or in a different Vert.x instance.
 *
 * It can even be bridged to allow client side JavaScript running in a browser to communicate on the same event bus.
 *
 * The event bus forms a distributed peer-to-peer messaging system spanning multiple server nodes and multiple browsers.
 *
 * The event bus supports publish/subscribe, point to point, and request-response messaging.
 *
 * The event bus API is very simple. It basically involves registering handlers, unregistering handlers and
 * sending and publishing messages.
 *
 * First some theory:
 *
 * === The Theory
 *
 * ==== Addressing
 *
 * Messages are sent on the event bus to an *address*.
 *
 * Vert.x doesn't bother with any fancy addressing schemes. In Vert.x an address is simply a string.
 * Any string is valid. However it is wise to use some kind of scheme, e.g. using periods to demarcate a namespace.
 *
 * Some examples of valid addresses are +europe.news.feed1+, +acme.games.pacman+, +sausages+, and +X+.
 *
 * ==== Handlers
 *
 * Messages are received in handlers. You register a handler at an address.
 *
 * Many different handlers can be registered at the same address.
 *
 * A single handler can be registered at many different addresses.
 *
 * ==== Publish / subscribe messaging
 *
 * The event bus supports *publishing* messages.
 *
 * Messages are published to an address. Publishing means delivering the message
 * to all handlers that are registered at that address.
 *
 * This is the familiar *publish/subscribe* messaging pattern.
 *
 * ==== Point to point and Request-Response messaging
 *
 * The event bus also supports *point to point* messaging.
 *
 * Messages are sent to an address. Vert.x will then route it to just one of the handlers registered at that address.
 *
 * If there is more than one handler registered at the address,
 * one will be chosen using a non-strict round-robin algorithm.
 *
 * With point to point messaging, an optional reply handler can be specified when sending the message.
 *
 * When a message is received by a recipient, and has been handled, the recipient can optionally decide to reply to
 * the message. If they do so the reply handler will be called.
 *
 * When the reply is received back at the sender, it too can be replied to. This can be repeated ad-infinitum,
 * and allows a dialog to be set-up between two different verticles.
 *
 * This is a common messaging pattern called the *request-response* pattern.
 *
 * ==== Best-effort delivery
 *
 * Vert.x does it's best to deliver messages and won't consciously throw them away. This is called *best-effort* delivery.
 *
 * However, in case of failure of all or parts of the event bus, there is a possibility messages will be lost.
 *
 * If your application cares about lost messages, you should code your handlers to be idempotent, and your senders
 * to retry after recovery.
 *
 * ==== Types of messages
 *
 * Out of the box Vert.x allows any primitive/simple type, String, or {@link io.vertx.core.buffer.Buffer buffers} to
 * be sent as messages.
 *
 * However it's a convention and common practice in Vert.x to send messages as http://json.org/[JSON]
 *
 * JSON is very easy to create, read and parse in all the languages that Vert.x supports so it has become a kind of
 * _lingua franca_ for Vert.x.
 *
 * However you are not forced to use JSON if you don't want to.
 *
 * The event bus is very flexible and also supports sending arbitrary objects over the event bus.
 * You do this by defining a {@link io.vertx.core.eventbus.MessageCodec codec} for the objects you want to send.
 *
 * === The Event Bus API
 *
 * Let's jump into the API
 *
 * ==== Getting the event bus
 *
 * You get a reference to the event bus as follows:
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example0_5}
 * ----
 *
 * There is a single instance of the event bus per Vert.x instance.
 *
 * ==== Registering Handlers
 *
 * This simplest way to register a handler is using {@link io.vertx.core.eventbus.EventBus#consumer(String, io.vertx.core.Handler)}.
 * Here's an example:
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example1}
 * ----
 *
 * When a message arrives for your handler, your handler will be called, passing in the {@link io.vertx.core.eventbus.Message message}.
 *
 * The object returned from call to +consumer()+ is an instance of {@link io.vertx.core.eventbus.MessageConsumer}
 *
 * This object can subsequently be used to unregister the handler, or use the handler as a stream.
 *
 * Alternatively you can use {@link io.vertx.core.eventbus.EventBus#consumer(String, io.vertx.core.Handler)} to
 * to return a +MessageConsumer+ with no handler set, and then set the handler on that. For example:
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example2}
 * ----
 *
 * When registering a handler on a clustered event bus, it can take some time for the registration to reach all
 * nodes of the cluster.
 *
 * If you want to be notified when this has completed, you can register a {@link io.vertx.core.eventbus.MessageConsumer#completionHandler completion handler}
 * on the +MessageConsumer+ object.
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example3}
 * ----
 *
 * ==== Un-registering Handlers
 *
 * To unregister a handler, call {@link io.vertx.core.eventbus.MessageConsumer#unregister}.
 *
 * If you are on a clustered event bus, un-registering can take some time to propagate across the nodes, if you want to
 * be notified when this is complete use {@link io.vertx.core.eventbus.MessageConsumer#unregister(io.vertx.core.Handler)}.
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example4}
 * ----
 *
 * ==== Publishing messages
 *
 * Publishing a message is simple. Just use {@link io.vertx.core.eventbus.EventBus#publish} specifying the
 * address to publish it to.
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example5}
 * ----
 *
 * That message will then be delivered to all handlers registered against the address +news.uk.sport+.
 *
 * ==== Sending messages
 *
 * Sending a message will result in only one handler registered at the address receiving the message.
 * This is the point to point messaging pattern. The handler is chosen in a non-strict round-robin fashion.
 *
 * You can send a message with {@link io.vertx.core.eventbus.EventBus#send}
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example6}
 * ----
 *
 * ==== Setting headers on messages
 *
 * Messages sent over the event bus can also contain headers. This can be specified by providing a
 * {@link io.vertx.core.eventbus.DeliveryOptions} when sending or publishing:
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example7}
 * ----
 *
 * ==== The Message object
 *
 * The object you receive in a message handler is a {@link io.vertx.core.eventbus.Message}.
 *
 * The {@link io.vertx.core.eventbus.Message#body} of the message corresponds to the object that was sent or published.
 *
 * The headers of the message are available with {@link io.vertx.core.eventbus.Message#headers}.
 *
 * ==== Replying to messages
 *
 * Sometimes after you send a message you want to receive a reply from the recipient.
 * This is known as the *request-response pattern*.
 *
 * To do this you can specify a reply handler when sending the message.
 *
 * When the receiver receives the message they can reply to it by calling {@link io.vertx.core.eventbus.Message#reply}.
 *
 * When this happens it causes a reply to be sent back to the sender and the reply handler is invoked with the reply.
 *
 * An example will make this clear:
 *
 * The receiver:
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example8}
 * ----
 *
 * The sender:
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example9}
 * ----
 *
 * The replies themselves can also be replied to so you can create a dialog between two different parties
 * consisting of multiple rounds.
 *
 * ==== Sending with timeouts
 *
 * When sending a message with a reply handler you can specify a timeout in the {@link io.vertx.core.eventbus.DeliveryOptions}.
 *
 * If a reply is not received within that time, the reply handler will be called with a failure.
 *
 * The default timeout is 30 seconds.
 *
 * ==== Send Failures
 *
 * Message sends can fail for other reasons, including:
 *
 * * There are no handlers available to send the message to
 * * The recipient has explicitly failed the message using {@link io.vertx.core.eventbus.Message#fail}
 *
 * In all cases the reply handler will be called with the specific failure.
 *
 * include::override/eventbus.adoc[]
 *
 * ==== Clustered Event Bus
 *
 * The event bus doesn't just exist in a single Vert.x instance. By clustering different Vert.x instances together on
 * your network they can form a single, distributed, event bus.
 *
 * ==== Clustering programmatically
 *
 * If you're creating your Vert.x instance programmatically you get a clustered event bus by configuring the Vert.x
 * instance as clustered;
 *
 * [source,$lang]
 * ----
 * {@link examples.EventBusExamples#example12}
 * ----
 *
 * You should also make sure you have a {@link io.vertx.core.spi.cluster.ClusterManager} implementation on your classpath,
 * for example the default {@code HazelcastClusterManager}.
 *
 * ==== Clustering on the command line
 *
 * You can run Vert.x clustered on the command line with
 *
 *  vertx run MyVerticle -cluster
 *
 * === Automatic clean-up in verticles
 *
 * If you're registering event bus handlers from inside verticles, those handlers will be automatically unregistered
 * when the verticle is undeployed.
 *
 */
@Document(fileName = "eventbus.adoc")
package io.vertx.core.eventbus;

import io.vertx.docgen.Document;

