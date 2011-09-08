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
 * Date: 15/08/11
 * Time: 16:18
 */
public abstract class NodexMain {

  //private final CountDownLatch endLatch = new CountDownLatch(1);

  public void run() {
    final long contextID = NodexInternal.instance.createAndAssociateContext();
    NodexInternal.instance.executeOnContext(contextID, new Runnable() {
      public void run() {
        NodexInternal.instance.setContextID(contextID);
        try {
          go();
        } catch (Throwable t) {
          t.printStackTrace(System.err);
        }
        //endLatch.countDown();
      }
    });
//    while (true) {
//      try {
//        endLatch.await();
//        break;
//      } catch (InterruptedException e) {
//        //Ignore
//      }
//    }
  }

  public abstract void go() throws Exception;

}
