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
 * == Buffers
 * :toc: left
 * <p>
 * Most data is shuffled around inside Vert.x using buffers.
 * <p>
 * A buffer is a sequence of zero or more bytes that can read from or written to and which expands automatically as
 * necessary to accommodate any bytes written to it. You can perhaps think of a buffer as smart byte array.
 * <p>
 * === Creating buffers
 * <p>
 * Buffers can create by using one of the static {@link io.vertx.core.buffer.Buffer#buffer} methods.
 * <p>
 * Buffers can be initialised from strings or byte arrays, or empty buffers can be created.
 * <p>
 * Here are some examples of creating buffers:
 * <p>
 * Create a new empty buffer:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example1}
 * ----
 * <p>
 * Create a buffer from a String. The String will be encoded in the buffer using UTF-8.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example2}
 * ----
 * <p>
 * Create a buffer from a String: The String will be encoded using the specified encoding, e.g:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example3}
 * ----
 * <p>
 * include::override/buffer_from_bytes.adoc[]
 * <p>
 * Create a buffer with an initial size hint. If you know your buffer will have a certain amount of data written to it
 * you can create the buffer and specify this size. This makes the buffer initially allocate that much memory and is
 * more efficient than the buffer automatically resizing multiple times as data is written to it.
 * <p>
 * Note that buffers created this way *are empty*. It does not create a buffer filled with zeros up to the specified size.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example5}
 * ----
 * <p>
 * === Writing to a Buffer
 * <p>
 * There are two ways to write to a buffer: appending, and random access.
 * In either case buffers will always expand automatically to encompass the bytes. It's not possible to get
 * an {@code IndexOutOfBoundsException} with a buffer.
 * <p>
 * ==== Appending to a Buffer
 * <p>
 * To append to a buffer, you use the {@code appendXXX} methods.
 * Append methods exist for appending various different types.
 * <p>
 * The return value of the {@code appendXXX} methods is the buffer itself, so these can be chained:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example6}
 * ----
 * <p>
 * ==== Random access buffer writes
 * <p>
 * You can also write into the buffer at a specific index, by using the {@code setXXX} methods.
 * Set methods exist for various different data types. All the set methods take an index as the first argument - this
 * represents the position in the buffer where to start writing the data.
 * <p>
 * The buffer will always expand as necessary to accommodate the data.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example7}
 * ----
 * <p>
 * === Reading from a Buffer
 * <p>
 * Data is read from a buffer using the {@code getXXX} methods. Get methods exist for various datatypes.
 * The first argument to these methods is an index in the buffer from where to get the data.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example8}
 * ----
 * <p>
 * === Working with unsigned numbers
 * <p>
 * Unsigned numbers can be read from or appended/set to a buffer with the {@code getUnsignedXXX},
 * {@code appendUnsignedXXX} and {@code setUnsignedXXX} methods. This is useful when implementing a codec for a
 * network protocol optimized to minimize bandwidth consumption.
 * <p>
 * In the following example, value 200 is set at specified position with just one byte:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example9}
 * ----
 * <p>
 * The console shows '200'.
 * <p>
 * === Buffer length
 * <p>
 * Use {@link io.vertx.core.buffer.Buffer#length} to obtain the length of the buffer.
 * The length of a buffer is the index of the byte in the buffer with the largest index + 1.
 * <p>
 * === Copying buffers
 * <p>
 * Use {@link io.vertx.core.buffer.Buffer#copy} to make a copy of the buffer
 * <p>
 * === Slicing buffers
 * <p>
 * A sliced buffer is a new buffer which backs onto the original buffer, i.e. it does not copy the underlying data.
 * Use {@link io.vertx.core.buffer.Buffer#slice} to create a sliced buffers
 * <p>
 * === Buffer re-use
 * <p>
 * After writing a buffer to a socket or other similar place, they cannot be re-used.
 */
@Document(fileName = "buffers.adoc")
package io.vertx.core.buffer;

import io.vertx.docgen.Document;

