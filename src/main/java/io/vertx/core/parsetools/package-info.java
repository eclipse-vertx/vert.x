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
 * {@link examples.RecordParserExamples#example1()}
 * ----
 *
 * You can also produce fixed sized chunks as follows:
 *
 * [source, $lang]
 * ----
 * {@link examples.RecordParserExamples#example2()}
 * ----
 *
 * For more details, check out the {@link io.vertx.core.parsetools.RecordParser} class.
 *
 */
@Document(fileName = "parsetools.adoc")
package io.vertx.core.parsetools;

import io.vertx.docgen.Document;

