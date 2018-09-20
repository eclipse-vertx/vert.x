/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

/**
 * Represents an file upload from an HTML FORM.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@VertxGen
public interface HttpServerFileUpload extends ReadStream<Buffer> {

  @Override
  HttpServerFileUpload exceptionHandler(Handler<Throwable> handler);

  @Override
  HttpServerFileUpload handler(Handler<Buffer> handler);

  @Override
  HttpServerFileUpload endHandler(Handler<Void> endHandler);

  @Override
  HttpServerFileUpload pause();

  @Override
  HttpServerFileUpload resume();

  @Override
  HttpServerFileUpload fetch(long amount);

  /**
   * Stream the content of this upload to the given file on storage.
   *
   * @param filename  the name of the file
   */
  @Fluent
  HttpServerFileUpload streamToFileSystem(String filename);

  /**
   * @return the filename which was used when upload the file.
   */
  String filename();

  /**
   * @return the name of the attribute
   */
  String name();

  /**
   * @return  the content type for the upload
   */
  String contentType();

  /**
   * @return the contentTransferEncoding for the upload
   */
  String contentTransferEncoding();

  /**
   * @return the charset for the upload
   */
  String charset();

  /**
   * The size of the upload may not be available until it is all read.
   * Check {@link #isSizeAvailable} to determine this
   *
   * @return the size of the upload (in bytes)
   */
  long size();

  /**
   * @return true if the size of the upload can be retrieved via {@link #size()}.
   */
  boolean isSizeAvailable();
}
