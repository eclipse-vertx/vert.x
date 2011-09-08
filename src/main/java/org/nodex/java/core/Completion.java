/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core;

/**
 * User: tim
 * Date: 01/09/11
 * Time: 17:31
 */
public class Completion<T> {

  public final T result;
  public final Exception exception;

  public static final Completion<Void> VOID_SUCCESSFUL_COMPLETION = new Completion<Void>((Void) null);

  public Completion(T result) {
    this.result = result;
    this.exception = null;
  }

  public Completion(Exception exception) {
    this.result = null;
    this.exception = exception;
  }

  public boolean succeeded() {
    return exception == null;
  }

  public boolean failed() {
    return exception != null;
  }

}

