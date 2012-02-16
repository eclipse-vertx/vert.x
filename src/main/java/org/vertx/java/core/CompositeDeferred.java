/*
 * Copyright 2011-2012 the original author or authors.
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

package org.vertx.java.core;

/**
 *
 * A Deferred in the body of the run() of which you can get and pass back another future the result of which will
 * be wired into this deferred.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class CompositeDeferred<T> extends DeferredAction<T> {

  @Override
  protected void run() {
    Future<T> def = doRun();
    def.handler(new CompletionHandler<T>() {
      public void handle(Future<T> future) {
        if (future.succeeded()) {
          setResult(future.result());
        } else {
          setException(future.exception());
        }
      }
    });
    if (def instanceof Deferred) {
      ((Deferred)def).execute();
    }
  }

  protected abstract Future<T> doRun();

}
