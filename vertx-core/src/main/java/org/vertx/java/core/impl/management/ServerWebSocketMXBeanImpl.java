/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.impl.management;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author swilliams
 *
 */
public class ServerWebSocketMXBeanImpl implements ServerWebSocketMXBean {

  private String path;
  private AtomicLong readFrames;
  private AtomicLong sentFrames;
  private AtomicLong exceptions;
  private String binaryHandlerID;
  private String textHandlerID;

  /**
   * @param path
   * @param textHandlerID 
   * @param binaryHandlerID 
   * @param sentFrames 
   * @param readFrames 
   * @param exceptions 
   */
  public ServerWebSocketMXBeanImpl(String path, String binaryHandlerID, String textHandlerID, AtomicLong readFrames, AtomicLong sentFrames, AtomicLong exceptions) {
    this.path = path;
    this.binaryHandlerID = binaryHandlerID;
    this.textHandlerID = textHandlerID;
    this.readFrames = readFrames;
    this.sentFrames = sentFrames;
    this.exceptions = exceptions;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public String getBinaryHandlerID() {
    return binaryHandlerID;
  }

  @Override
  public String getTextHandlerID() {
    return textHandlerID;
  }

  @Override
  public long getFramesRead() {
    return readFrames.get();
  }

  @Override
  public long getFramesSent() {
    return sentFrames.get();
  }

  @Override
  public long getExceptionCount() {
    return exceptions.get();
  }

}
