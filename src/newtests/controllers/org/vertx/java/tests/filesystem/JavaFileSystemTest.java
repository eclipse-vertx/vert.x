package org.vertx.java.tests.filesystem;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestBase;
import vertx.tests.filesystem.TestClient;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaFileSystemTest extends TestBase {

  private static final Logger log = Logger.getLogger(JavaFileSystemTest.class);

  private Map<String, Object> params;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    params = SharedData.getMap("params");
    startApp(AppType.JAVA, TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
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

  public void testStat() throws Exception {
    startTest(getMethodName());
  }

  public void testStatFileDoesNotExist() throws Exception {
    startTest(getMethodName());
  }

  public void testStatFollowLink() throws Exception {
    startTest(getMethodName());
  }

  public void testStatDontFollowLink() throws Exception {
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

}
