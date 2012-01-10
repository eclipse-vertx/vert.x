package vertx.tests.filesystem;

import org.testng.annotations.Test;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.file.FileSystemException;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestClientBase;
import org.vertx.java.newtests.TestUtils;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private static final String TEST_DIR = "test-tmp";

  private Map<String, Object> params;
  private String pathSep;
  private File testDir;

  @Override
  public void start() {
    super.start();
    params = SharedData.getMap("params");
    java.nio.file.FileSystem fs = FileSystems.getDefault();
    pathSep = fs.getSeparator();
    params = SharedData.getMap("params");

    testDir = new File(TEST_DIR);
    if (testDir.exists()) {
      deleteDir(testDir);
    }
    testDir.mkdir();

    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testSimpleCopy() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";
    createFileWithJunk(source, 100);
    testCopy(source, target, false, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testSimpleCopyFileAlreadyExists() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";
    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);
    testCopy(source, target, false, false, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testCopyIntoDir() throws Exception {
    final String source = "foo.txt";
    String dir = "some-dir";
    final String target = dir + pathSep + "bar.txt";
    mkDir(dir);
    createFileWithJunk(source, 100);
    testCopy(source, target, false, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testCopyEmptyDir() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";
    mkDir(source);
    testCopy(source, target, false, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testCopyNonEmptyDir() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";
    final String file1 = pathSep + "somefile.bar";
    mkDir(source);
    createFileWithJunk(source + file1, 100);
    testCopy(source, target, false, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(source));
        tu.azzert(fileExists(target));
        tu.azzert(!fileExists(target + file1));
      }
    });
  }

  public void testFailCopyDirAlreadyExists() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";
    mkDir(source);
    mkDir(target);
    testCopy(source, target, false, false, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testRecursiveCopy() throws Exception {
    final String dir = "some-dir";
    final String file1 = pathSep + "file1.dat";
    final String file2 = pathSep + "index.html";
    final String dir2 = "next-dir";
    final String file3 = pathSep + "blah.java";
    mkDir(dir);
    createFileWithJunk(dir + file1, 100);
    createFileWithJunk(dir + file2, 100);
    mkDir(dir + pathSep + dir2);
    createFileWithJunk(dir + pathSep + dir2 + file3, 100);
    final String target = "some-other-dir";
    testCopy(dir, target, true, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(dir));
        tu.azzert(fileExists(target));
        tu.azzert(fileExists(target + file1));
        tu.azzert(fileExists(target + file2));
        tu.azzert(fileExists(target + pathSep + dir2 + file3));
      }
    });
  }

  private void testCopy(final String source, final String target, final boolean recursive,
                        final boolean shouldPass, final Handler<Void> afterOK) {
    CompletionHandler<Void> compl = createHandler(shouldPass, afterOK);
    if (recursive) {
      FileSystem.instance.copy(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target, true).handler(compl);
    } else {
      FileSystem.instance.copy(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target).handler(compl);
    }
  }

  public void testSimpleMove() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";
    createFileWithJunk(source, 100);
    testMove(source, target, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(!fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testSimpleMoveFileAlreadyExists() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";
    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);
    testMove(source, target, false, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testMoveEmptyDir() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";
    mkDir(source);
    testMove(source, target, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(!fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testMoveEmptyDirTargetExists() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";
    mkDir(source);
    mkDir(target);
    testMove(source, target, false, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileExists(source));
        tu.azzert(fileExists(target));
      }
    });
  }

  public void testMoveNonEmptyDir() throws Exception {
    final String dir = "some-dir";
    final String file1 = pathSep + "file1.dat";
    final String file2 = pathSep + "index.html";
    final String dir2 = "next-dir";
    final String file3 = pathSep + "blah.java";
    mkDir(dir);
    createFileWithJunk(dir + file1, 100);
    createFileWithJunk(dir + file2, 100);
    mkDir(dir + pathSep + dir2);
    createFileWithJunk(dir + pathSep + dir2 + file3, 100);
    final String target = "some-other-dir";
    testMove(dir, target, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(!fileExists(dir));
        tu.azzert(fileExists(target));
        tu.azzert(fileExists(target + file1));
        tu.azzert(fileExists(target + file2));
        tu.azzert(fileExists(target + pathSep + dir2 + file3));
      }
    });
  }

  private void testMove(final String source, final String target, final boolean shouldPass, final Handler<Void> afterOK) throws Exception {
    FileSystem.instance.move(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target).handler(createHandler(shouldPass, afterOK));
  }

  public void testTruncate() throws Exception {
    final String file1 = "some-file.dat";
    long initialLen = 1000;
    final long truncatedLen = 534;
    createFileWithJunk(file1, initialLen);
    tu.azzert(fileLength(file1) == initialLen);
    testTruncate(file1, truncatedLen, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(fileLength(file1) == truncatedLen);
      }
    });
  }

  public void testTruncateFileDoesNotExist() throws Exception {
    String file1 = "some-file.dat";
    long truncatedLen = 534;
    testTruncate(file1, truncatedLen, false, null);
  }

  private void testTruncate(final String file, final long truncatedLen, final boolean shouldPass,
                            final Handler<Void> afterOK) throws Exception {
    FileSystem.instance.truncate(TEST_DIR + pathSep + file, truncatedLen).handler(createHandler(shouldPass, afterOK));
  }

  public void testChmodNonRecursive1() throws Exception {
    testChmodNonRecursive();
  }

  public void testChmodNonRecursive2() throws Exception {
    testChmodNonRecursive();
  }

  public void testChmodNonRecursive3() throws Exception {
    testChmodNonRecursive();
  }

  public void testChmodNonRecursive4() throws Exception {
    testChmodNonRecursive();
  }

  public void testChmodNonRecursive5() throws Exception {
    testChmodNonRecursive();
  }

  public void testChmodNonRecursive6() throws Exception {
    testChmodNonRecursive();
  }

  public void testChmodNonRecursive() throws Exception {
    final String perms = (String)params.get("perms");
    final String file1 = "some-file.dat";
    createFileWithJunk(file1, 100);
    testChmod(file1, perms, null, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(perms.equals(getPerms(file1)));
        deleteFile(file1);
      }
    });
  }

  public void testChmodRecursive1() throws Exception {
    testChmodRecursive();
  }

  public void testChmodRecursive2() throws Exception {
    testChmodRecursive();
  }

  public void testChmodRecursive3() throws Exception {
    testChmodRecursive();
  }

  public void testChmodRecursive4() throws Exception {
    testChmodRecursive();
  }

  public void testChmodRecursive5() throws Exception {
    testChmodRecursive();
  }

  public void testChmodRecursive6() throws Exception {
    testChmodRecursive();
  }

  public void testChmodRecursive() throws Exception {
    final String perms = (String)params.get("perms");
    final String dirPerms = (String)params.get("dirPerms");
    final String dir = "some-dir";
    final String file1 = pathSep + "file1.dat";
    final String file2 = pathSep + "index.html";
    final String dir2 = "next-dir";
    final String file3 = pathSep + "blah.java";
    mkDir(dir);
    createFileWithJunk(dir + file1, 100);
    createFileWithJunk(dir + file2, 100);
    mkDir(dir + pathSep + dir2);
    createFileWithJunk(dir + pathSep + dir2 + file3, 100);
    testChmod(dir, perms, dirPerms, true, new SimpleHandler() {
      public void handle() {
        tu.azzert(dirPerms.equals(getPerms(dir)));
        tu.azzert(perms.equals(getPerms(dir + file1)));
        tu.azzert(perms.equals(getPerms(dir + file2)));
        tu.azzert(dirPerms.equals(getPerms(dir + pathSep + dir2)));
        tu.azzert(perms.equals(getPerms(dir + pathSep + dir2 + file3)));
        deleteDir(dir);
      }
    });
  }

  private void testChmod(final String file, final String perms, final String dirPerms,
                         final boolean shouldPass, final Handler<Void> afterOK) throws Exception {
    if (Files.isDirectory(Paths.get(TEST_DIR + pathSep + file))) {
      tu.azzert("rwxr-xr-x".equals(getPerms(file)));
    } else {
      tu.azzert("rw-r--r--".equals(getPerms(file)));
    }
    CompletionHandler<Void> compl = createHandler(shouldPass, afterOK);
    if (dirPerms != null) {
      FileSystem.instance.chmod(TEST_DIR + pathSep + file, perms, dirPerms).handler(compl);
    } else {
      FileSystem.instance.chmod(TEST_DIR + pathSep + file, perms).handler(compl);
    }
  }

  private CompletionHandler<Void> createHandler(final boolean shouldPass, final Handler<Void> afterOK) {
    return new CompletionHandler<Void>() {
          public void handle(Future<Void> completion) {
            if (!completion.succeeded()) {
              if (shouldPass) {
                tu.exception(completion.exception(), "Truncate failed");
              } else {
                tu.azzert(completion.exception() instanceof FileSystemException);
                if (afterOK != null) {
                  afterOK.handle(null);
                }
                tu.testComplete();
              }
            } else {
              if (shouldPass) {
                if (afterOK != null) {
                  afterOK.handle(null);
                }
                tu.testComplete();
              } else {
                tu.azzert(false, "Truncate should fail");
              }
            }
          }
        };
  }

  // Helper methods

  private boolean fileExists(String fileName) {
    File file = new File(testDir, fileName);
    return file.exists();
  }

  private void createFileWithJunk(String fileName, long length) throws Exception {
    createFile(fileName, TestUtils.generateRandomByteArray((int) length));
  }

  private void createFile(String fileName, byte[] bytes) throws Exception {
    File file = new File(testDir, fileName);
    Path path = Paths.get(file.getCanonicalPath());
    Files.write(path, bytes);
  }

  private void deleteDir(File dir) {
    File[] files = dir.listFiles();
    for (int i = 0; i < files.length; i++) {
      if (files[i].isDirectory()) {
        deleteDir(files[i]);
      } else {
        files[i].delete();
      }
    }
    dir.delete();
  }

  private void deleteDir(String dir) {
    deleteDir(new File(TEST_DIR + pathSep + dir));
  }

  private void mkDir(String dirName) {
    File dir = new File(TEST_DIR + pathSep + dirName);
    dir.mkdir();
  }

  private long fileLength(String fileName) {
    File file = new File(testDir, fileName);
    return file.length();
  }

  private String getPerms(String fileName) {
    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(Paths.get(testDir + pathSep + fileName));
      return PosixFilePermissions.toString(perms);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  private void deleteFile(String fileName) {
    File file = new File(TEST_DIR + pathSep + fileName);
    file.delete();
  }

}
