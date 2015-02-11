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
 *
 * Most data is shuffled around inside Vert.x using buffers.
 *
 * A buffer is a sequence of zero or more bytes that can read from or written to and which expands automatically as
 * necessary to accommodate any bytes written to it. You can perhaps think of a buffer as smart byte array.
 *
 * === Creating buffers
 *
 * Buffers can create by using one of the static {@link io.vertx.core.buffer.Buffer#buffer} methods.
 *
 * Buffers can be initialised from strings or byte arrays, or empty buffers can be created.
 *
 * Here are some examples of creating buffers:
 *
 * Create a new empty buffer:
 *
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example1}
 * ----
 *
 * Create a buffer from a String. The String will be encoded in the buffer using UTF-8.
 *
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example2}
 * ----
 *
 * Create a buffer from a String: The String will be encoded using the specified encoding, e.g:
 *
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example3}
 * ----
 *
 * include::override/buffer_from_bytes.adoc[]
 *
 * Create a buffer with an initial size hint. If you know your buffer will have a certain amount of data written to it
 * you can create the buffer and specify this size. This makes the buffer initially allocate that much memory and is
 * more efficient than the buffer automatically resizing multiple times as data is written to it.
 *
 * Note that buffers created this way *are empty*. It does not create a buffer filled with zeros up to the specified size.
 *
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example5}
 * ----
 *
 * === Writing to a Buffer
 *
 * There are two ways to write to a buffer: appending, and random access.
 * In either case buffers will always expand automatically to encompass the bytes. It's not possible to get
 * an {@code IndexOutOfBoundsException} with a buffer.
 *
 * ==== Appending to a Buffer
 *
 * To append to a buffer, you use the {@code appendXXX} methods.
 * Append methods exist for appending various different types.
 *
 * The return value of the {@code appendXXX} methods is the buffer itself, so these can be chained:
 *
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example6}
 * ----
 *
 * ==== Random access buffer writes
 *
 * You can also write into the buffer at a specific index, by using the {@code setXXX} methods.
 * Set methods exist for various different data types. All the set methods take an index as the first argument - this
 * represents the position in the buffer where to start writing the data.
 *
 * The buffer will always expand as necessary to accommodate the data.
 *
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example7}
 * ----
 *
 * === Reading from a Buffer
 *
 * Data is read from a buffer using the {@code getXXX} methods. Get methods exist for various datatypes.
 * The first argument to these methods is an index in the buffer from where to get the data.
 *
 * [source,$lang]
 * ----
 * {@link examples.BufferExamples#example8}
 * ----
 *
 * === Buffer length
 *
 * Use {@link io.vertx.core.buffer.Buffer#length} to obtain the length of the buffer.
 * The length of a buffer is the index of the byte in the buffer with the largest index + 1.
 *
 * === Copying buffers
 *
 * Use {@link io.vertx.core.buffer.Buffer#copy} to make a copy of the buffer
 *
 * === Slicing buffers
 *
 * A sliced buffer is a new buffer which backs onto the original buffer, i.e. it does not copy the underlying data.
 * Use {@link io.vertx.core.buffer.Buffer#slice} to create a sliced buffers
 *
 * === Buffer re-use
 *
 * After writing a buffer to a socket or other similar place, they cannot be re-used.
 *
 */
@Document(fileName = "buffers.adoc")
package io.vertx.core.buffer;

import io.vertx.docgen.Document;

