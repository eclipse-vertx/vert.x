/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class AbstractVerticle implements Verticle {

  protected Vertx vertx;

  @Override
  public Vertx getVertx() {
    return vertx;
  }

  @Override
  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    start();
    startFuture.setResult(null);
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    stop();
    stopFuture.setResult(null);
  }

  public void start() throws Exception {
  }

  public void stop() throws Exception {
  }

}
