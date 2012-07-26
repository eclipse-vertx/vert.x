package org.vertx.java.tests.core.redeploy;

import junit.framework.TestCase;
import org.junit.Test;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.Deployment;
import org.vertx.java.deploy.impl.ModuleReloader;
import org.vertx.java.deploy.impl.Redeployer;
import org.vertx.java.framework.TestUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedeployerTest extends TestCase {

  private static final Logger log = LoggerFactory.getLogger(RedeployerTest.class);

  Vertx vertx;
  TestReloader reloader;
  File modRoot;
  Redeployer red;

  protected void setUp() throws Exception {
    super.setUp();

    log.info("Current dir is " + new File(".").getAbsolutePath());

    vertx = Vertx.newVertx();
    reloader = new TestReloader();
    modRoot = new File("reloader-test-mods");
    modRoot.mkdir();
    red = new Redeployer(vertx, modRoot, reloader);
  }

  protected void tearDown() throws Exception {
    vertx.fileSystem().deleteSync(modRoot.getAbsolutePath(), true);
    super.tearDown();
  }

  @Test
  public void testAddFile() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    createFile(modDir, "blah.txt", TestUtils.randomAlphaString(1000));
    waitReload(dep);
  }

  @Test
  public void testModifyFile() throws Exception {
    String modName = "my-mod";
    File modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", "my-mod", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    modifyFile(modDir, "blah.txt");
    waitReload(dep);
  }

  private File createModDir(String modName) {
    File modDir = new File(modRoot, modName);
    modDir.mkdir();
    return modDir;
  }

  private void createFile(File modDir, String fileName, String content) throws Exception {
    File f = new File(modDir, fileName);
    vertx.fileSystem().writeFileSync(f.getAbsolutePath(), new Buffer(content));
  }

  private void modifyFile(File modDir, String fileName) throws Exception {
    File f = new File(modDir, fileName);
    f.setLastModified(System.currentTimeMillis());
  }

  private void createDirectory(File modDir, String dirName) throws Exception {
    File f = new File(modDir, dirName);
    vertx.fileSystem().mkdirSync(f.getAbsolutePath());
  }

  private void waitReload(Deployment dep) throws Exception {
    Set<Deployment> set = new HashSet<>();
    set.add(dep);
    reloader.waitReload(set);
  }

  class TestReloader implements ModuleReloader {

    Set<Deployment> reloaded = new HashSet<>();
    CountDownLatch latch = new CountDownLatch(1);

    @Override
    public synchronized void reloadModules(Set<Deployment> deps) {
      log.info("reload module called with " + deps.size());
      for (Deployment dep: deps) {
        log.info("reload module: " + dep.modName);
      }
      reloaded.addAll(deps);
      latch.countDown();
    }

    synchronized void waitReload(Set<Deployment> deps) throws Exception {
      if (!reloaded.isEmpty()) {
        checkDeps(deps);
      } else {
        log.info("waiting");
        if (!latch.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Time out");
        }
        log.info("waited");
        checkDeps(deps);
      }
    }

    private void checkDeps(Set<Deployment> deps) {
      assertEquals(deps.size(), reloaded.size());
      for (Deployment dep: deps) {
        assertTrue(reloaded.contains(dep));
      }
    }
  }

  private Deployment createDeployment(String name, String modName, String parentName) {
     return new Deployment(name, modName, 1, null, null, null, null, parentName);
  }


}
