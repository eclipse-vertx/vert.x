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
package io.vertx.core.http.impl;

import io.vertx.core.buffer.Buffer;

public class ClientMultipartFormDataPart {

  final String name;
  final String value;
  final String filename;
  final String mediaType;
  final Buffer content;
  final String pathname;
  final Boolean text;

  public ClientMultipartFormDataPart(String name, String value) {
    if (name == null) {
      throw new NullPointerException();
    }
    if (value == null) {
      throw new NullPointerException();
    }
    this.name = name;
    this.value = value;
    this.filename = null;
    this.content = null;
    this.pathname = null;
    this.mediaType = null;
    this.text = null;
  }


  public ClientMultipartFormDataPart(String name, String filename, Buffer content, String mediaType, boolean text) {
    if (name == null) {
      throw new NullPointerException();
    }
    if (filename == null) {
      throw new NullPointerException();
    }
    if (content == null) {
      throw new NullPointerException();
    }
    if (mediaType == null) {
      throw new NullPointerException();
    }
    this.name = name;
    this.value = null;
    this.filename = filename;
    this.content = content;
    this.pathname = null;
    this.mediaType = mediaType;
    this.text = text;
  }

  public ClientMultipartFormDataPart(String name, String filename, String pathname, String mediaType, boolean text) {
    if (name == null) {
      throw new NullPointerException();
    }
    if (filename == null) {
      throw new NullPointerException();
    }
    if (pathname == null) {
      throw new NullPointerException();
    }
    if (mediaType == null) {
      throw new NullPointerException();
    }
    this.name = name;
    this.value = null;
    this.filename = filename;
    this.content = null;
    this.pathname = pathname;
    this.mediaType = mediaType;
    this.text = text;
  }

  /**
   * @return the name
   */
  public String name() {
    return name;
  }

  /**
   * @return {@code true} when this part is an attribute
   */
  public boolean isAttribute() {
    return value != null;
  }

  /**
   * @return {@code true} when this part is a file upload
   */
  public boolean isFileUpload() {
    return value == null;
  }

  /**
   * @return the value when the part for a form attribute otherwise {@code null}
   */
  public String value() {
    return value;
  }

  /**
   * @return the filename when this part is a file upload otherwise {@code null}
   */
  public String filename() {
    return filename;
  }

  /**
   * @return the pathname when this part is a file upload created with a pathname otherwise {@code null}
   */
  public String pathname() {
    return pathname;
  }

  /**
   * @return the content when this part is a file upload created with a buffer otherwise {@code null}
   */
  public Buffer content() {
    return content;
  }

  /**
   * @return the media type when this part is a file upload otherwise {@code null}
   */
  public String mediaType() {
    return mediaType;
  }

  /**
   * @return whether the file upload is text or binary when this part is a file upload otherwise {@code null}
   */
  public Boolean isText() {
    return text;
  }
}
