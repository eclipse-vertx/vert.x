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
 * == Using the file system with Vert.x
 *
 * The Vert.x {@link io.vertx.core.file.FileSystem} object provides many operations for manipulating the file system.
 *
 * There is one file system object per Vert.x instance, and you obtain it with  {@link io.vertx.core.Vertx#fileSystem()}.
 *
 * A blocking and a non blocking version of each operation is provided. The non blocking versions take a handler
 * which is called when the operation completes or an error occurs.
 *
 * Here's an example of an asynchronous copy of a file:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#example1}
 * ----
 * The blocking versions are named `xxxBlocking` and return the results or throw exceptions directly. In many
 * cases, depending on the operating system and file system, some of the potentially blocking operations can return
 * quickly, which is why we provide them, but it's highly recommended that you test how long they take to return in your
 * particular application before using them from an event loop, so as not to break the Golden Rule.
 *
 * Here's the copy using the blocking API:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#example2}
 * ----
 *
 * Many operations exist to copy, move, truncate, chmod and many other file operations. We won't list them all here,
 * please consult the {@link io.vertx.core.file.FileSystem API docs} for the full list.
 *
 * Let's see a couple of examples using asynchronous methods:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#asyncAPIExamples}
 * ----
 *
 * === Asynchronous files
 *
 * Vert.x provides an asynchronous file abstraction that allows you to manipulate a file on the file system.
 *
 * You open an {@link io.vertx.core.file.AsyncFile AsyncFile} as follows:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#example3}
 * ----
 *
 * `AsyncFile` implements `ReadStream` and `WriteStream` so you can _pump_
 * files to and from other stream objects such as net sockets, http requests and responses, and WebSockets.
 *
 * They also allow you to read and write directly to them.
 *
 * ==== Random access writes
 *
 * To use an `AsyncFile` for random access writing you use the
 * {@link io.vertx.core.file.AsyncFile#write(io.vertx.core.buffer.Buffer, long, io.vertx.core.Handler) write} method.
 *
 * The parameters to the method are:
 *
 * * `buffer`: the buffer to write.
 * * `position`: an integer position in the file where to write the buffer. If the position is greater or equal to the size
 *  of the file, the file will be enlarged to accommodate the offset.
 * * `handler`: the result handler
 *
 * Here is an example of random access writes:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#asyncFileWrite()}
 * ----
 *
 * ==== Random access reads
 *
 * To use an `AsyncFile` for random access reads you use the
 * {@link io.vertx.core.file.AsyncFile#read(io.vertx.core.buffer.Buffer, int, long, int, io.vertx.core.Handler) read}
 * method.
 *
 * The parameters to the method are:
 *
 * * `buffer`: the buffer into which the data will be read.
 * * `offset`: an integer offset into the buffer where the read data will be placed.
 * * `position`: the position in the file where to read data from.
 * * `length`: the number of bytes of data to read
 * * `handler`: the result handler
 *
 * Here's an example of random access reads:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#asyncFileRead()}
 * ----
 *
 * ==== Opening Options
 *
 * When opening an `AsyncFile`, you pass an {@link io.vertx.core.file.OpenOptions OpenOptions} instance.
 * These options describe the behavior of the file access. For instance, you can configure the file permissions with the
 * {@link io.vertx.core.file.OpenOptions#setRead(boolean)}, {@link io.vertx.core.file.OpenOptions#setWrite(boolean)}
 * and {@link io.vertx.core.file.OpenOptions#setPerms(java.lang.String)} methods.
 *
 * You can also configure the behavior if the open file already exists with
 * {@link io.vertx.core.file.OpenOptions#setCreateNew(boolean)} and
 * {@link io.vertx.core.file.OpenOptions#setTruncateExisting(boolean)}.
 *
 * You can also mark the file to be deleted on
 * close or when the JVM is shutdown with {@link io.vertx.core.file.OpenOptions#setDeleteOnClose(boolean)}.
 *
 * ==== Flushing data to underlying storage.
 *
 * In the `OpenOptions`, you can enable/disable the automatic synchronisation of the content on every write using
 * {@link io.vertx.core.file.OpenOptions#setDsync(boolean)}. In that case, you can manually flush any writes from the OS
 * cache by calling the {@link io.vertx.core.file.AsyncFile#flush()} method.
 *
 * This method can also be called with an handler which will be called when the flush is complete.
 *
 * ==== Using AsyncFile as ReadStream and WriteStream
 *
 * `AsyncFile` implements `ReadStream` and `WriteStream`. You can then
 * use them with a _pump_ to pump data to and from other read and write streams. For example, this would
 * copy the content to another `AsyncFile`:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#asyncFilePump()}
 * ----
 *
 * You can also use the _pump_ to write file content into HTTP responses, or more generally in any
 * `WriteStream`.
 *
 * ==== Closing an AsyncFile
 *
 * To close an `AsyncFile` call the {@link io.vertx.core.file.AsyncFile#close()} method. Closing is asynchronous and
 * if you want to be notified when the close has been completed you can specify a handler function as an argument.
 *
 */
@Document(fileName = "filesystem.adoc")
package io.vertx.core.file;

import io.vertx.docgen.Document;

