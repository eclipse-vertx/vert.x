package org.vertx.java.tests.core.filesystem;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestBase;
import vertx.tests.core.filesystem.TestClient;

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
    params = SharedData.instance.getMap("params");
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

  public void testWriteAsync() throws Exception {
    startTest(getMethodName());
  }

  public void testReadAsync() throws Exception {
    startTest(getMethodName());
  }

  public void testWriteStream() throws Exception {
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

  @Test
  public void testExistsNoContext() throws Exception {
    try {
      FileSystem.instance.exists("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testExistsDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.existsDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testChmod1NoContext() throws Exception {
    try {
      FileSystem.instance.chmod("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testChmod2NoContext() throws Exception {
    try {
      FileSystem.instance.chmod("foo", "bar", "quux");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testChmodDeferred1NoContext() throws Exception {
    try {
      FileSystem.instance.chmodDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testChmodDeferred2NoContext() throws Exception {
    try {
      FileSystem.instance.chmodDeferred("foo", "bar", "quux");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCopy1NoContext() throws Exception {
    try {
      FileSystem.instance.copy("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCopy2NoContext() throws Exception {
    try {
      FileSystem.instance.copy("foo", "bar", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCopyDeferred1NoContext() throws Exception {
    try {
      FileSystem.instance.copyDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCopyDeferred2NoContext() throws Exception {
    try {
      FileSystem.instance.copyDeferred("foo", "bar", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCreateFile1NoContext() throws Exception {
    try {
      FileSystem.instance.createFile("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCreateFile2NoContext() throws Exception {
    try {
      FileSystem.instance.createFile("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCreateFileDeferred1NoContext() throws Exception {
    try {
      FileSystem.instance.createFileDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCreateFile2DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.createFileDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testDelete1NoContext() throws Exception {
    try {
      FileSystem.instance.delete("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testDelete2NoContext() throws Exception {
    try {
      FileSystem.instance.delete("foo", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testDelete1DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.deleteDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testDelete2DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.deleteDeferred("foo", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testFSPropsNoContext() throws Exception {
    try {
      FileSystem.instance.fsProps("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testFSPropsDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.fsPropsDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testLinkNoContext() throws Exception {
    try {
      FileSystem.instance.link("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testDeferredLinkNoContext() throws Exception {
    try {
      FileSystem.instance.linkDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testLpropsNoContext() throws Exception {
    try {
      FileSystem.instance.lprops("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testLpropsDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.lpropsDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMkdir1NoContext() throws Exception {
    try {
      FileSystem.instance.mkdir("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMkdir2NoContext() throws Exception {
    try {
      FileSystem.instance.mkdir("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMkdir3NoContext() throws Exception {
    try {
      FileSystem.instance.mkdir("foo", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMkdir4NoContext() throws Exception {
    try {
      FileSystem.instance.mkdir("foo", "bar", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

    @Test
  public void testMkdir1DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.mkdirDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMkdir2DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.mkdirDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMkdir3DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.mkdirDeferred("foo", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMkdir4DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.mkdirDeferred("foo", "bar", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMoveNoContext() throws Exception {
    try {
      FileSystem.instance.move("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testMoveDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.moveDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen1NoContext() throws Exception {
    try {
      FileSystem.instance.open("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen2NoContext() throws Exception {
    try {
      FileSystem.instance.open("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen3NoContext() throws Exception {
    try {
      FileSystem.instance.open("foo", "bar", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen4NoContext() throws Exception {
    try {
      FileSystem.instance.open("foo", "bar", true, true, true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen5NoContext() throws Exception {
    try {
      FileSystem.instance.open("foo", "bar", true, true, true, true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen1DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.openDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen2DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.openDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen3DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.openDeferred("foo", "bar", true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen4DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.openDeferred("foo", "bar", true, true, true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testOpen5DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.openDeferred("foo", "bar", true, true, true, true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testPropsNoContext() throws Exception {
    try {
      FileSystem.instance.props("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testPropsDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.propsDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testReadDirNoContext() throws Exception {
    try {
      FileSystem.instance.readDir("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testReadDirDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.readDirDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testReadDir2NoContext() throws Exception {
    try {
      FileSystem.instance.readDir("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testReadDir2DeferredNoContext() throws Exception {
    try {
      FileSystem.instance.readDirDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testReadFileNoContext() throws Exception {
    try {
      FileSystem.instance.readFile("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testReadFileDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.readFileDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testReadSymLinkNoContext() throws Exception {
    try {
      FileSystem.instance.readSymlink("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testReadSymLinkDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.readSymlinkDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testSymLinkNoContext() throws Exception {
    try {
      FileSystem.instance.symlink("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testSymLinkDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.symlinkDeferred("foo", "bar");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testTruncateNoContext() throws Exception {
    try {
      FileSystem.instance.truncate("foo", 1234);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testTruncateDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.truncateDeferred("foo", 1234);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testUnlinkNoContext() throws Exception {
    try {
      FileSystem.instance.unlink("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testUnlinkDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.unlinkDeferred("foo");
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testWriteFileNoContext() throws Exception {
    try {
      FileSystem.instance.writeFile("foo", Buffer.create("foo"));
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testWriteFileDeferredNoContext() throws Exception {
    try {
      FileSystem.instance.writeFileDeferred("foo", Buffer.create("foo"));
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

}
