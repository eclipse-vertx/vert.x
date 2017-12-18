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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.vertx.core.buffer.Buffer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
final class NettyFileUpload implements FileUpload {

  private final HttpServerFileUploadImpl upload;
  private final String name;
  private String contentType;
  private String filename;
  private String contentTransferEncoding;
  private Charset charset;
  private boolean completed;
  private long maxSize = -1;

  NettyFileUpload(HttpServerFileUploadImpl upload, String name, String filename, String contentType, String contentTransferEncoding, Charset charset) {
    this.upload = upload;
    this.name = name;
    this.filename = filename;
    this.contentType = contentType;
    this.contentTransferEncoding = contentTransferEncoding;
    this.charset = charset;
  }

  @Override
  public void setContent(ByteBuf channelBuffer) throws IOException {
    completed = true;
    upload.receiveData(Buffer.buffer(channelBuffer));
    upload.complete();
  }

  @Override
  public void addContent(ByteBuf channelBuffer, boolean last) throws IOException {
    upload.receiveData(Buffer.buffer(channelBuffer));
    if (last) {
      completed = true;
      upload.complete();
    }
  }

  @Override
  public void setContent(File file) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setContent(InputStream inputStream) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCompleted() {
    return completed;
  }

  @Override
  public long length() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long definedLength() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkSize(long newSize) throws IOException {
    if (maxSize >= 0 && newSize > maxSize) {
      throw new IOException("Size exceed allowed maximum capacity");
    }
  }

  @Override
  public long getMaxSize() {
    return maxSize;
  }

  @Override
  public void setMaxSize(long maxSize) {
    this.maxSize = maxSize;
  }

  @Override
  public byte[] get() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ByteBuf getChunk(int i) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getString() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getString(Charset charset) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCharset(Charset charset) {
    this.charset = charset;
  }

  @Override
  public Charset getCharset() {
    return charset;
  }

  @Override
  public boolean renameTo(File file) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInMemory() {
    return false;
  }

  @Override
  public File getFile() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public HttpDataType getHttpDataType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int compareTo(InterfaceHttpData o) {
    return 0;
  }

  @Override
  public String getFilename() {
    return filename;
  }

  @Override
  public void setFilename(String filename) {
    this.filename = filename;
  }

  @Override
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public void setContentTransferEncoding(String contentTransferEncoding) {
    this.contentTransferEncoding = contentTransferEncoding;
  }

  @Override
  public String getContentTransferEncoding() {
    return contentTransferEncoding;
  }

  @Override
  public ByteBuf getByteBuf() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileUpload copy() {
    throw new UnsupportedOperationException();
  }

  //@Override
  public FileUpload duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileUpload retainedDuplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileUpload replace(ByteBuf content) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileUpload retain() {
    return this;
  }

  @Override
  public FileUpload retain(int increment) {
    return this;
  }

  @Override
  public FileUpload touch(Object hint) {
    return this;
  }

  @Override
  public FileUpload touch() {
    return this;
  }

  @Override
  public ByteBuf content() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int refCnt() {
    return 1;
  }

  @Override
  public boolean release() {
    return false;
  }

  @Override
  public boolean release(int decrement) {
    return false;
  }
}
