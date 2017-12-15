/*
 * Copyright 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

/**
 * ==== Setting headers on messages
 *
 * Messages sent over the event bus can also contain headers. This can be specified by providing a
 * {@link io.vertx.core.eventbus.DeliveryOptions} when sending or publishing:
 *
 * [source,$lang]
 * ----
 * {@link docoverride.eventbus.Examples#headers(io.vertx.core.eventbus.EventBus)}
 * ----
 */
@Document(fileName = "override/eventbus_headers.adoc")
package docoverride.eventbus.headers;

import io.vertx.docgen.Document;
