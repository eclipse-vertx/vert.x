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
 * There is one file system object per Vert.x instance, and you obtain it with {@link io.vertx.core.Vertx#fileSystem()}.
 *
 * A blocking and a non blocking version of each operation is provided.
 *
 * The non blocking versions take a handler which is called when the operation completes or an error occurs.
 *
 * Here's an example of asynchronously copying a file:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#example1}
 * ----
 *
 * The blocking versions are named {@code xxxBlocking} and return the results or throw exceptions directly.
 *
 *
 * In many cases, depending on the operating system and file system,some of the potentially blocking operations
 * can return quickly, which is why we provide them, but it's highly recommended that you test how long they take to
 * return in your particular application before using them from an event loop, so as not to break the Golden Rule.
 *
 * Here's the copy using the blocking API:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#example2}
 * ----
 *
 * Many operations exist to copy, move, truncate, chmod and many other file operations.
 *
 * We won't list them all here, please consult the {@link io.vertx.core.file.FileSystem API docs} for the full list.
 *
 * === Asynchronous files
 *
 * Vert.x provides an asynchronous file abstraction that allows you to manipulate a file on the file system
 *
 * You open an {@link io.vertx.core.file.AsyncFile} as follows:
 *
 * [source,$lang]
 * ----
 * {@link examples.FileSystemExamples#example3}
 * ----
 *
 *
 *
 * TODO
 */
@Document(fileName = "filesystem.adoc")
package io.vertx.core.file;

import io.vertx.docgen.Document;

