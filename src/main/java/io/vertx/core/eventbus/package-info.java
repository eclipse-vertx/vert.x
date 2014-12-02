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
 * The event bus is the nervous system of Vert.x.
 *
 * It allows verticles to communicate with each other irrespective of what language they are written in, and
 * whether they're in the same Vert.x instance, or in a different Vert.x instance.
 *
 * It even allows client side JavaScript running in a browser to communicate on the same event bus. (More on that later).
 * The event bus forms a distributed peer-to-peer messaging system spanning multiple server nodes and multiple browsers.
 *
 * The event bus API is incredibly simple. It basically involves registering handlers, unregistering handlers and
 * sending and publishing messages.
 *
 * First some theory:
 *
 * === The Theory
 *
 * ==== Addressing
 *
 * Messages are sent on the event bus to an _address_.
 *
 * Vert.x doesn't bother with any fancy addressing schemes. In Vert.x an address is simply a string, any string is valid.
 * However it is wise to use some kind of scheme, e.g. using periods to demarcate a namespace.
 *
 * Some examples of valid addresses are `europe.news.feed1`, `acme.games.pacman`, `sausages`, and `X`.
 *
 * ==== Handlers
 *
 * A handler is a thing that receives messages from the bus. You register a handler at an address.
 *
 * Many different handlers from the same or different verticles can be registered at the same address. A single handler
 * can be registered by the verticle at many different addresses.
 *
 * ==== Publish / subscribe messaging
 *
 * The event bus supports _publishing_ messages. Messages are published to an address. Publishing means delivering
 * the message to all handlers that are registered at that address. This is the familiar _publish/subscribe_
 * messaging pattern.
 *
 * ==== Point to point and Request-Response messaging
 *
 * The event bus supports _point to point_ messaging. Messages are sent to an address. Vert.x will then route it to just
 * one of the handlers registered at that address. If there is more than one handler registered at the address, one
 * will be chosen using a non-strict round-robin algorithm.
 *
 * With point to point messaging, an optional reply handler can be specified when sending the message. When
 * a message is received by a recipient, and has been handled, the recipient can optionally decide to reply to
 * the message. If they do so that reply handler will be called.
 *
 * When the reply is received back at the sender, it too can be replied to. This can be repeated ad-infinitum,
 * and allows a dialog to be set-up between two different verticles. This is a common messaging pattern called
 * the _Request-Response_ pattern.
 *
 * ==== Transient
 *
 * _All messages in the event bus are transient, and in case of failure of all or parts of the event bus, there is a
 * possibility messages will be lost. If your application cares about lost messages, you should code your handlers
 * to be idempotent, and your senders to retry after recovery._
 *
 * If you want to persist your messages you can use a persistent work queue module for that.
 *
 * ==== Types of messages
 *
 * Messages that you send on the event bus can be as simple as a string, a number or a boolean. You can also send
 * Vert.x buffers or JSON messages.
 *
 * It's highly recommended you use JSON messages to communicate between verticles. JSON is easy to create and
 * parse in all the languages that Vert.x supports.
 *
 * === Event Bus API
 *
 * Let's jump into the API.
 *
 * ==== Registering and Unregistering Handlers
 *
 * To set a message handler on the address `test.address`, you do something like the following:
 *
 * [source,java]
 * ----
 * {@link examples.EventBusExamples#theExample()}
 * ----
 *
 * It's as simple as that. The handler will then receive any messages sent to that address.
 *
 * The class {@link io.vertx.core.eventbus.Message} is a generic type and specific Message types include
 * `Message<Boolean>`, `Message<Buffer>`, `Message<byte[]>`, `Message<Byte>`, `Message<Character>`, `Message<Double>`,
 * `Message<Float>`, `Message<Integer>`, `Message<JsonObject>`, `Message<JsonArray>`, `Message<Long>`, `Message<Short>`
 * and `Message<String>`.
 *
 * If you know you'll always be receiving messages of a particular type you can use the specific type in your handler, e.g:
 *
 * [source,java]
 * ----
 * {@link examples.EventBusExamples#theExample2()}
 * ----
 *
 * blah blah blah....
 *
 */
@Document
package io.vertx.core.eventbus;

import io.vertx.docgen.Document;