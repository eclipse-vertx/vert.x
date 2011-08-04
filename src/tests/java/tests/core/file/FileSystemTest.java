package tests.core.file;

import org.nodex.core.Completion;
import org.nodex.core.Nodex;
import org.nodex.core.file.FileSystem;
import org.nodex.core.file.FileSystemException;
import org.nodex.core.net.NetClient;
import org.nodex.core.net.NetConnectHandler;
import org.nodex.core.net.NetServer;
import org.nodex.core.net.NetSocket;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tests.Utils;
import tests.core.TestBase;

import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: tim
 * Date: 04/08/11
 * Time: 09:04
 */
public class FileSystemTest extends TestBase {

  private static final String TEST_DIR = "test-tmp";

  private File testDir;
  private String pathSep;

  @BeforeMethod
  public void setUp() throws Exception {
    testDir = new File(TEST_DIR);
    if (testDir.exists()) {
      deleteDir(testDir);
    }
    testDir.mkdir();

    java.nio.file.FileSystem fs = FileSystems.getDefault();
    pathSep = fs.getSeparator();
  }

  @AfterMethod
  public void tearDown() {
    deleteDir(testDir);
  }

  @Test
  public void testSimpleCopy() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";

    createFileWithJunk(source, 100);

    azzert(testCopy(source, target, false) == null);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testSimpleCopyFileAlreadyExist() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";

    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);

    azzert(testCopy(source, target, false) instanceof FileSystemException);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testCopyIntoDir() throws Exception {
    final String source = "foo.txt";
    final String dir = "some-dir";
    final String target = dir + pathSep + "bar.txt";

    mkDir(dir);

    createFileWithJunk(source, 100);

    azzert(testCopy(source, target, false) == null);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testCopyEmptyDir() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";

    mkDir(source);

    azzert(testCopy(source, target, false) == null);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testCopyNonEmptyDir() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";
    final String file1 = pathSep + "somefile.bar";

    mkDir(source);
    createFileWithJunk(source + file1, 100);

    azzert(testCopy(source, target, false) == null);
    azzert(fileExists(source));
    azzert(fileExists(target));
    azzert(!fileExists(target + file1)); // Non recursive copy will only copy directory, not contents

    throwAssertions();
  }

  @Test
  public void testFailCopyDirAlreadyExists() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";

    mkDir(source);
    mkDir(target);

    azzert(testCopy(source, target, false) instanceof FileSystemException);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
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

    azzert(testCopy(dir, target, true) == null);

    azzert(fileExists(dir));
    azzert(fileExists(target));
    azzert(fileExists(target + file1));
    azzert(fileExists(target + file2));
    azzert(fileExists(target + pathSep + dir2 + file3));

    throwAssertions();
  }


  private Exception testCopy(final String source, final String target, final boolean recursive) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    run(latch, new Runnable() {
      public void run() {
        Completion compl = new Completion() {
          public void onCompletion() {
            latch.countDown();
          }

          @Override
          public void onException(Exception e) {
            exception.set(e);
            latch.countDown();
          }
        };

        if (recursive) {
          FileSystem.instance.copy(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target, true, compl);
        } else {
          FileSystem.instance.copy(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target, compl);
        }
      }
    });
    return exception.get();
  }

  @Test
  public void testSimpleMove() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";

    createFileWithJunk(source, 100);

    azzert(testMove(source, target) == null);
    azzert(!fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testSimpleMoveFileAlreadyExist() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";

    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);

    azzert(testMove(source, target) instanceof FileSystemException);

    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testMoveEmptyDir() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";

    mkDir(source);

    azzert(testMove(source, target) == null);
    azzert(!fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  public void testMoveEmptyDirTargetExists() throws Exception {
    final String source = "some-dir";
    final String target = "some-other-dir";

    mkDir(source);
    mkDir(target);

    azzert(testMove(source, target) instanceof FileSystemException);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
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

    azzert(testMove(dir, target) == null);

    azzert(!fileExists(dir));
    azzert(fileExists(target));
    azzert(fileExists(target + file1));
    azzert(fileExists(target + file2));
    azzert(fileExists(target + pathSep + dir2 + file3));

    throwAssertions();
  }

  private Exception testMove(final String source, final String target) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    run(latch, new Runnable() {
      public void run() {
        Completion compl = new Completion() {
          public void onCompletion() {
            latch.countDown();
          }

          @Override
          public void onException(Exception e) {
            exception.set(e);
            latch.countDown();
          }
        };

        FileSystem.instance.move(TEST_DIR + pathSep  + source, TEST_DIR + pathSep + target, compl);
      }
    });
    return exception.get();
  }

  @Test
  public void testTruncate() throws Exception {
    final String file1 = "some-file.dat";
    final long initialLen = 1000;
    final long truncatedLen = 534;

    createFileWithJunk(file1, initialLen);
    azzert(fileLength(file1) == initialLen);

    azzert(testTruncate(file1, truncatedLen) == null);
    azzert(fileLength(file1) == truncatedLen);
    throwAssertions();
  }

  @Test
  public void testTruncateFileDoesNotExist() throws Exception {
    final String file1 = "some-file.dat";
    final long initialLen = 1000;
    final long truncatedLen = 534;

    azzert(testTruncate(file1, truncatedLen) instanceof FileSystemException);
    throwAssertions();
  }

  private Exception testTruncate(final String file, final long truncatedLen) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        FileSystem.instance.truncate(TEST_DIR + pathSep + file, truncatedLen, new Completion() {
          public void onCompletion() {
            latch.countDown();
          }
          public void onException(Exception e) {
            exception.set(e);
            latch.countDown();
          }
        });
      }
    });
    return exception.get();
  }

  @Test
  public void testChmod() throws Exception {
    testChmodNonRecursive("rw-------");
    testChmodNonRecursive("rwx------");
    testChmodNonRecursive("rw-rw-rw-");
    testChmodNonRecursive("rw-r--r--");
    testChmodNonRecursive("rw--w--w-");
    testChmodNonRecursive("rw-rw-rw-");

    testChmodRecursive("rw-------", "rwx------");
    testChmodRecursive("rwx------", "rwx------");
    testChmodRecursive("rw-rw-rw-", "rwxrw-rw-");
    testChmodRecursive("rw-r--r--", "rwxr--r--");
    testChmodRecursive("rw--w--w-", "rwx-w--w-");
    testChmodRecursive("rw-rw-rw-", "rwxrw-rw-");
  }
  
  private void testChmodNonRecursive(final String perms) throws Exception {
    final String file1 = "some-file.dat";

    createFileWithJunk(file1, 100);

    azzert(testChmod(file1, perms, null) == null);
    azzert(perms.equals(getPerms(file1)));

    throwAssertions();

    deleteFile(file1);
  }

  private void testChmodRecursive(final String perms, final String dirPerms) throws Exception {
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

    azzert(testChmod(dir, perms, dirPerms) == null);

    azzert(dirPerms.equals(getPerms(dir)));
    azzert(perms.equals(getPerms(dir + file1)));
    azzert(perms.equals(getPerms(dir + file2)));
    azzert(dirPerms.equals(getPerms(dir + pathSep + dir2)));
    azzert(perms.equals(getPerms(dir + pathSep + dir2 + file3)));

    throwAssertions();

    deleteDir(dir);
  }

  private Exception testChmod(final String file, final String perms, final String dirPerms) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    if (Files.isDirectory(Paths.get(TEST_DIR + pathSep + file))) {
      azzert("rwxr-xr-x".equals(getPerms(file)));
    } else {
      azzert("rw-r--r--".equals(getPerms(file)));
    }

    run(latch, new Runnable() {
      public void run() {

        Completion compl = new Completion() {
          public void onCompletion() {
            latch.countDown();
          }

          public void onException(Exception e) {
            exception.set(e);
            latch.countDown();
          }
        };

        if (dirPerms != null) {
           FileSystem.instance.chmod(TEST_DIR + pathSep + file, perms, dirPerms, compl);
        } else {
          FileSystem.instance.chmod(TEST_DIR + pathSep + file, perms, compl);
        }
      }
    });

    return exception.get();
  }


  // All file system operations need to be executed in a context so we use a net server for that
  private void run(CountDownLatch latch, final Runnable runner) throws Exception {
    NetServer server = NetServer.createServer(new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
        try {
          runner.run();
        } catch (Exception e) {
          azzert(false);
        }
      }
    }).listen(8181);

    NetClient.createClient().connect(8181, new NetConnectHandler() {
      public void onConnect(NetSocket sock) {
      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));
    awaitClose(server);
  }

  private void deleteDir(String dir) {
    deleteDir(new File(TEST_DIR + pathSep + dir));
  }

  private void deleteDir(File dir) {
    File[] files = dir.listFiles();
    for(int i = 0; i < files.length; i++) {
      if (files[i].isDirectory()) {
        deleteDir(files[i]);
      } else {
        files[i].delete();
      }
    }
    dir.delete();
  }

  private void deleteFile(String fileName) {
    File file = new File(TEST_DIR + pathSep + fileName);
    file.delete();
  }

  private void mkDir(String dirName) {
    File dir = new File(TEST_DIR + pathSep + dirName);
    dir.mkdir();
  }

  private void createFileWithJunk(String fileName, long length) throws Exception {
    createFile(fileName, Utils.generateRandomByteArray((int)length));
  }

  private void createFile(String fileName, byte[] bytes) throws Exception {
    File file = new File(testDir, fileName);
    Path path = Paths.get(file.getCanonicalPath());
    Files.write(path, bytes);
  }

  private boolean fileExists(String fileName) {
    File file = new File(testDir, fileName);
    return file.exists();
  }

  private long fileLength(String fileName) {
    File file = new File(testDir, fileName);
    return file.length();
  }

  private String getPerms(String fileName) throws Exception {
    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(Paths.get(testDir + pathSep + fileName));
    return PosixFilePermissions.toString(perms);
  }

}