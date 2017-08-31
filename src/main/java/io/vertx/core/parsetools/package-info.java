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
 * == Record Parser
 *
 * The record parser allows you to easily parse protocols which are delimited by a sequence of bytes, or fixed
 * size records. It transforms a sequence of input buffer to a sequence of buffer structured as configured (either
 * fixed size or separated records).
 *
 * For example, if you have a simple ASCII text protocol delimited by '\n' and the input is the following:
 *
 * [source]
 * ----
 * buffer1:HELLO\nHOW ARE Y
 * buffer2:OU?\nI AM
 * buffer3: DOING OK
 * buffer4:\n
 * ----
 *
 * The record parser would produce
 *[source]
 * ----
 * buffer1:HELLO
 * buffer2:HOW ARE YOU?
 * buffer3:I AM DOING OK
 * ----
 *
 * Let's see the associated code:
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#recordParserExample1()}
 * ----
 *
 * You can also produce fixed sized chunks as follows:
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#recordParserExample2()}
 * ----
 *
 * For more details, check out the {@link io.vertx.core.parsetools.RecordParser} class.
 *
 * == Json Parser
 *
 * You can easily parse JSON structures but that requires to provide the JSON content at once, but it
 * may not be convenient when you need to parse very large structures.
 *
 * The non-blocking JSON parser is an event driven parser able to deal with very large structures.
 * It transforms a sequence of input buffer to a sequence of JSON parse events.
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#jsonParserExample1()}
 * ----
 *
 * The parser is non-blocking and emitted events are driven by the input buffers.
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#jsonParserExample2}
 * ----
 *
 * Event driven parsing provides more control but comes at the price of dealing with fine grained events, which can be
 * inconvenient sometimes. The JSON parser allows you to handle JSON structures as values when it is desired:
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#jsonParserExample3}
 * ----
 *
 * The value mode can be set and unset during the parsing allowing you to switch between fine grained
 * events or JSON object value events.
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#jsonParserExample4}
 * ----
 *
 * You can do the same with arrays as well
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#jsonParserExample5}
 * ----
 *
 * You can also decode POJOs
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#jsonParserExample6}
 * ----
 *
 * Whenever the parser fails to process a buffer, an exception will be thrown unless you set an exception handler:
 *
 * [source, $lang]
 * ----
 * {@link examples.ParseToolsExamples#jsonParserExample7}
 * ----
 *
 * For more details, check out the {@link io.vertx.core.parsetools.JsonParser} class.
 */
@Document(fileName = "parsetools.adoc")
package io.vertx.core.parsetools;

import io.vertx.docgen.Document;

