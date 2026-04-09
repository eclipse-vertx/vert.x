package io.vertx.test.core;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import javax.management.MBeanServer;
import java.io.File;
import java.lang.management.ManagementFactory;

/**
 * Take a heap dump after all tests have been executed, dumps are written to ${project.build.directory}/dumps/dump-XXX.hprof
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HeapDumpRunListener extends RunListener {

  @Override
  public void testRunFinished(Result result) throws Exception {
    File target = TestUtils.MAVEN_TARGET_DIR;
    if (!target.exists()) {
      return;
    }
    File heapDumpDir = new File(target, "dumps");
    if (!heapDumpDir.exists()) {
      if (!heapDumpDir.mkdir()) {
        return;
      }
    }
    int i = 0;
    File heapDumpFile;
    while (true) {
      heapDumpFile = new File(heapDumpDir, "dump-" + i + ".hprof");
      if (!heapDumpFile.exists()) {
        break;
      }
      i++;
    }
    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    try {
      HotSpotDiagnosticMXBean hotSpotDiagnosticMXBean = ManagementFactory.newPlatformMXBeanProxy(
        mBeanServer,
        "com.sun.management:type=HotSpotDiagnostic",
        HotSpotDiagnosticMXBean.class);
      hotSpotDiagnosticMXBean.dumpHeap(heapDumpFile.getAbsolutePath(), Boolean.TRUE);
    } catch (Exception e) {
      //log exception
    }
  }
}
