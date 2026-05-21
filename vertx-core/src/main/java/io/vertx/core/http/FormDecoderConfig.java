/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
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

/**
 * Form decoder configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class FormDecoderConfig {

  private int maxAttributeSize;
  private int maxFields;
  private int maxBufferedBytes;

  public FormDecoderConfig() {
    this.maxAttributeSize = HttpServerOptions.DEFAULT_MAX_FORM_ATTRIBUTE_SIZE;
    this.maxFields = HttpServerOptions.DEFAULT_MAX_FORM_FIELDS;
    this.maxBufferedBytes = HttpServerOptions.DEFAULT_MAX_FORM_BUFFERED_SIZE;
  }

  public FormDecoderConfig(FormDecoderConfig other) {
    this.maxAttributeSize = other.maxAttributeSize;
    this.maxFields = other.maxFields;
    this.maxBufferedBytes = other.maxBufferedBytes;
  }

  /**
   * @return Returns the maximum size of a form attribute
   */
  public int getMaxAttributeSize() {
    return maxAttributeSize;
  }

  /**
   * Set the maximum size of a form attribute. Set to {@code -1} to allow unlimited length
   *
   * @param maxSize the new maximum size
   * @return a reference to this, so the API can be used fluently
   */
  public FormDecoderConfig setMaxAttributeSize(int maxSize) {
    this.maxAttributeSize = maxSize;
    return this;
  }

  /**
   * @return Returns the maximum number of form fields
   */
  public int getMaxFields() {
    return maxFields;
  }

  /**
   * Set the maximum number of fields of a form. Set to {@code -1} to allow unlimited number of attributes
   *
   * @param maxFields the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public FormDecoderConfig setMaxFields(int maxFields) {
    this.maxFields = maxFields;
    return this;
  }

  /**
   * @return Returns the maximum number of bytes a server can buffer when decoding a form
   */
  public int getMaxBufferedBytes() {
    return maxBufferedBytes;
  }

  /**
   * Set the maximum number of bytes a server can buffer when decoding a form. Set to {@code -1} to allow unlimited length
   *
   * @param maxBufferedBytes the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public FormDecoderConfig setMaxBufferedBytes(int maxBufferedBytes) {
    this.maxBufferedBytes = maxBufferedBytes;
    return this;
  }
}
