/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.composition;

public class Composable {

  private Runnable onComplete;
  private boolean complete;

  public synchronized void onComplete(Runnable onComplete) {
    this.onComplete = onComplete;
    if (complete) {
      onComplete.run();
    }
  }

  public synchronized void complete() {
    if (onComplete != null) {
      onComplete.run();
    }
    complete = true;
  }

  public void execute() {
  }
}
