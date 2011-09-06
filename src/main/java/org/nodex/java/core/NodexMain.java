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
