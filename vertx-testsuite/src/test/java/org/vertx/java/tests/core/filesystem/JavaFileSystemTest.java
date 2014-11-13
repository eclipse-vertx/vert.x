/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.tests.core.filesystem;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.filesystem.TestClient;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaFileSystemTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(JavaFileSystemTest.class);

  private static String TMP_DIR = System.getProperty("java.io.tmpdir");

  private Map<String, Object> params;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    params = vertx.sharedData().getMap("params");
    startApp(TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testAdjustFileWithSpaces() {
    startTest(getMethodName());
  }

  @Test
  public void testSimpleCopy() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testSimpleCopyFileAlreadyExists() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testCopyIntoDir() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testCopyEmptyDir() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testCopyNonEmptyDir() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testFailCopyDirAlreadyExists() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testRecursiveCopy() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testSimpleMove() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testSimpleMoveFileAlreadyExists() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testMoveEmptyDir() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testMoveEmptyDirTargetExists() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testMoveNonEmptyDir() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testTruncate() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testTruncateExtendsFile() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testTruncateFileDoesNotExist() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testChmodNonRecursive1() throws Exception {
    params.put("perms", "rw-------");
    startTest(getMethodName());
  }

  @Test
  public void testChmodNonRecursive2() throws Exception {
    params.put("perms", "rwx------");
    startTest(getMethodName());
  }

  @Test
  public void testChmodNonRecursive3() throws Exception {
    params.put("perms", "rw-rw-rw-");
    startTest(getMethodName());
  }

  @Test
  public void testChmodNonRecursive4() throws Exception {
    params.put("perms", "rw-r--r--");
    startTest(getMethodName());
  }

  @Test
  public void testChmodNonRecursive5() throws Exception {
    params.put("perms", "rw--w--w-");
    startTest(getMethodName());
  }

  @Test
  public void testChmodNonRecursive6() throws Exception {
    params.put("perms", "rw-rw-rw-");
    startTest(getMethodName());
  }

  @Test
  public void testChmodRecursive1() throws Exception {
    params.put("perms", "rw-------");
    params.put("dirPerms", "rwx------");
    startTest(getMethodName());
  }

  @Test
  public void testChmodRecursive2() throws Exception {
    params.put("perms", "rwx------");
    params.put("dirPerms", "rwx------");
    startTest(getMethodName());
  }

  @Test
  public void testChmodRecursive3() throws Exception {
    params.put("perms", "rw-rw-rw-");
    params.put("dirPerms", "rwxrw-rw-");
    startTest(getMethodName());
  }

  @Test
  public void testChmodRecursive4() throws Exception {
    params.put("perms", "rw-r--r--");
    params.put("dirPerms", "rwxr--r--");
    startTest(getMethodName());
  }

  @Test
  public void testChmodRecursive5() throws Exception {
    params.put("perms", "rw--w--w-");
    params.put("dirPerms", "rwx-w--w-");
    startTest(getMethodName());
  }

  @Test
  public void testChmodRecursive6() throws Exception {
    params.put("perms", "rw-rw-rw-");
    params.put("dirPerms", "rwxrw-rw-");
    startTest(getMethodName());
  }

  public void testProps() throws Exception {
    startTest(getMethodName());
  }

  public void testPropsFileDoesNotExist() throws Exception {
    startTest(getMethodName());
  }

  public void testPropsFollowLink() throws Exception {
    startTest(getMethodName());
  }

  public void testPropsDontFollowLink() throws Exception {
    startTest(getMethodName());
  }

  public void testLink() throws Exception {
    startTest(getMethodName());
  }

  public void testSymLink() throws Exception {
    startTest(getMethodName());
  }

  public void testUnlink() throws Exception {
    startTest(getMethodName());
  }

  public void testReadSymLink() throws Exception {
    startTest(getMethodName());
  }

  public void testSimpleDelete() throws Exception {
    startTest(getMethodName());
  }

  public void testDeleteEmptyDir() throws Exception {
    startTest(getMethodName());
  }

  public void testDeleteNonExistent() throws Exception {
    startTest(getMethodName());
  }

  public void testDeleteNonEmptyFails() throws Exception {
    startTest(getMethodName());
  }

  public void testDeleteRecursive() throws Exception {
    startTest(getMethodName());
  }

  public void testMkdirSimple() throws Exception {
    startTest(getMethodName());
  }

  public void testMkdirWithParentsFails() throws Exception {
    startTest(getMethodName());
  }

  public void testMkdirWithPerms() throws Exception {
    startTest(getMethodName());
  }

  public void testMkdirCreateParents() throws Exception {
    startTest(getMethodName());
  }

  public void testMkdirCreateParentsWithPerms() throws Exception {
    startTest(getMethodName());
  }

  public void testReadDirSimple() throws Exception {
    startTest(getMethodName());
  }

  public void testReadDirWithFilter() throws Exception {
    startTest(getMethodName());
  }

  public void testReadFile() throws Exception {
    startTest(getMethodName());
  }

  public void testWriteFile() throws Exception {
    startTest(getMethodName());
  }

  public void testOpenAndWriteFileSynch() throws Exception{
      startTest(getMethodName());
  }
  
  public void testWriteAsync() throws Exception {
    startTest(getMethodName());
  }

  public void testReadAsync() throws Exception {
    startTest(getMethodName());
  }

  public void testWriteStream() throws Exception {
    startTest(getMethodName());
  }
  public void testWriteStreamWithCompositeBuffer() throws Exception {
    startTest(getMethodName());
  }

  public void testReadStream() throws Exception {
    startTest(getMethodName());
  }

  public void testPumpFileStreams() throws Exception {
    startTest(getMethodName());
  }

  public void testCreateFileWithPerms() throws Exception {
    startTest(getMethodName());
  }

  public void testCreateFileNoPerms() throws Exception {
    startTest(getMethodName());
  }

  public void testCreateFileAlreadyExists() throws Exception {
    startTest(getMethodName());
  }

  public void testExists() throws Exception {
    startTest(getMethodName());
  }

  public void testNotExists() throws Exception {
    startTest(getMethodName());
  }

  public void testFSProps() throws Exception {
    startTest(getMethodName());
  }

  public void testChownToRootFails() throws Exception {
    startTest(getMethodName());
  }

  public void testChownToNotExistingUserFails() throws Exception {
    startTest(getMethodName());
  }

  public void testChownToOwnUser() throws Exception {
    startTest(getMethodName());
  }

  public void testChownToOwnGroup() throws Exception {
    startTest(getMethodName());
  }

  private AsyncResultHandler createHandler() {
    return new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> event) {
      }
    };
  }

  @Test
  public void testExistsNoContext() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final Vertx vertx = VertxFactory.newVertx();
    vertx.fileSystem().exists(TMP_DIR + "/foo", new AsyncResultHandler<Boolean>() {
      public void handle(AsyncResult event) {
        assert(vertx.isEventLoop());
        latch.countDown();
      }
    });
    assert(latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void testOpenNoContext() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final Vertx vertx = VertxFactory.newVertx();
    vertx.fileSystem().open(TMP_DIR + "/foo", new AsyncResultHandler<AsyncFile>() {
      public void handle(AsyncResult event) {
        assert (vertx.isEventLoop());
        latch.countDown();
      }
    });
    assert(latch.await(5, TimeUnit.SECONDS));
  }


}
