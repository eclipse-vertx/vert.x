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
 * <p>Represents the result of an asynchronous action. </p>
 * <p>If the action succeeded then {@link #result} will hold the result, otherwise {@link #exception} will contain
 * the exception.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Completion<T> {

  /**
   * The result of the action, or null if the action did not succeed.
   */
  public final T result;

  /**
   * If the action failed, the exception. Otherwise null.
   */
  public final Exception exception;

  /**
   * An instance of Completion that represents a successful completion with no result.
   */
  public static final Completion<Void> VOID_SUCCESSFUL_COMPLETION = new Completion<Void>((Void) null);

  /**
   * Create a successful {@code Completion} with the result.
   */
  public Completion(T result) {
    this.result = result;
    this.exception = null;
  }

  /**
   * Create an unsuccessful {@code Completion} with the exception.
   */
  public Completion(Exception exception) {
    this.result = null;
    this.exception = exception;
  }

  /**
   * Was the action successful?
   */
  public boolean succeeded() {
    return exception == null;
  }

  /**
   * Did the action fail?
   */
  public boolean failed() {
    return exception != null;
  }

}

