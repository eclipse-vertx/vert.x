/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
