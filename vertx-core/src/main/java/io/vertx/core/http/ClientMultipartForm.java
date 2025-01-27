/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.ClientMultipartFormImpl;

import java.nio.charset.Charset;

/**
 * A multipart form, providing file upload capabilities.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public interface ClientMultipartForm extends ClientForm {

  /**
   * @return a blank multipart form
   */
  static ClientMultipartForm multipartForm() {
    return new ClientMultipartFormImpl(true);
  }

  /**
   * {@inheritDoc}
   */
  ClientMultipartForm attribute(String name, String value);

  /**
   * {@inheritDoc}
   */
  ClientMultipartForm charset(String charset);

  /**
   * {@inheritDoc}
   */
  ClientMultipartForm charset(Charset charset);

  /**
   * Allow or disallow multipart mixed encoding when files are sharing the same file name.
   * <br/>
   * The default value is {@code true}.
   * <br/>
   * Set to {@code false} if you want to achieve the behavior for <a href="http://www.w3.org/TR/html5/forms.html#multipart-form-data">HTML5</a>.
   *
   * @param allow {@code true} allows use of multipart mixed encoding
   * @return a reference to this, so the API can be used fluently
   */
  ClientMultipartForm mixed(boolean allow);

  /**
   * @return whether multipart mixed encoding is allowed
   */
  boolean mixed();

  /**
   * Add a text file upload form data part.
   *
   * @param name      name of the parameter
   * @param filename  filename of the file
   * @param mediaType the MIME type of the file
   * @param content   the content of the file
   * @return a reference to this, so the API can be used fluently
   */
  ClientMultipartForm textFileUpload(String name, String filename, String mediaType, Buffer content);

  /**
   * Add a binary file upload form data part.
   *
   * @param name      name of the parameter
   * @param filename  filename of the file
   * @param mediaType the MIME type of the file
   * @param content   the content of the file
   * @return a reference to this, so the API can be used fluently
   */
  ClientMultipartForm binaryFileUpload(String name, String filename, String mediaType, Buffer content);

  /**
   * Add a text file upload form data part.
   *
   * @param name      name of the parameter
   * @param filename  filename of the file
   * @param mediaType the MIME type of the file
   * @param pathname  the pathname of the file
   * @return a reference to this, so the API can be used fluently
   */
  ClientMultipartForm textFileUpload(String name, String filename, String mediaType, String pathname);

  /**
   * Add a binary file upload form data part.
   *
   * @param name      name of the parameter
   * @param filename  filename of the file
   * @param mediaType the MIME type of the file
   * @param pathname  the pathname of the file
   * @return a reference to this, so the API can be used fluently
   */
  ClientMultipartForm binaryFileUpload(String name, String filename, String mediaType, String pathname);

}
