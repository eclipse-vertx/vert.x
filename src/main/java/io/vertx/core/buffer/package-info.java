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
 * Most data in Vert.x is shuffled around using instances of {@link io.vertx.core.buffer.Buffer}
 *
 * A Buffer represents a sequence of zero or more bytes that can be written to or read from, and which expands
 * automatically as necessary to accomodate any bytes written to it. You can perhaps think of a buffer as
 * smart byte array.
 *
 * === Creating Buffers
 *
 * Create a new empty buffer:
 *
 * [source,java]
 * ----
 * {@link examples.BufferExamples#createEmptyBuffer()}
 * ----
 *
 * Create a buffer from a String. The String will be encoded in the buffer using UTF-8.
 *
 * [source,java]
 * ----
 * {@link examples.BufferExamples#createBufferFromString()}
 * ----
 *
 * Create a buffer from a String: The String will be encoded using the specified encoding, e.g:
 *
 * [source,java]
 * ----
 * {@link examples.BufferExamples#createBufferFromEncodedString()}
 * ----
 *
 * Create a buffer with an initial size hint. If you know your buffer will have a certain amount of data written to
 * it you can create the buffer and specify this size. This makes the buffer initially allocate that much memory
 * and is more efficient than the buffer automatically resizing multiple times as data is written to it.
 *
 * Note that buffers created this way are empty. It does not create a buffer filled with zeros up to the specified size.
 *
 * [source,java]
 * ----
 * {@link examples.BufferExamples#createWithInitialSize()}
 * ----
 *
 * === Writing to a Buffer
 *
 * There are two ways to write to a buffer: appending, and random access. In either case buffers will always expand
 * automatically to encompass the bytes. It's not possible to get an `IndexOutOfBoundsException` with a buffer.
 *
 * ==== Appending to a Buffer
 *
 * To append to a buffer, you use the `appendXXX` methods. Append methods exist for appending other buffers,
 * byte arrays, String and all primitive types.
 *
 * The return value of the `appendXXX methods is the buffer itself, so these can be chained:
 *
 * [source,java]
 * ----
 * {@link examples.BufferExamples#appendingToABuffer()}
 * ----
 *
 * ==== Random access buffer writes
 *
 * You can also write into the buffer at a specific index, by using the `setXXX` methods. Set methods exist for
 * other buffers, byte arrays, String and all primitive types. All the set methods take an index as the first
 * argument - this represents the position in the buffer where to start writing the data.
 *
 * The buffer will always expand as necessary to accomodate the data.
 *
 * [source,java]
 * ----
 * {@link examples.BufferExamples#randomAccessBufferWrite()}
 * ----
 *
 * === Reading from a Buffer
 *
 * Data is read from a buffer using the `getXXX` methods. Get methods exist for byte arrays, String and all primitive types.
 * The first argument to these methods is an index in the buffer from where to get the data.
 *
 * [source,java]
 * ----
 * {@link examples.BufferExamples#readingFromABuffer()}
 * ----
 *
 * === Other buffer methods:
 *
 * * {@link io.vertx.core.buffer.Buffer#length()}. To obtain the length of the buffer. The length of a buffer is the
 *   index of the byte in the buffer with the largest index + 1.
 * * {@link io.vertx.core.buffer.Buffer#copy()}. Copy the entire buffer
 *
 * See the {@link io.vertx.core.buffer.Buffer doc} for more detailed method level documentation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Document
package io.vertx.core.buffer;

import io.vertx.docgen.Document;