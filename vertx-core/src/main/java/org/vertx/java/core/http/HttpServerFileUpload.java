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
package org.vertx.java.core.http;

import org.vertx.java.core.streams.ReadStream;

import java.nio.charset.Charset;

/**
 * Represents an upload from an HTML form.<p>
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface HttpServerFileUpload extends ReadStream<HttpServerFileUpload> {
  /**
   * Stream the content of this upload to the given filename.
   */
  HttpServerFileUpload streamToFileSystem(String filename);

  /**
   * Returns the filename which was used when upload the file.
   */
  String filename();

  /**
   * Returns the name of the attribute
   */
  String name();

  /**
   * Returns the contentType for the upload
   */
  String contentType();

  /**
   * Returns the contentTransferEncoding for the upload
   */
  String contentTransferEncoding();

  /**
   * Returns the charset for the upload
   */
  Charset charset();

  /**
   * Returns the size of the upload (in bytes)
   */
  long size();

  /**
   * Returns {@code true} if the size of the upload can be retrieved via {@link #size()}.
   */
  boolean isSizeAvailable();
}
