/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
}
