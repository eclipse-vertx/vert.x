/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

/**
 * ==== Message Codecs
 *
 * You can send any object you like across the event bus if you define and register a {@link io.vertx.core.eventbus.MessageCodec message codec} for it.
 *
 * Message codecs have a name and you specify that name in the {@link io.vertx.core.eventbus.DeliveryOptions}
 * when sending or publishing the message:
 *
 * [source,java]
 * ----
 * {@link docoverride.eventbus.Examples#example10}
 * ----
 *
 * If you always want the same codec to be used for a particular type then you can register a default codec for it, then
 * you don't have to specify the codec on each send in the delivery options:
 *
 * [source,java]
 * ----
 * {@link docoverride.eventbus.Examples#example11}
 * ----
 *
 * You unregister a message codec with {@link io.vertx.core.eventbus.EventBus#unregisterCodec}.
 *
 * Message codecs don't always have to encode and decode as the same type. For example you can write a codec that
 * allows a MyPOJO class to be sent, but when that message is sent to a handler it arrives as a MyOtherPOJO class.
 */
@Document(fileName = "override/eventbus.adoc")
package docoverride.eventbus;

import io.vertx.docgen.Document;
