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
import io.vertx.core.http.ClientMultipartForm;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClientMultipartFormImpl implements ClientMultipartForm, Iterable<ClientMultipartFormDataPart> {

  private Charset charset = StandardCharsets.UTF_8;
  private final List<ClientMultipartFormDataPart> parts = new ArrayList<>();
  private final boolean multipart;
  private boolean mixed;

  public ClientMultipartFormImpl(boolean multipart) {
    this.multipart = multipart;
    this.mixed = true;
  }

  public boolean isMultipart() {
    return multipart;
  }

  @Override
  public Iterator<ClientMultipartFormDataPart> iterator() {
    return parts.iterator();
  }

  @Override
  public ClientMultipartForm attribute(String name, String value) {
    parts.add(new ClientMultipartFormDataPart(name, value));
    return this;
  }

  @Override
  public ClientMultipartFormImpl charset(String charset) {
    return charset(charset != null ? Charset.forName(charset) : null);
  }

  @Override
  public ClientMultipartFormImpl charset(Charset charset) {
    this.charset = charset;
    return this;
  }

  @Override
  public ClientMultipartForm mixed(boolean allow) {
    this.mixed = allow;
    return this;
  }

  @Override
  public boolean mixed() {
    return mixed;
  }

  @Override
  public Charset charset() {
    return charset;
  }

  @Override
  public ClientMultipartForm textFileUpload(String name, String filename, String mediaType, Buffer content) {
    parts.add(new ClientMultipartFormDataPart(name, filename, content, mediaType, true));
    return this;
  }

  @Override
  public ClientMultipartForm binaryFileUpload(String name, String filename, String mediaType, Buffer content) {
    parts.add(new ClientMultipartFormDataPart(name, filename, content, mediaType, false));
    return this;
  }

  @Override
  public ClientMultipartForm textFileUpload(String name, String filename, String mediaType, String pathname) {
    parts.add(new ClientMultipartFormDataPart(name, filename, pathname, mediaType, true));
    return this;
  }

  @Override
  public ClientMultipartForm binaryFileUpload(String name, String filename, String mediaType, String pathname) {
    parts.add(new ClientMultipartFormDataPart(name, filename, pathname, mediaType, false));
    return this;
  }
}
