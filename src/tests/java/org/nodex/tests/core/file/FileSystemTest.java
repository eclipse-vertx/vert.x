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

package org.nodex.tests.core.file;

import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Handler;
import org.nodex.java.core.SimpleHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.file.AsyncFile;
import org.nodex.java.core.file.FileProps;
import org.nodex.java.core.file.FileSystem;
import org.nodex.java.core.file.FileSystemException;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.Deferred;
import org.nodex.java.core.SimpleDeferred;
import org.nodex.java.core.streams.Pump;
import org.nodex.java.core.streams.ReadStream;
import org.nodex.java.core.streams.WriteStream;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystemTest extends TestBase {

  private static final String TEST_DIR = "test-tmp";
  private static final String DEFAULT_DIR_PERMS = "rwxr-xr-x";

  private File testDir;
  private String pathSep;

  @BeforeMethod
  public void before() throws Exception {
    testDir = new File(TEST_DIR);
    if (testDir.exists()) {
      deleteDir(testDir);
    }
    testDir.mkdir();

    java.nio.file.FileSystem fs = FileSystems.getDefault();
    pathSep = fs.getSeparator();
  }

  @AfterMethod
  public void after() {
    deleteDir(testDir);
  }

  @Test
  public void testSimpleCopy() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";

    createFileWithJunk(source, 100);

    azzert(testCopy(source, target, false) == null);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testSimpleCopyFileAlreadyExist() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";

    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);

    azzert(testCopy(source, target, false) instanceof FileSystemException);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testCopyIntoDir() throws Exception {
    String source = "foo.txt";
    String dir = "some-dir";
    String target = dir + pathSep + "bar.txt";

    mkDir(dir);

    createFileWithJunk(source, 100);

    azzert(testCopy(source, target, false) == null);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testCopyEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";

    mkDir(source);

    azzert(testCopy(source, target, false) == null);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testCopyNonEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    String file1 = pathSep + "somefile.bar";

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
    String source = "some-dir";
    String target = "some-other-dir";

    mkDir(source);
    mkDir(target);

    azzert(testCopy(source, target, false) instanceof FileSystemException);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testRecursiveCopy() throws Exception {
    String dir = "some-dir";
    String file1 = pathSep + "file1.dat";
    String file2 = pathSep + "index.html";
    String dir2 = "next-dir";
    String file3 = pathSep + "blah.java";

    mkDir(dir);
    createFileWithJunk(dir + file1, 100);
    createFileWithJunk(dir + file2, 100);
    mkDir(dir + pathSep + dir2);
    createFileWithJunk(dir + pathSep + dir2 + file3, 100);

    String target = "some-other-dir";

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
        CompletionHandler<Void> compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };

        if (recursive) {
          FileSystem.instance.copy(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target, true).handler(compl);
        } else {
          FileSystem.instance.copy(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target).handler(compl);
        }
      }
    });
    return exception.get();
  }

  @Test
  public void testSimpleMove() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";

    createFileWithJunk(source, 100);

    azzert(testMove(source, target) == null);
    azzert(!fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testSimpleMoveFileAlreadyExist() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";

    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);

    azzert(testMove(source, target) instanceof FileSystemException);

    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testMoveEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";

    mkDir(source);

    azzert(testMove(source, target) == null);
    azzert(!fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  public void testMoveEmptyDirTargetExists() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";

    mkDir(source);
    mkDir(target);

    azzert(testMove(source, target) instanceof FileSystemException);
    azzert(fileExists(source));
    azzert(fileExists(target));

    throwAssertions();
  }

  @Test
  public void testMoveNonEmptyDir() throws Exception {
    String dir = "some-dir";
    String file1 = pathSep + "file1.dat";
    String file2 = pathSep + "index.html";
    String dir2 = "next-dir";
    String file3 = pathSep + "blah.java";

    mkDir(dir);
    createFileWithJunk(dir + file1, 100);
    createFileWithJunk(dir + file2, 100);
    mkDir(dir + pathSep + dir2);
    createFileWithJunk(dir + pathSep + dir2 + file3, 100);

    String target = "some-other-dir";

    azzert(testMove(dir, target) == null);

    azzert(!fileExists(dir));
    azzert(fileExists(target));
    azzert(fileExists(target + file1));
    azzert(fileExists(target + file2));
    azzert(fileExists(target + pathSep + dir2 + file3));

    throwAssertions();
  }

  abstract class TestDeferred<T> extends SimpleDeferred<T> {
    public void setResult(T res) {
      super.setResult(res);
    }

    public void setException(Exception e) {
      super.setException(e);
    }
  }

  private Exception testMove(final String source, final String target) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    run(latch, new Runnable() {
      public void run() {
        FileSystem.instance.move(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target).handler(new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        });
      }
    });
    return exception.get();
  }

  @Test
  public void testTruncate() throws Exception {
    String file1 = "some-file.dat";
    long initialLen = 1000;
    long truncatedLen = 534;

    createFileWithJunk(file1, initialLen);
    azzert(fileLength(file1) == initialLen);

    azzert(testTruncate(file1, truncatedLen) == null);
    azzert(fileLength(file1) == truncatedLen);
    throwAssertions();
  }

  @Test
  public void testTruncateFileDoesNotExist() throws Exception {
    String file1 = "some-file.dat";
    long initialLen = 1000;
    long truncatedLen = 534;

    azzert(testTruncate(file1, truncatedLen) instanceof FileSystemException);
    throwAssertions();
  }

  private Exception testTruncate(final String file, final long truncatedLen) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        FileSystem.instance.truncate(TEST_DIR + pathSep + file, truncatedLen).handler(
          new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (completion.failed()) {
              exception.set(completion.exception());
            }
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

    throwAssertions();
  }

  private void testChmodNonRecursive(final String perms) throws Exception {
    String file1 = "some-file.dat";

    createFileWithJunk(file1, 100);

    azzert(testChmod(file1, perms, null) == null);
    azzert(perms.equals(getPerms(file1)));

    throwAssertions();

    deleteFile(file1);
  }

  private void testChmodRecursive(final String perms, final String dirPerms) throws Exception {
    String dir = "some-dir";
    String file1 = pathSep + "file1.dat";
    String file2 = pathSep + "index.html";
    String dir2 = "next-dir";
    String file3 = pathSep + "blah.java";

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

        CompletionHandler<Void> compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };

        if (dirPerms != null) {
          FileSystem.instance.chmod(TEST_DIR + pathSep + file, perms, dirPerms).handler(compl);
        } else {
          FileSystem.instance.chmod(TEST_DIR + pathSep + file, perms).handler(compl);
        }
      }
    });

    return exception.get();
  }

  @Test
  public void testStat() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    long start = 1000 * (System.currentTimeMillis() / 1000);
    createFileWithJunk(fileName, fileSize);
    long end = 1000 * (System.currentTimeMillis() / 1000);

    Object res = testStat(fileName, false);

    azzert(res instanceof Exception == false);
    FileProps st = (FileProps) res;
    azzert(st != null);
    azzert(fileSize == st.size);
    azzert(st.creationTime.getTime() >= start);
    azzert(st.creationTime.getTime() <= end);
    azzert(st.lastAccessTime.getTime() >= start);
    azzert(st.lastAccessTime.getTime() <= end);
    azzert(st.lastModifiedTime.getTime() >= start);
    azzert(st.lastModifiedTime.getTime() <= end);
    azzert(!st.isDirectory);
    azzert(!st.isOther);
    azzert(st.isRegularFile);
    azzert(!st.isSymbolicLink);

    throwAssertions();
  }

  @Test
  public void testStatFileDoesNotExist() throws Exception {
    String fileName = "some-file.txt";
    Object res = testStat(fileName, false);
    azzert(res instanceof FileSystemException);
    throwAssertions();
  }

  @Test
  public void testStatLink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    long start = 1000 * (System.currentTimeMillis() / 1000);
    createFileWithJunk(fileName, fileSize);
    long end = 1000 * (System.currentTimeMillis() / 1000);

    String linkName = "some-link.txt";
    Files.createSymbolicLink(Paths.get(TEST_DIR + pathSep + linkName), Paths.get(fileName));

    Object res = testStat(linkName, false);

    azzert(res instanceof Exception == false);
    FileProps st = (FileProps) res;
    azzert(st != null);
    azzert(fileSize == st.size);
    azzert(st.creationTime.getTime() >= start);
    azzert(st.creationTime.getTime() <= end);
    azzert(st.lastAccessTime.getTime() >= start);
    azzert(st.lastAccessTime.getTime() <= end);
    azzert(st.lastModifiedTime.getTime() >= start);
    azzert(st.lastModifiedTime.getTime() <= end);
    azzert(!st.isDirectory);
    azzert(!st.isOther);
    azzert(st.isRegularFile);
    azzert(!st.isSymbolicLink);

    res = testStat(linkName, true);
    st = (FileProps) res;
    azzert(st != null);
    azzert(st.isSymbolicLink);
    throwAssertions();
  }

  private Object testStat(final String fileName, final boolean link) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    final AtomicReference<FileProps> stats = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<FileProps> compl = new CompletionHandler<FileProps>() {
          public void handle(Deferred<FileProps> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            } else {
              stats.set(completion.result());
            }
            latch.countDown();
          }
        };

        if (link) {
          FileSystem.instance.lprops(TEST_DIR + pathSep + fileName).handler(compl);
        } else {
          FileSystem.instance.props(TEST_DIR + pathSep + fileName).handler(compl);
        }
      }
    });

    if (exception.get() != null) {
      return exception.get();
    } else {
      return stats.get();
    }
  }

  @Test
  public void testLink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);

    String linkName = "some-link.txt";
    azzert(testLink(linkName, fileName, false) == null);
    azzert(fileLength(linkName) == fileSize);
    azzert(!Files.isSymbolicLink(Paths.get(TEST_DIR + pathSep + linkName)));

    String symlinkName = "some-sym-link.txt";
    azzert(testLink(symlinkName, fileName, true) == null);
    azzert(fileLength(symlinkName) == fileSize);
    azzert(Files.isSymbolicLink(Paths.get(TEST_DIR + pathSep + symlinkName)));

    throwAssertions();
  }

  private Exception testLink(final String from, final String to, final boolean symbolic) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<Void> compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };
        if (symbolic) {
          // Symlink is relative
          FileSystem.instance.symlink(TEST_DIR + pathSep + from, to).handler(compl);
        } else {
          FileSystem.instance.link(TEST_DIR + pathSep + from, TEST_DIR + pathSep + to).handler(compl);
        }
      }
    });

    return exception.get();
  }

  @Test
  public void testUnlink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);

    final String linkName = "some-link.txt";
    Files.createLink(Paths.get(TEST_DIR + pathSep + linkName), Paths.get(TEST_DIR + pathSep + fileName));

    azzert(fileSize == fileLength(linkName));

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<Void> compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };
        FileSystem.instance.unlink(TEST_DIR + pathSep + linkName).handler(compl);
      }
    });

    azzert(!fileExists(linkName));
    throwAssertions();
  }

  @Test
  public void testReadSymlink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);

    final String linkName = "some-link.txt";
    Files.createSymbolicLink(Paths.get(TEST_DIR + pathSep + linkName), Paths.get(fileName));

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    final AtomicReference<String> name = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<String> compl = new CompletionHandler<String>() {
          public void handle(Deferred<String> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            } else {
              name.set(completion.result());
            }
            latch.countDown();
          }
        };
        FileSystem.instance.readSymlink(TEST_DIR + pathSep + linkName).handler(compl);

      }
    });

    azzert(exception.get() == null);
    azzert(fileName.equals(name.get()));

    throwAssertions();
  }

  @Test
  public void testSimpleDelete() throws Exception {
    String fileName = "some-file.txt";
    createFileWithJunk(fileName, 100);
    azzert(fileExists(fileName));
    azzert(testDelete(fileName, false) == null);
    azzert(!fileExists(fileName));

    throwAssertions();
  }

  @Test
  public void testDeleteEmptyDir() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    azzert(fileExists(dirName));
    azzert(testDelete(dirName, false) == null);
    azzert(!fileExists(dirName));
    throwAssertions();
  }

  @Test
  public void testDeleteNonExistent() throws Exception {
    String dirName = "some-dir";
    azzert(!fileExists(dirName));
    azzert(testDelete(dirName, false) instanceof FileSystemException);
    throwAssertions();
  }

  @Test
  public void testDeleteNonEmptyFails() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    String file1 = "some-file.txt";
    createFileWithJunk(dirName + pathSep + file1, 100);
    azzert(testDelete(dirName, false) instanceof FileSystemException);
    throwAssertions();
  }

  @Test
  public void testDeleteRecursive() throws Exception {
    String dir = "some-dir";
    String file1 = pathSep + "file1.dat";
    String file2 = pathSep + "index.html";
    String dir2 = "next-dir";
    String file3 = pathSep + "blah.java";

    mkDir(dir);
    createFileWithJunk(dir + file1, 100);
    createFileWithJunk(dir + file2, 100);
    mkDir(dir + pathSep + dir2);
    createFileWithJunk(dir + pathSep + dir2 + file3, 100);

    Exception e = testDelete(dir, true);
    azzert(e == null);
    azzert(!fileExists(dir));
    throwAssertions();
  }

  private Exception testDelete(final String fileName, final boolean recursive) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<Void> compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };
        if (recursive) {
          FileSystem.instance.delete(TEST_DIR + pathSep + fileName, recursive).handler(compl);
        } else {
          FileSystem.instance.delete(TEST_DIR + pathSep + fileName).handler(compl);
        }
      }
    });

    return exception.get();
  }

  @Test
  public void testMkdirSimple() throws Exception {
    String dirName = "some-dir";
    azzert(testMkdir(dirName, null, false) == null);
    azzert(fileExists(dirName));
    azzert(Files.isDirectory(Paths.get(TEST_DIR + pathSep + dirName)));
    azzert(DEFAULT_DIR_PERMS.equals(getPerms(dirName)));
    throwAssertions();
  }

  @Test
  public void testMkdirWithParentsFails() throws Exception {
    String dirName = "top-dir" + pathSep + "some-dir";
    azzert(testMkdir(dirName, null, false) instanceof FileSystemException);
    throwAssertions();
  }

  @Test
  public void testMkdirWithPerms() throws Exception {
    String dirName = "some-dir";
    String perms = "rwx--x--x";
    Exception e = testMkdir(dirName, perms, false);
    azzert(e == null);
    azzert(fileExists(dirName));
    azzert(Files.isDirectory(Paths.get(TEST_DIR + pathSep + dirName)));
    azzert(perms.equals(getPerms(dirName)));
    throwAssertions();
  }

  @Test
  public void testMkdirCreateParents() throws Exception {
    String dirName = "top-dir" + pathSep + "/some-dir";
    Exception e = testMkdir(dirName, null, true);
    azzert(e == null);
    azzert(fileExists(dirName));
    azzert(Files.isDirectory(Paths.get(TEST_DIR + pathSep + dirName)));
    azzert(DEFAULT_DIR_PERMS.equals(getPerms(dirName)));
    throwAssertions();
  }

  @Test
  public void testMkdirCreateParentsWithPerms() throws Exception {
    String dirName = "top-dir" + pathSep + "/some-dir";
    String perms = "rwx--x--x";
    Exception e = testMkdir(dirName, perms, true);
    azzert(e == null);
    azzert(fileExists(dirName));
    azzert(Files.isDirectory(Paths.get(TEST_DIR + pathSep + dirName)));
    azzert(perms.equals(getPerms(dirName)));
    throwAssertions();
  }

  private Exception testMkdir(final String dirName, final String perms, final boolean createParents) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<Void> compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };
        if (createParents) {
          if (perms != null) {
            FileSystem.instance.mkdir(TEST_DIR + pathSep + dirName, perms, createParents).handler(compl);
          } else {
            FileSystem.instance.mkdir(TEST_DIR + pathSep + dirName, createParents).handler(compl);
          }
        } else {
          if (perms != null) {
            FileSystem.instance.mkdir(TEST_DIR + pathSep + dirName, perms).handler(compl);
          } else {
            FileSystem.instance.mkdir(TEST_DIR + pathSep + dirName).handler(compl);
          }
        }
      }
    });

    return exception.get();
  }

  @Test
  public void testReadDirSimple() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    int numFiles = 10;
    for (int i = 0; i < numFiles; i++) {
      createFileWithJunk(dirName + pathSep + "file-" + i + ".dat", 100);
    }
    Object res = testReadDir(dirName, null);
    azzert(res instanceof String[]);
    String[] fileNames = (String[]) res;
    azzert(fileNames.length == numFiles);
    Set<String> fset = new HashSet<String>();
    for (int i = 0; i < numFiles; i++) {
      fset.add(fileNames[i]);
    }
    File dir = new File(TEST_DIR + pathSep + dirName);
    String root = dir.getCanonicalPath();
    for (int i = 0; i < numFiles; i++) {
      azzert(fset.contains(root + pathSep + "file-" + i + ".dat"));
    }
    throwAssertions();
  }

  @Test
  public void testReadDirWithFilter() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    int numFiles = 10;
    for (int i = 0; i < numFiles; i++) {
      createFileWithJunk(dirName + pathSep + "foo-" + i + ".txt", 100);
    }
    for (int i = 0; i < numFiles; i++) {
      createFileWithJunk(dirName + pathSep + "bar-" + i + ".txt", 100);
    }
    Object res = testReadDir(dirName, "foo.+");
    azzert(res instanceof String[]);
    String[] fileNames = (String[]) res;
    azzert(fileNames.length == numFiles);
    Set<String> fset = new HashSet<String>();
    for (int i = 0; i < numFiles; i++) {
      fset.add(fileNames[i]);
    }
    File dir = new File(TEST_DIR + pathSep + dirName);
    String root = dir.getCanonicalPath();
    for (int i = 0; i < numFiles; i++) {
      azzert(fset.contains(root + pathSep + "foo-" + i + ".txt"));
    }

    throwAssertions();
  }

  private Object testReadDir(final String dirName, final String filter) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    final AtomicReference<String[]> res = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<String[]> compl = new CompletionHandler<String[]>() {
          public void handle(Deferred<String[]> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            } else {
              res.set(completion.result());
            }
            latch.countDown();
          }
        };
        if (filter == null) {
          FileSystem.instance.readDir(TEST_DIR + pathSep + dirName).handler(compl);
        } else {
          FileSystem.instance.readDir(TEST_DIR + pathSep + dirName, filter).handler(compl);
        }
      }
    });

    if (exception.get() != null) {
      return exception.get();
    } else {
      return res.get();
    }
  }

  @Test
  public void testReadFile() throws Exception {
    byte[] content = Utils.generateRandomByteArray(1000);
    final String fileName = "some-file.dat";
    createFile(fileName, content);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    final AtomicReference<Buffer> res = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<Buffer> compl = new CompletionHandler<Buffer>() {
          public void handle(Deferred<Buffer> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            } else {
              res.set(completion.result());
            }
            latch.countDown();
          }
        };
        FileSystem.instance.readFile(TEST_DIR + pathSep + fileName).handler(compl);
      }
    });

    azzert(exception.get() == null);
    azzert(Utils.buffersEqual(Buffer.create(content), res.get()));
    throwAssertions();
  }

  @Test
  public void testReadFileAsString() throws Exception {
    final String content = Utils.randomAlphaString(1000);
    final String fileName = "some-file.dat";
    createFile(fileName, content.getBytes("UTF-8"));

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    final AtomicReference<String> res = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<String> compl = new CompletionHandler<String>() {
          public void handle(Deferred<String> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            } else {
              res.set(completion.result());
            }
            latch.countDown();
          }
        };
        FileSystem.instance.readFileAsString(TEST_DIR + pathSep + fileName, "UTF-8").handler(compl);
      }
    });

    azzert(exception.get() == null);
    azzert(content.equals(res.get()));
    throwAssertions();
  }

  @Test
  public void testWriteFile() throws Exception {
    byte[] content = Utils.generateRandomByteArray(1000);
    final Buffer buff = Buffer.create(content);
    final String fileName = "some-file.dat";

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    //final AtomicReference<Buffer> res = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<Void> compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };
        FileSystem.instance.writeFile(TEST_DIR + pathSep + fileName, buff).handler(compl);
      }
    });

    azzert(exception.get() == null);
    azzert(fileExists(fileName));
    azzert(fileLength(fileName) == content.length);
    byte[] readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName));
    azzert(Utils.buffersEqual(buff, Buffer.create(readBytes)));
    throwAssertions();
  }

  @Test
  public void testWriteStringToFile() throws Exception {
    final String content = Utils.randomAlphaString(10);
    final String fileName = "some-file.dat";

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<Void> compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (!completion.succeeded()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };
        FileSystem.instance.writeStringToFile(TEST_DIR + pathSep + fileName, content, "UTF-8").handler(compl);
      }
    });

    azzert(exception.get() == null);
    azzert(fileExists(fileName));
    byte[] readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName));
    String readStr = new String(readBytes, Charset.forName("UTF-8"));
    azzert(content.equals(readStr));
    throwAssertions();
  }

  @Test
  public void testWriteAsync() throws Exception {
    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;

    byte[] content = Utils.generateRandomByteArray(chunkSize * chunks);
    final Buffer buff = Buffer.create(content);

    final CountDownLatch latch = new CountDownLatch(chunks);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      void countDownAll() {
        for (int i = 0; i < chunks; i++) {
          latch.countDown();
        }
      }

      public void run() {
        FileSystem.instance.open(TEST_DIR + pathSep + fileName).handler(new CompletionHandler<AsyncFile>() {

          public void handle(Deferred<AsyncFile> completion) {
            if (completion.succeeded()) {
              for (int i = 0; i < chunks; i++) {

                Buffer chunk = buff.copy(i * chunkSize, (i + 1) * chunkSize);
                azzert(chunk.length() == chunkSize);

                completion.result().write(chunk, i * chunkSize).handler(new CompletionHandler<Void>() {

                  public void handle(Deferred<Void> completion) {
                    if (completion.succeeded()) {
                      latch.countDown();
                    } else {
                      completion.exception().printStackTrace();
                      exception.set(completion.exception());
                      countDownAll();
                    }
                  }
                });
              }
            } else {
              exception.set(completion.exception());
              completion.exception().printStackTrace();
              countDownAll();
            }
          }
        });
      }
    });

    azzert(exception.get() == null);
    azzert(fileExists(fileName));
    byte[] readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName));
    azzert(Utils.buffersEqual(buff, Buffer.create(readBytes)));
    throwAssertions();

  }

  @Test
  public void testReadAsync() throws Exception {
    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;

    byte[] content = Utils.generateRandomByteArray(chunkSize * chunks);
    final Buffer expected = Buffer.create(content);
    createFile(fileName, content);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {

      public void run() {
        FileSystem.instance.open(TEST_DIR + pathSep + fileName, null, true, false, false).handler(new CompletionHandler<AsyncFile>() {

          public void handle(Deferred<AsyncFile> completion) {
            if (completion.succeeded()) {
              final Buffer buff = Buffer.create(chunks * chunkSize);
              final AtomicInteger reads = new AtomicInteger(0);
              for (int i = 0; i < chunks; i++) {
                completion.result().read(buff, i * chunkSize, i * chunkSize, chunkSize).handler(new CompletionHandler<Buffer>() {
                  public void handle(Deferred<Buffer> completion) {
                    if (completion.succeeded()) {
                      if (reads.incrementAndGet() == chunks) {
                        azzert(Utils.buffersEqual(expected, buff));
                        azzert(buff == completion.result());
                        latch.countDown();
                      }
                    } else {
                      completion.exception().printStackTrace();
                      exception.set(completion.exception());
                      latch.countDown();
                    }
                  }
                });
              }
            } else {
              exception.set(completion.exception());
              completion.exception().printStackTrace();
              latch.countDown();
            }
          }
        });
      }
    });

    azzert(exception.get() == null);
    throwAssertions();
  }

  @Test
  public void testWriteStream() throws Exception {

    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;

    byte[] content = Utils.generateRandomByteArray(chunkSize * chunks);
    final Buffer buff = Buffer.create(content);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        FileSystem.instance.open(TEST_DIR + pathSep + fileName).handler(new CompletionHandler<AsyncFile>() {

          public void handle(Deferred<AsyncFile> completion) {
            if (completion.succeeded()) {
              WriteStream ws = completion.result().getWriteStream();

              ws.exceptionHandler(new Handler<Exception>() {
                public void handle(Exception e) {
                  exception.set(e);
                  latch.countDown();
                }
              });

              for (int i = 0; i < chunks; i++) {

                Buffer chunk = buff.copy(i * chunkSize, (i + 1) * chunkSize);
                azzert(chunk.length() == chunkSize);

                ws.writeBuffer(chunk);
              }

              completion.result().close().handler(new CompletionHandler<Void>() {
                public void handle(Deferred<Void> completion) {
                  if (completion.failed()) {
                    completion.exception().printStackTrace();
                    exception.set(completion.exception());
                  }
                  latch.countDown();
                }
              });
            } else {
              exception.set(completion.exception());
              completion.exception().printStackTrace();
              latch.countDown();
            }
          }
        });
      }
    });

    azzert(exception.get() == null);
    azzert(fileExists(fileName));
    byte[] readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName));
    azzert(Utils.buffersEqual(buff, Buffer.create(readBytes)));
    throwAssertions();
  }

  @Test
  public void testReadStream() throws Exception {
    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;

    final byte[] content = Utils.generateRandomByteArray(chunkSize * chunks);

    createFile(fileName, content);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {

      public void run() {
        FileSystem.instance.open(TEST_DIR + pathSep + fileName, null, true, false, false).handler(new CompletionHandler<AsyncFile>() {

          public void handle(Deferred<AsyncFile> completion) {
            if (completion.succeeded()) {
              ReadStream rs = completion.result().getReadStream();

              final Buffer buff = Buffer.create(0);

              rs.dataHandler(new Handler<Buffer>() {
                int count;

                public void handle(Buffer data) {
                  buff.appendBuffer(data);
                }
              });

              rs.exceptionHandler(new Handler<Exception>() {
                public void handle(Exception e) {
                  exception.set(e);
                  latch.countDown();
                }
              });

              rs.endHandler(new SimpleHandler() {
                public void handle() {
                  azzert(Utils.buffersEqual(buff, Buffer.create(content)));
                  latch.countDown();
                }
              });
            } else {
              exception.set(completion.exception());
              completion.exception().printStackTrace();
              latch.countDown();
            }
          }
        });
      }
    });

    azzert(exception.get() == null);
    throwAssertions();
  }


  @Test
  public void testPumpFileStreams() throws Exception {
    final String fileName1 = "some-file.dat";
    final String fileName2 = "some-other-file.dat";

    //Non integer multiple of buffer size
    final int fileSize = (int) (AsyncFile.BUFFER_SIZE * 1000.3);

    final byte[] content = Utils.generateRandomByteArray(fileSize);

    createFile(fileName1, content);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {

      public void run() {
        // Open file for reading
        FileSystem.instance.open(TEST_DIR + pathSep + fileName1, null, true, false, false).handler(new CompletionHandler<AsyncFile>() {

          public void handle(Deferred<AsyncFile> completion) {
            if (completion.succeeded()) {
              final ReadStream rs = completion.result().getReadStream();

              //Open file for writing
              FileSystem.instance.open(TEST_DIR + pathSep + fileName2, null, true, true, true).handler(new CompletionHandler<AsyncFile>() {

                public void handle(final Deferred<AsyncFile> completion) {
                  if (completion.succeeded()) {
                    WriteStream ws = completion.result().getWriteStream();

                    Pump p = new Pump(rs, ws);

                    p.start();

                    rs.endHandler(new SimpleHandler() {
                      public void handle() {
                        completion.result().close().handler(new CompletionHandler<Void>() {

                          public void handle(Deferred<Void> completion) {
                            if (completion.failed()) {
                              exception.set(completion.exception());
                              completion.exception().printStackTrace();
                            }
                            latch.countDown();
                          }
                        });
                      }
                    });
                  } else {
                    exception.set(completion.exception());
                    completion.exception().printStackTrace();
                    latch.countDown();
                  }
                }
              });
            } else {
              exception.set(completion.exception());
              completion.exception().printStackTrace();
              latch.countDown();
            }
          }
        });
      }
    });

    azzert(exception.get() == null);
    azzert(fileExists(fileName2));
    byte[] readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName2));
    azzert(Utils.buffersEqual(Buffer.create(content), Buffer.create(readBytes)));
    throwAssertions();
  }

  @Test
  public void testCreateFileNoPerms() throws Exception {
    testCreateFile(null);
  }

  @Test
  public void testCreateFileWithPerms() throws Exception {
    testCreateFile("rwx------");
  }

  private void testCreateFile(final String perms) throws Exception {
    final String fileName = "some-file.dat";

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler compl = new CompletionHandler<Void>() {
          public void handle(Deferred<Void> completion) {
            if (completion.failed()) {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };
        if (perms != null) {
          FileSystem.instance.createFile(TEST_DIR + pathSep + fileName, perms).handler(compl);
        } else {
          FileSystem.instance.createFile(TEST_DIR + pathSep + fileName).handler(compl);
        }
      }
    });

    azzert(exception.get() == null);
    azzert(fileExists(fileName));
    azzert(0 == fileLength(fileName));
    if (perms != null) {
      azzert(perms.equals(getPerms(fileName)));
    }
  }

  @Test
  public void testExists() throws Exception {
    final String fileName = "some-file.dat";
    createFileWithJunk(fileName, 100);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> exception = new AtomicReference<>();
    final AtomicReference<Boolean> ares = new AtomicReference<>();

    run(latch, new Runnable() {
      public void run() {
        CompletionHandler<Boolean> compl = new CompletionHandler<Boolean>() {
          public void handle(Deferred<Boolean> completion) {
            if (completion.succeeded()) {
              ares.set(completion.result());
            } else {
              exception.set(completion.exception());
            }
            latch.countDown();
          }
        };
        FileSystem.instance.exists(TEST_DIR + pathSep + fileName).handler(compl);
      }
    });

    azzert(exception.get() == null);
    azzert(ares.get());
  }

  public void testSync() throws Exception {
    //TODO
  }

  public void testFSStats() throws Exception {
    //TODO
  }


  // All file system operations need to be executed in a context
  private void run(CountDownLatch latch, final Runnable runner) throws Exception {

    final long context = NodexInternal.instance.createAndAssociateContext();

    NodexInternal.instance.executeOnContext(context, new Runnable() {
      public void run() {
        NodexInternal.instance.setContextID(context);
        runner.run();
      }
    });

    if (latch != null) azzert(latch.await(5, TimeUnit.SECONDS));

    NodexInternal.instance.destroyContext(context);
  }

  private void deleteDir(String dir) {
    deleteDir(new File(TEST_DIR + pathSep + dir));
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

  private void deleteFile(String fileName) {
    File file = new File(TEST_DIR + pathSep + fileName);
    file.delete();
  }

  private void mkDir(String dirName) {
    File dir = new File(TEST_DIR + pathSep + dirName);
    dir.mkdir();
  }

  private void createFileWithJunk(String fileName, long length) throws Exception {
    createFile(fileName, Utils.generateRandomByteArray((int) length));
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