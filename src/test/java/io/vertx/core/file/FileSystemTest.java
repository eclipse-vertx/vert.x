/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.*;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.impl.AsyncFileImpl;
import io.vertx.core.impl.Utils;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.test.core.TestUtils.*;
import static org.hamcrest.CoreMatchers.instanceOf;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystemTest extends VertxTestBase {

  private static final String DEFAULT_DIR_PERMS = "rwxr-xr-x";
  private static final String DEFAULT_FILE_PERMS = "rw-r--r--";

  private String pathSep;
  private String testDir;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  public void setUp() throws Exception {
    super.setUp();
    java.nio.file.FileSystem fs = FileSystems.getDefault();
    pathSep = fs.getSeparator();
    File ftestDir = testFolder.newFolder();
    testDir = ftestDir.toString();
  }

  @Test
  public void testIllegalArguments() throws Exception {
    assertNullPointerException(() -> vertx.fileSystem().copy(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().copy("ignored", null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().copyBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().copyBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().copyRecursive(null, "ignored", true, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().copyRecursive("ignored", null, true, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().copyRecursiveBlocking(null, "ignored", true));
    assertNullPointerException(() -> vertx.fileSystem().copyRecursiveBlocking("ignored", null, true));
    assertNullPointerException(() -> vertx.fileSystem().move(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().move("ignored", null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().moveBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().moveBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().truncate(null, 0, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().truncateBlocking(null, 0));
    assertNullPointerException(() -> vertx.fileSystem().chmod(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().chmod("ignored", null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().chmodBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chmodBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().chmodRecursive(null, "ignored", "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().chmodRecursive("ignored", null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().chmodRecursiveBlocking(null, "ignored", "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chmodRecursiveBlocking("ignored", null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chown(null, "ignored", "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().chownBlocking(null, "ignored", "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().props(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().propsBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().lprops(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().lpropsBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().link(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().link("ignored", null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().linkBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().linkBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().symlink(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().symlink("ignored", null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().symlinkBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().symlinkBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().unlink(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().unlinkBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().readSymlink(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().readSymlinkBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().delete(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().deleteBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().deleteRecursive(null, true, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().deleteRecursiveBlocking(null, true));
    assertNullPointerException(() -> vertx.fileSystem().mkdir(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().mkdirBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().mkdir(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().mkdirBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().mkdirs(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().mkdirsBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().mkdirs(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().mkdirsBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().readDir(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().readDirBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().readDir(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().readDirBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().readFile(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().readFileBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().writeFile(null, Buffer.buffer(), h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().writeFile("ignored", null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().writeFileBlocking(null, Buffer.buffer()));
    assertNullPointerException(() -> vertx.fileSystem().writeFileBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().open(null, new OpenOptions(), h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().open("ignored", null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().openBlocking(null, new OpenOptions()));
    assertNullPointerException(() -> vertx.fileSystem().openBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().createFile(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().createFileBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().createFile(null, "ignored", h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().createFileBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().exists(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().existsBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().fsProps(null, h -> {}));
    assertNullPointerException(() -> vertx.fileSystem().fsPropsBlocking(null));

    String fileName = "some-file.dat";
    AsyncFile asyncFile = vertx.fileSystem().openBlocking(testDir + pathSep + fileName, new OpenOptions());

    assertNullPointerException(() -> asyncFile.write(null));
    assertIllegalArgumentException(() -> asyncFile.setWriteQueueMaxSize(1));
    assertIllegalArgumentException(() -> asyncFile.setWriteQueueMaxSize(0));
    assertIllegalArgumentException(() -> asyncFile.setWriteQueueMaxSize(-1));
    assertNullPointerException(() -> asyncFile.write(null, 0, h -> {}));
    assertNullPointerException(() -> asyncFile.write(Buffer.buffer(), 0, null));
    assertIllegalArgumentException(() -> asyncFile.write(Buffer.buffer(), -1, h -> {}));

    assertNullPointerException(() -> asyncFile.read(null, 0, 0, 0, h -> {}));
    assertNullPointerException(() -> asyncFile.read(Buffer.buffer(), 0, 0, 0, null));

    assertIllegalArgumentException(() -> asyncFile.read(Buffer.buffer(), -1, 0, 0, h -> {}));
    assertIllegalArgumentException(() -> asyncFile.read(Buffer.buffer(), 0, -1, 0, h -> {}));
    assertIllegalArgumentException(() -> asyncFile.read(Buffer.buffer(), 0, 0, -1, h -> {}));
  }

  @Test
  public void testSimpleCopy() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    testCopy(source, target, false, true, v-> {
      assertTrue(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
  }

  @Test
  public void testSimpleCopyFileAlreadyExists() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);
    testCopy(source, target, false, false, v -> {
      assertTrue(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
  }

  @Test
  public void testCopyIntoDir() throws Exception {
    String source = "foo.txt";
    String dir = "some-dir";
    String target = dir + pathSep + "bar.txt";
    mkDir(dir);
    createFileWithJunk(source, 100);
    testCopy(source, target, false, true, v -> {
      assertTrue(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
  }

  @Test
  public void testCopyEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    mkDir(source);
    testCopy(source, target, false, true, v -> {
      assertTrue(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
  }

  @Test
  public void testCopyNonEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    String file1 = pathSep + "somefile.bar";
    mkDir(source);
    createFileWithJunk(source + file1, 100);
    testCopy(source, target, false, true, v -> {
      assertTrue(fileExists(source));
      assertTrue(fileExists(target));
      assertFalse(fileExists(target + file1));
    });
    await();
  }

  @Test
  public void testFailCopyDirAlreadyExists() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    mkDir(source);
    mkDir(target);
    testCopy(source, target, false, false, v -> {
      assertTrue(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
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
    testCopy(dir, target, true, true, v -> {
      assertTrue(fileExists(dir));
      assertTrue(fileExists(target));
      assertTrue(fileExists(target + file1));
      assertTrue(fileExists(target + file2));
      assertTrue(fileExists(target + pathSep + dir2 + file3));
    });
    await();
  }

  private void testCopy(String source, String target, boolean recursive,
                        boolean shouldPass, Handler<Void> afterOK) {
    if (recursive) {
      vertx.fileSystem().copyRecursive(testDir + pathSep + source, testDir + pathSep + target, true, createHandler(shouldPass, afterOK));
    } else {
      vertx.fileSystem().copy(testDir + pathSep + source, testDir + pathSep + target, createHandler(shouldPass, afterOK));
    }
  }

  @Test
  public void testSimpleMove() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    testMove(source, target, true, v -> {
      assertFalse(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
  }

  @Test
  public void testSimpleMoveFileAlreadyExists() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);
    testMove(source, target, false, v -> {
      assertTrue(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
  }

  @Test
  public void testMoveEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    mkDir(source);
    testMove(source, target, true, v -> {
      assertFalse(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
  }

  @Test
  public void testMoveEmptyDirTargetExists() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    mkDir(source);
    mkDir(target);
    testMove(source, target, false, v -> {
      assertTrue(fileExists(source));
      assertTrue(fileExists(target));
    });
    await();
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
    testMove(dir, target, true, v -> {
      assertFalse(fileExists(dir));
      assertTrue(fileExists(target));
      assertTrue(fileExists(target + file1));
      assertTrue(fileExists(target + file2));
      assertTrue(fileExists(target + pathSep + dir2 + file3));
    });
    await();
  }

  private void testMove(String source, String target, boolean shouldPass, Handler<Void> afterOK) throws Exception {
    vertx.fileSystem().move(testDir + pathSep + source, testDir + pathSep + target, createHandler(shouldPass, afterOK));
  }

  @Test
  public void testTruncate() throws Exception {
    String file1 = "some-file.dat";
    long initialLen = 1000;
    long truncatedLen = 534;
    createFileWithJunk(file1, initialLen);
    assertEquals(initialLen, fileLength(file1));
    testTruncate(file1, truncatedLen, true, v -> {
      assertEquals(truncatedLen, fileLength(file1));
    });
    await();
  }

  @Test
  public void testTruncateExtendsFile() throws Exception {
    String file1 = "some-file.dat";
    long initialLen = 500;
    long truncatedLen = 1000;
    createFileWithJunk(file1, initialLen);
    assertEquals(initialLen, fileLength(file1));
    testTruncate(file1, truncatedLen, true, v -> {
      assertEquals(truncatedLen, fileLength(file1));
    });
    await();
  }

  @Test
  public void testTruncateFileDoesNotExist() throws Exception {
    String file1 = "some-file.dat";
    long truncatedLen = 534;
    testTruncate(file1, truncatedLen, false, null);
    await();
  }

  private void testTruncate(String file, long truncatedLen, boolean shouldPass,
                            Handler<Void> afterOK) throws Exception {
    vertx.fileSystem().truncate(testDir + pathSep + file, truncatedLen, createHandler(shouldPass, afterOK));
  }

  @Test
  public void testChmodNonRecursive1() throws Exception {
    testChmodNonRecursive("rw-------");
  }

  @Test
  public void testChmodNonRecursive2() throws Exception {
    testChmodNonRecursive("rwx------");
  }

  @Test
  public void testChmodNonRecursive3() throws Exception {
    testChmodNonRecursive("rw-rw-rw-");
  }

  @Test
  public void testChmodNonRecursive4() throws Exception {
    testChmodNonRecursive("rw-r--r--");
  }

  @Test
  public void testChmodNonRecursive5() throws Exception {
    testChmodNonRecursive("rw--w--w-");
  }

  @Test
  public void testChmodNonRecursive6() throws Exception {
    testChmodNonRecursive("rw-rw-rw-");
  }

  private void testChmodNonRecursive(String perms) throws Exception {
    String file1 = "some-file.dat";
    createFileWithJunk(file1, 100);
    testChmod(file1, perms, null, true, v -> {
      azzertPerms(perms, file1);
      deleteFile(file1);
    });
    await();
  }

  private void azzertPerms(String perms, String file1) {
    if (!Utils.isWindows()) {
      assertEquals(perms, getPerms(file1));
    }
  }

  @Test
  public void testChmodRecursive1() throws Exception {
    testChmodRecursive("rw-------", "rwx------");
  }

  @Test
  public void testChmodRecursive2() throws Exception {
    testChmodRecursive("rwx------", "rwx------");
  }

  @Test
  public void testChmodRecursive3() throws Exception {
    testChmodRecursive("rw-rw-rw-", "rwxrw-rw-");
  }

  @Test
  public void testChmodRecursive4() throws Exception {
    testChmodRecursive("rw-r--r--", "rwxr--r--");
  }

  @Test
  public void testChmodRecursive5() throws Exception {
    testChmodRecursive("rw--w--w-", "rwx-w--w-");
  }

  @Test
  public void testChmodRecursive6() throws Exception {
    testChmodRecursive("rw-rw-rw-", "rwxrw-rw-");
  }

  private void testChmodRecursive(String perms, String dirPerms) throws Exception {
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
    testChmod(dir, perms, dirPerms, true, v -> {
      azzertPerms(dirPerms, dir);
      azzertPerms(perms, dir + file1);
      azzertPerms(perms, dir + file2);
      azzertPerms(dirPerms, dir + pathSep + dir2);
      azzertPerms(perms, dir + pathSep + dir2 + file3);
      deleteDir(dir);
    });
    await();
  }

  @Test
  public void testChownToRootFails() throws Exception {
    testChownFails("root");
  }

  @Test
  public void testChownToNotExistingUserFails() throws Exception {
    testChownFails("jfhfhjejweg");
  }

  private void testChownFails(String user) throws Exception {
    String file1 = "some-file.dat";
    createFileWithJunk(file1, 100);
    vertx.fileSystem().chown(testDir + pathSep + file1, user, null, ar -> {
      deleteFile(file1);
      assertTrue(ar.failed());
      testComplete();
    });
    await();
  }

  @Test
  public void testChownToOwnUser() throws Exception {
    String file1 = "some-file.dat";
    createFileWithJunk(file1, 100);
    String fullPath = testDir + pathSep + file1;
    Path path = Paths.get(fullPath);
    UserPrincipal owner = Files.getOwner(path);
    String user = owner.getName();
    vertx.fileSystem().chown(fullPath, user, null, ar -> {
      deleteFile(file1);
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testChownToOwnGroup() throws Exception {
    // Not supported in WindowsFileSystemProvider
    Assume.assumeFalse(Utils.isWindows());
    String file1 = "some-file.dat";
    createFileWithJunk(file1, 100);
    String fullPath = testDir + pathSep + file1;
    Path path = Paths.get(fullPath);
    GroupPrincipal group = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS).group();
    vertx.fileSystem().chown(fullPath, null, group.getName(), ar -> {
      deleteFile(file1);
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  private void testChmod(String file, String perms, String dirPerms,
                         boolean shouldPass, Handler<Void> afterOK) throws Exception {
    if (Files.isDirectory(Paths.get(testDir + pathSep + file))) {
      azzertPerms(DEFAULT_DIR_PERMS, file);
    } else {
      azzertPerms(DEFAULT_FILE_PERMS, file);
    }
    if (dirPerms != null) {
      vertx.fileSystem().chmodRecursive(testDir + pathSep + file, perms, dirPerms, createHandler(shouldPass, afterOK));
    } else {
      vertx.fileSystem().chmod(testDir + pathSep + file, perms, createHandler(shouldPass, afterOK));
    }
  }

  @Test
  public void testProps() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;

    // The times are quite inaccurate so we give 1 second leeway
    long start = 1000 * (System.currentTimeMillis() / 1000 - 1);
    createFileWithJunk(fileName, fileSize);

    testProps(fileName, false, true, st -> {
      assertNotNull(st);
      assertEquals(fileSize, st.size());
      assertTrue(st.creationTime() >= start);
      assertTrue(st.lastAccessTime() >= start);
      assertTrue(st.lastModifiedTime() >= start);
      assertFalse(st.isDirectory());
      assertTrue(st.isRegularFile());
      assertFalse(st.isSymbolicLink());
    });
    await();
  }

  @Test
  public void testPropsFileDoesNotExist() throws Exception {
    String fileName = "some-file.txt";
    testProps(fileName, false, false, null);
    await();
  }

  @Test
  public void testPropsFollowLink() throws Exception {
    // Symlinks require a modified security policy in Windows. -- See http://stackoverflow.com/questions/23217460/how-to-create-soft-symbolic-link-using-java-nio-files
    Assume.assumeFalse(Utils.isWindows());
    String fileName = "some-file.txt";
    long fileSize = 1234;

    // The times are quite inaccurate so we give 1 second leeway
    long start = 1000 * (System.currentTimeMillis() / 1000 - 1);
    createFileWithJunk(fileName, fileSize);
    long end = 1000 * (System.currentTimeMillis() / 1000 + 1);

    String linkName = "some-link.txt";
    Files.createSymbolicLink(Paths.get(testDir + pathSep + linkName), Paths.get(fileName));

    testProps(linkName, false, true, st -> {
      assertNotNull(st);
      assertEquals(fileSize, st.size());
      assertTrue(st.creationTime() >= start);
      assertTrue(st.creationTime() <= end);
      assertTrue(st.lastAccessTime() >= start);
      assertTrue(st.lastAccessTime() <= end);
      assertTrue(st.lastModifiedTime() >= start);
      assertTrue(st.lastModifiedTime() <= end);
      assertFalse(st.isDirectory());
      assertFalse(st.isOther());
      assertTrue(st.isRegularFile());
      assertFalse(st.isSymbolicLink());
    });
    await();
  }

  @Test
  public void testPropsDontFollowLink() throws Exception {
    // Symlinks require a modified security policy in Windows. -- See http://stackoverflow.com/questions/23217460/how-to-create-soft-symbolic-link-using-java-nio-files
    Assume.assumeFalse(Utils.isWindows());
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String linkName = "some-link.txt";
    Files.createSymbolicLink(Paths.get(testDir + pathSep + linkName), Paths.get(fileName));
    testProps(linkName, true, true, st -> {
      assertNotNull(st != null);
      assertTrue(st.isSymbolicLink());
    });
    await();
  }

  private void testProps(String fileName, boolean link, boolean shouldPass,
                         Handler<FileProps> afterOK) throws Exception {
    Handler<AsyncResult<FileProps>> handler = ar -> {
      if (ar.failed()) {
        if (shouldPass) {
          fail(ar.cause().getMessage());
        } else {
          assertTrue(ar.cause() instanceof io.vertx.core.file.FileSystemException);
          if (afterOK != null) {
            afterOK.handle(ar.result());
          }
          testComplete();
        }
      } else {
        if (shouldPass) {
          if (afterOK != null) {
            afterOK.handle(ar.result());
          }
          testComplete();
        } else {
          fail("stat should fail");
        }
      }
    };
    if (link) {
      vertx.fileSystem().lprops(testDir + pathSep + fileName, handler);
    } else {
      vertx.fileSystem().props(testDir + pathSep + fileName, handler);
    }
  }

  @Test
  public void testLink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String linkName = "some-link.txt";
    testLink(linkName, fileName, false, true, v -> {
      assertEquals(fileSize, fileLength(linkName));
      assertFalse(Files.isSymbolicLink(Paths.get(testDir + pathSep + linkName)));
    });
    await();
  }

  @Test
  public void testSymLink() throws Exception {
    // Symlinks require a modified security policy in Windows. -- See http://stackoverflow.com/questions/23217460/how-to-create-soft-symbolic-link-using-java-nio-files
    Assume.assumeFalse(Utils.isWindows());
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String symlinkName = "some-sym-link.txt";
    testLink(symlinkName, fileName, true, true, v -> {
      assertEquals(fileSize, fileLength(symlinkName));
      assertTrue(Files.isSymbolicLink(Paths.get(testDir + pathSep + symlinkName)));
      // Now try reading it
      String read = vertx.fileSystem().readSymlinkBlocking(testDir + pathSep + symlinkName);
      assertEquals(fileName, read);
    });
    await();
  }

  private void testLink(String from, String to, boolean symbolic,
                        boolean shouldPass, Handler<Void> afterOK) throws Exception {
    if (symbolic) {
      // Symlink is relative
      vertx.fileSystem().symlink(testDir + pathSep + from, to, createHandler(shouldPass, afterOK));
    } else {
      vertx.fileSystem().link(testDir + pathSep + from, testDir + pathSep + to, createHandler(shouldPass, afterOK));
    }
  }

  @Test
  public void testUnlink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String linkName = "some-link.txt";
    Files.createLink(Paths.get(testDir + pathSep + linkName), Paths.get(testDir + pathSep + fileName));
    assertEquals(fileSize, fileLength(linkName));
    vertx.fileSystem().unlink(testDir + pathSep + linkName, createHandler(true, v -> assertFalse(fileExists(linkName))));
    await();
  }

  @Test
  public void testReadSymLink() throws Exception {
    // Symlinks require a modified security policy in Windows. -- See http://stackoverflow.com/questions/23217460/how-to-create-soft-symbolic-link-using-java-nio-files
    Assume.assumeFalse(Utils.isWindows());
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String linkName = "some-link.txt";
    Files.createSymbolicLink(Paths.get(testDir + pathSep + linkName), Paths.get(fileName));
    vertx.fileSystem().readSymlink(testDir + pathSep + linkName, ar -> {
      if (ar.failed()) {
        fail(ar.cause().getMessage());
      } else {
        assertEquals(fileName, ar.result());
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testSimpleDelete() throws Exception {
    String fileName = "some-file.txt";
    createFileWithJunk(fileName, 100);
    assertTrue(fileExists(fileName));
    testDelete(fileName, false, true, v -> {
      assertFalse(fileExists(fileName));
    });
    await();
  }

  @Test
  public void testDeleteEmptyDir() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    assertTrue(fileExists(dirName));
    testDelete(dirName, false, true, v -> {
      assertFalse(fileExists(dirName));
    });
    await();
  }

  @Test
  public void testDeleteNonExistent() throws Exception {
    String dirName = "some-dir";
    assertFalse(fileExists(dirName));
    testDelete(dirName, false, false, null);
    await();
  }

  @Test
  public void testDeleteNonEmptyFails() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    String file1 = "some-file.txt";
    createFileWithJunk(dirName + pathSep + file1, 100);
    testDelete(dirName, false, false, null);
    await();
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
    testDelete(dir, true, true, v -> {
      assertFalse(fileExists(dir));
    });
    await();
  }

  private void testDelete(String fileName, boolean recursive, boolean shouldPass,
                          Handler<Void> afterOK) throws Exception {
    if (recursive) {
      vertx.fileSystem().deleteRecursive(testDir + pathSep + fileName, recursive, createHandler(shouldPass, afterOK));
    } else {
      vertx.fileSystem().delete(testDir + pathSep + fileName, createHandler(shouldPass, afterOK));
    }
  }

  @Test
  public void testMkdirSimple() throws Exception {
    String dirName = "some-dir";
    testMkdir(dirName, null, false, true, v -> {
      assertTrue(fileExists(dirName));
      assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
    });
    await();
  }

  @Test
  public void testMkdirWithParentsFails() throws Exception {
    String dirName = "top-dir" + pathSep + "some-dir";
    testMkdir(dirName, null, false, false, null);
    await();
  }

  @Test
  public void testMkdirWithPerms() throws Exception {
    String dirName = "some-dir";
    String perms = "rwx--x--x";
    testMkdir(dirName, perms, false, true, v -> {
      assertTrue(fileExists(dirName));
      assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
      azzertPerms(perms, dirName);
    });
    await();
  }

  @Test
  public void testMkdirCreateParents() throws Exception {
    String dirName = "top-dir" + pathSep + "/some-dir";
    testMkdir(dirName, null, true, true, v -> {
      assertTrue(fileExists(dirName));
      assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
    });
    await();
  }

  @Test
  public void testMkdirCreateParentsWithPerms() throws Exception {
    String dirName = "top-dir" + pathSep + "/some-dir";
    String perms = "rwx--x--x";
    testMkdir(dirName, perms, true, true, v -> {
      assertTrue(fileExists(dirName));
      assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
      azzertPerms(perms, dirName);
    });
    await();
  }

  private void testMkdir(String dirName, String perms, boolean createParents,
                         boolean shouldPass, Handler<Void> afterOK) throws Exception {
    Handler<AsyncResult<Void>> handler = createHandler(shouldPass, afterOK);
    if (createParents) {
      if (perms != null) {
        vertx.fileSystem().mkdirs(testDir + pathSep + dirName, perms, handler);
      } else {
        vertx.fileSystem().mkdirs(testDir + pathSep + dirName, handler);
      }
    } else {
      if (perms != null) {
        vertx.fileSystem().mkdir(testDir + pathSep + dirName, perms, handler);
      } else {
        vertx.fileSystem().mkdir(testDir + pathSep + dirName, handler);
      }
    }
  }

  @Test
  public void testReadDirSimple() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    int numFiles = 10;
    for (int i = 0; i < numFiles; i++) {
      createFileWithJunk(dirName + pathSep + "file-" + i + ".dat", 100);
    }
    testReadDir(dirName, null, true, fileNames -> {
      assertEquals(numFiles, fileNames.size());
      Set<String> fset = new HashSet<String>();
      for (String fileName: fileNames) {
        fset.add(fileName);
      }
      File dir = new File(testDir + pathSep + dirName);
      String root;
      try {
        root = dir.getCanonicalPath();
      } catch (IOException e) {
        fail(e.getMessage());
        return;
      }
      for (int i = 0; i < numFiles; i++) {
        assertTrue(fset.contains(root + pathSep + "file-" + i + ".dat"));
      }
    });
    await();
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
    testReadDir(dirName, "foo.+", true, fileNames -> {
      assertEquals(numFiles, fileNames.size());
      Set<String> fset = new HashSet<>();
      for (String fileName: fileNames) {
        fset.add(fileName);
      }
      File dir = new File(testDir + pathSep + dirName);
      String root;
      try {
        root = dir.getCanonicalPath();
      } catch (IOException e) {
        fail(e.getMessage());
        return;
      }
      for (int i = 0; i < numFiles; i++) {
        assertTrue(fset.contains(root + pathSep + "foo-" + i + ".txt"));
      }
    });
    await();
  }

  private void testReadDir(String dirName, String filter, boolean shouldPass,
                           Handler<List<String>> afterOK) throws Exception {
    Handler<AsyncResult<List<String>>> handler = ar -> {
      if (ar.failed()) {
        if (shouldPass) {
          fail(ar.cause().getMessage());
        } else {
          assertTrue(ar.cause() instanceof FileSystemException);
          if (afterOK != null) {
            afterOK.handle(null);
          }
          testComplete();
        }
      } else {
        if (shouldPass) {
          if (afterOK != null) {
            afterOK.handle(ar.result());
          }
          testComplete();
        } else {
          fail("read should fail");
        }
      }
    };
    if (filter == null) {
      vertx.fileSystem().readDir(testDir + pathSep + dirName, handler);
    } else {
      vertx.fileSystem().readDir(testDir + pathSep + dirName, filter, handler);
    }
  }

  @Test
  public void testReadFile() throws Exception {
    byte[] content = TestUtils.randomByteArray(1000);
    String fileName = "some-file.dat";
    createFile(fileName, content);

    vertx.fileSystem().readFile(testDir + pathSep + fileName, ar -> {
      if (ar.failed()) {
        fail(ar.cause().getMessage());
      } else {
        assertEquals(Buffer.buffer(content), ar.result());
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testWriteFile() throws Exception {
    byte[] content = TestUtils.randomByteArray(1000);
    Buffer buff = Buffer.buffer(content);
    String fileName = "some-file.dat";
    vertx.fileSystem().writeFile(testDir + pathSep + fileName, buff, ar -> {
      if (ar.failed()) {
        fail(ar.cause().getMessage());
      } else {
        assertTrue(fileExists(fileName));
        assertEquals(content.length, fileLength(fileName));
        byte[] readBytes;
        try {
          readBytes = Files.readAllBytes(Paths.get(testDir + pathSep + fileName));
        } catch (IOException e) {
          fail(e.getMessage());
          return;
        }
        assertEquals(buff, Buffer.buffer(readBytes));
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testWriteAsync() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    Buffer buff = Buffer.buffer(content);
    AtomicInteger count = new AtomicInteger();
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), arr -> {
      if (arr.succeeded()) {
        for (int i = 0; i < chunks; i++) {
          Buffer chunk = buff.getBuffer(i * chunkSize, (i + 1) * chunkSize);
          assertEquals(chunkSize, chunk.length());
          arr.result().write(chunk, i * chunkSize, ar -> {
            if (ar.succeeded()) {
              if (count.incrementAndGet() == chunks) {
                arr.result().close(ar2 -> {
                  if (ar2.failed()) {
                    fail(ar2.cause().getMessage());
                  } else {
                    assertTrue(fileExists(fileName));
                    byte[] readBytes;
                    try {
                      readBytes = Files.readAllBytes(Paths.get(testDir + pathSep + fileName));
                    } catch (IOException e) {
                      fail(e.getMessage());
                      return;
                    }
                    Buffer read = Buffer.buffer(readBytes);
                    assertEquals(buff, read);
                    testComplete();
                  }
                });
              }
            } else {
              fail(ar.cause().getMessage());
            }
          });
        }
      } else {
        fail(arr.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testReadAsync() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    Buffer expected = Buffer.buffer(content);
    createFile(fileName, content);
    AtomicInteger reads = new AtomicInteger();
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), arr -> {
      if (arr.succeeded()) {
        Buffer buff = Buffer.buffer(chunks * chunkSize);
        for (int i = 0; i < chunks; i++) {
          arr.result().read(buff, i * chunkSize, i * chunkSize, chunkSize, arb -> {
            if (arb.succeeded()) {
              if (reads.incrementAndGet() == chunks) {
                arr.result().close(ar -> {
                  if (ar.failed()) {
                    fail(ar.cause().getMessage());
                  } else {
                    assertEquals(expected, buff);
                    assertEquals(buff, arb.result());
                    testComplete();
                  }
                });
              }
            } else {
              fail(arb.cause().getMessage());
            }
          });
        }
      } else {
        fail(arr.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testWriteStream() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    Buffer buff = Buffer.buffer(content);
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), ar -> {
      if (ar.succeeded()) {
        WriteStream<Buffer> ws = ar.result();
        ws.exceptionHandler(t -> fail(t.getMessage()));
        for (int i = 0; i < chunks; i++) {
          Buffer chunk = buff.getBuffer(i * chunkSize, (i + 1) * chunkSize);
          assertEquals(chunkSize, chunk.length());
          ws.write(chunk);
        }
        ar.result().close(ar2 -> {
          if (ar2.failed()) {
            fail(ar2.cause().getMessage());
          } else {
            assertTrue(fileExists(fileName));
            byte[] readBytes;
            try {
              readBytes = Files.readAllBytes(Paths.get(testDir + pathSep + fileName));
            } catch (IOException e) {
              fail(e.getMessage());
              return;
            }
            assertEquals(buff, Buffer.buffer(readBytes));
            testComplete();
          }
        });
      } else {
        fail(ar.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testWriteStreamAppend() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] existing = TestUtils.randomByteArray(1000);
    createFile(fileName, existing);
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    Buffer buff = Buffer.buffer(content);
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions().setAppend(true), ar -> {
      if (ar.succeeded()) {
        AsyncFile ws = ar.result();
        ws.exceptionHandler(t -> fail(t.getMessage()));
        for (int i = 0; i < chunks; i++) {
          Buffer chunk = buff.getBuffer(i * chunkSize, (i + 1) * chunkSize);
          assertEquals(chunkSize, chunk.length());
          ws.write(chunk);
        }
        ar.result().close(ar2 -> {
          if (ar2.failed()) {
            fail(ar2.cause().getMessage());
          } else {
            assertTrue(fileExists(fileName));
            byte[] readBytes;
            try {
              readBytes = Files.readAllBytes(Paths.get(testDir + pathSep + fileName));
            } catch (IOException e) {
              fail(e.getMessage());
              return;
            }
            assertEquals(Buffer.buffer(existing).appendBuffer(buff), Buffer.buffer(readBytes));
            testComplete();
          }
        });
      } else {
        fail(ar.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testWriteStreamWithCompositeBuffer() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content1 = TestUtils.randomByteArray(chunkSize * (chunks / 2));
    byte[] content2 = TestUtils.randomByteArray(chunkSize * (chunks / 2));
    ByteBuf byteBuf = Unpooled.wrappedBuffer(content1, content2);
    Buffer buff = Buffer.buffer(byteBuf);
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), ar -> {
      if (ar.succeeded()) {
        WriteStream<Buffer> ws = ar.result();
        ws.exceptionHandler(t -> fail(t.getMessage()));
        ws.write(buff);
        ar.result().close(ar2 -> {
          if (ar2.failed()) {
            fail(ar2.cause().getMessage());
          } else {
            assertTrue(fileExists(fileName));
            byte[] readBytes;
            try {
              readBytes = Files.readAllBytes(Paths.get(testDir + pathSep + fileName));
            } catch (IOException e) {
              fail(e.getMessage());
              return;
            }
            assertEquals(buff, Buffer.buffer(readBytes));
            byteBuf.release();
            testComplete();
          }
        });
      } else {
        fail(ar.cause().getMessage());
      }
    });
    await();
  }

  enum ReadStrategy {

    NONE {
      @Override void init(ReadStream<Buffer> stream) { }
      @Override void handle(ReadStream<Buffer> stream) { }
    },

    FLOWING {
      @Override
      void init(ReadStream<Buffer> stream) {
        flowing.set(true);
      }

      @Override
      void handle(ReadStream<Buffer> stream) {
        assert flowing.getAndSet(false);
        stream.pause();
        Vertx.currentContext().owner().setTimer(1, id -> {
          assert !flowing.getAndSet(true);
          stream.resume();
        });
      }
    },

    FETCH {
      @Override
      void init(ReadStream<Buffer> stream) {
        fetching.set(true);
        stream.pause();
        stream.fetch(1);
      }
      @Override
      void handle(ReadStream<Buffer> stream) {
        assert fetching.getAndSet(false);
        Vertx.currentContext().owner().setTimer(1, id -> {
          assert !fetching.getAndSet(true);
          stream.fetch(1);
        });
      }
    };

    final static AtomicBoolean flowing = new AtomicBoolean();
    final static AtomicBoolean fetching = new AtomicBoolean();

    abstract void init(ReadStream<Buffer> stream);
    abstract void handle(ReadStream<Buffer> stream);

  }

  @Test
  public void testReadStream() throws Exception {
    testReadStream(ReadStrategy.NONE);
  }

  @Test
  public void testReadStreamFlowing() throws Exception {
    testReadStream(ReadStrategy.FLOWING);
  }

  @Test
  public void testReadStreamFetch() throws Exception {
    testReadStream(ReadStrategy.FETCH);
  }

  private void testReadStream(ReadStrategy strategy) throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    createFile(fileName, content);
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), ar -> {
      if (ar.succeeded()) {
        ReadStream<Buffer> rs = ar.result();
        strategy.init(rs);
        Buffer buff = Buffer.buffer();
        rs.handler(chunk -> {
          buff.appendBuffer(chunk);
          strategy.handle(rs);
        });
        rs.exceptionHandler(t -> fail(t.getMessage()));
        rs.endHandler(v -> {
          ar.result().close(ar2 -> {
            if (ar2.failed()) {
              fail(ar2.cause().getMessage());
            } else {
              assertEquals(Buffer.buffer(content), buff);
              testComplete();
            }
          });
        });
      } else {
        fail(ar.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testReadStreamWithBufferSize() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 16384;
    int chunks = 1;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    createFile(fileName, content);
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), ar -> {
      if (ar.succeeded()) {
        AsyncFile rs = ar.result();
        rs.setReadBufferSize(chunkSize);
        Buffer buff = Buffer.buffer();
        int[] callCount = {0};
        rs.handler((buff1) -> {
          buff.appendBuffer(buff1);
          callCount[0]++;
        });
        rs.exceptionHandler(t -> fail(t.getMessage()));
        rs.endHandler(v -> {
          ar.result().close(ar2 -> {
            if (ar2.failed()) {
              fail(ar2.cause().getMessage());
            } else {
              assertEquals(1, callCount[0]);
              assertEquals(Buffer.buffer(content), buff);
              testComplete();
            }
          });
        });
      } else {
        fail(ar.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testReadStreamSetReadPos() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    createFile(fileName, content);
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), ar -> {
      if (ar.succeeded()) {
        AsyncFile rs = ar.result();
        rs.setReadPos(chunkSize * chunks / 2);
        Buffer buff = Buffer.buffer();
        rs.handler(buff::appendBuffer);
        rs.exceptionHandler(t -> fail(t.getMessage()));
        rs.endHandler(v -> {
          ar.result().close(ar2 -> {
            if (ar2.failed()) {
              fail(ar2.cause().getMessage());
            } else {
              assertEquals(chunkSize * chunks / 2, buff.length());
              byte[] lastHalf = new byte[chunkSize * chunks / 2];
              System.arraycopy(content, chunkSize * chunks / 2, lastHalf, 0, chunkSize * chunks / 2);
              assertEquals(Buffer.buffer(lastHalf), buff);
              testComplete();
            }
          });
        });
      } else {
        fail(ar.cause().getMessage());
      }
    });
    await();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPumpFileStreams() throws Exception {
    String fileName1 = "some-file.dat";
    String fileName2 = "some-other-file.dat";

    //Non integer multiple of buffer size
    int fileSize = (int) (AsyncFileImpl.DEFAULT_READ_BUFFER_SIZE * 1000.3);
    byte[] content = TestUtils.randomByteArray(fileSize);
    createFile(fileName1, content);

    vertx.fileSystem().open(testDir + pathSep + fileName1, new OpenOptions(), arr -> {
      if (arr.succeeded()) {
        ReadStream rs = arr.result();
        //Open file for writing
        vertx.fileSystem().open(testDir + pathSep + fileName2, new OpenOptions(), ar -> {
          if (ar.succeeded()) {
            WriteStream ws = ar.result();
            Pump p = Pump.pump(rs, ws);
            p.start();
            rs.endHandler(v -> {
              arr.result().close(car -> {
                if (car.failed()) {
                  fail(ar.cause().getMessage());
                } else {
                  ar.result().close(ar2 -> {
                    if (ar2.failed()) {
                      fail(ar2.cause().getMessage());
                    } else {
                      assertTrue(fileExists(fileName2));
                      byte[] readBytes;
                      try {
                        readBytes = Files.readAllBytes(Paths.get(testDir + pathSep + fileName2));
                      } catch (IOException e) {
                        fail(e.getMessage());
                        return;
                      }
                      assertEquals(Buffer.buffer(content), Buffer.buffer(readBytes));
                      testComplete();
                    }
                  });
                }
              });
            });
          } else {
            fail(ar.cause().getMessage());
          }
        });
      } else {
        fail(arr.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testCreateFileNoPerms() throws Exception {
    testCreateFile(null, true);
  }

  @Test
  public void testCreateFileWithPerms() throws Exception {
    testCreateFile("rwx------", true);
  }

  @Test
  public void testCreateFileAlreadyExists() throws Exception {
    createFileWithJunk("some-file.dat", 100);
    testCreateFile(null, false);
  }

  private void testCreateFile(String perms, boolean shouldPass) throws Exception {
    String fileName = "some-file.dat";
    Handler<AsyncResult<Void>> handler = ar -> {
      if (ar.failed()) {
        if (shouldPass) {
          fail(ar.cause().getMessage());
        } else {
          assertTrue(ar.cause() instanceof FileSystemException);
          testComplete();
        }
      } else {
        if (shouldPass) {
          assertTrue(fileExists(fileName));
          assertEquals(0, fileLength(fileName));
          if (perms != null) {
            azzertPerms(perms, fileName);
          }
          testComplete();
        } else {
          fail("test should fail");
        }
      }
    };
    if (perms != null) {
      vertx.fileSystem().createFile(testDir + pathSep + fileName, perms, handler);
    } else {
      vertx.fileSystem().createFile(testDir + pathSep + fileName, handler);
    }
    await();
  }

  @Test
  public void testExists() throws Exception {
    testExists(true);
  }

  @Test
  public void testNotExists() throws Exception {
    testExists(false);
  }

  private void testExists(boolean exists) throws Exception {
    String fileName = "some-file.dat";
    if (exists) {
      createFileWithJunk(fileName, 100);
    }
    vertx.fileSystem().exists(testDir + pathSep + fileName, ar -> {
      if (ar.succeeded()) {
        if (exists) {
          assertTrue(ar.result());
        } else {
          assertFalse(ar.result());
        }
        testComplete();
      } else {
        fail(ar.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testFSProps() throws Exception {
    String fileName = "some-file.txt";
    createFileWithJunk(fileName, 1234);
    testFSProps(fileName, props -> {
      assertTrue(props.totalSpace() > 0);
      assertTrue(props.unallocatedSpace() > 0);
      assertTrue(props.usableSpace() > 0);
    });
    await();
  }

  private void testFSProps(String fileName,
                           Handler<FileSystemProps> afterOK) throws Exception {
    vertx.fileSystem().fsProps(testDir + pathSep + fileName, ar -> {
      if (ar.failed()) {
        fail(ar.cause().getMessage());
      } else {
        afterOK.handle(ar.result());
        testComplete();
      }
    });
  }

  @Test
  public void testOpenOptions() {
    OpenOptions opts = new OpenOptions();
    assertNull(opts.getPerms());
    String perms = "rwxrwxrwx";
    assertEquals(opts, opts.setPerms(perms));
    assertEquals(perms, opts.getPerms());
    assertTrue(opts.isCreate());
    assertEquals(opts, opts.setCreate(false));
    assertFalse(opts.isCreate());
    assertFalse(opts.isCreateNew());
    assertEquals(opts, opts.setCreateNew(true));
    assertTrue(opts.isCreateNew());
    assertTrue(opts.isRead());
    assertEquals(opts, opts.setRead(false));
    assertFalse(opts.isRead());
    assertTrue(opts.isWrite());
    assertEquals(opts, opts.setWrite(false));
    assertFalse(opts.isWrite());
    assertFalse(opts.isDsync());
    assertEquals(opts, opts.setDsync(true));
    assertTrue(opts.isDsync());
    assertFalse(opts.isSync());
    assertEquals(opts, opts.setSync(true));
    assertTrue(opts.isSync());
    assertFalse(opts.isDeleteOnClose());
    assertEquals(opts, opts.setDeleteOnClose(true));
    assertTrue(opts.isDeleteOnClose());
    assertFalse(opts.isTruncateExisting());
    assertEquals(opts, opts.setTruncateExisting(true));
    assertTrue(opts.isTruncateExisting());
    assertFalse(opts.isSparse());
    assertEquals(opts, opts.setSparse(true));
    assertTrue(opts.isSparse());
  }

  @Test
  public void testDefaultOptionOptions() {
    OpenOptions def = new OpenOptions();
    OpenOptions json = new OpenOptions(new JsonObject());
    assertEquals(def.getPerms(), json.getPerms());
    assertEquals(def.isRead(), json.isRead());
    assertEquals(def.isWrite(), json.isWrite());
    assertEquals(def.isCreate(), json.isCreate());
    assertEquals(def.isCreateNew(), json.isCreateNew());
    assertEquals(def.isDeleteOnClose(), json.isDeleteOnClose());
    assertEquals(def.isTruncateExisting(), json.isTruncateExisting());
    assertEquals(def.isSparse(), json.isSparse());
    assertEquals(def.isSync(), json.isSync());
    assertEquals(def.isDsync(), json.isDsync());
  }

  @Test
  public void testAsyncFileCloseHandlerIsAsync() throws Exception {
    String fileName = "some-file.dat";
    createFileWithJunk(fileName, 100);
    AsyncFile file = vertx.fileSystem().openBlocking(testDir + pathSep + fileName, new OpenOptions());
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    file.close(ar -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testDrainNotCalledAfterClose() throws Exception {
    String fileName = "some-file.dat";
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), onSuccess(file -> {
      Buffer buf = TestUtils.randomBuffer(1024 * 1024);
      file.write(buf);
      AtomicBoolean drainAfterClose = new AtomicBoolean();
      file.drainHandler(v -> {
        drainAfterClose.set(true);
      });
      file.close(ar -> {
        assertFalse(drainAfterClose.get());
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testResumeFileInEndHandler() throws Exception {
    Buffer expected = TestUtils.randomBuffer(10000);
    String fileName = "some-file.dat";
    createFile(fileName, expected.getBytes());
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), onSuccess(file -> {
      Buffer buffer = Buffer.buffer();
      file.endHandler(v -> {
        assertEquals(buffer.length(), expected.length());
        file.pause();
        file.resume();
        complete();
      });
      file.handler(buffer::appendBuffer);
    }));
    await();
  }

  private Handler<AsyncResult<Void>> createHandler(boolean shouldPass, Handler<Void> afterOK) {
    return ar -> {
      if (ar.failed()) {
        if (shouldPass) {
          fail(ar.cause().getMessage());
        } else {
          assertTrue(ar.cause() instanceof FileSystemException);
          if (afterOK != null) {
            afterOK.handle(null);
          }
          testComplete();
        }
      } else {
        if (shouldPass) {
          if (afterOK != null) {
            afterOK.handle(null);
          }
          testComplete();
        } else {
          fail("operation should fail");
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
    createFile(fileName, TestUtils.randomByteArray((int) length));
  }

  private void createFile(String fileName, byte[] bytes) throws Exception {
    File file = new File(testDir, fileName);
    Path path = Paths.get(file.getCanonicalPath());
    Files.write(path, bytes);

    setPerms( path, DEFAULT_FILE_PERMS );
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
    deleteDir(new File(testDir + pathSep + dir));
  }

  private void mkDir(String dirName) throws Exception {
    File dir = new File(testDir + pathSep + dirName);
    dir.mkdir();

    setPerms( Paths.get( dir.getCanonicalPath() ), DEFAULT_DIR_PERMS );
  }

  private long fileLength(String fileName) {
    File file = new File(testDir, fileName);
    return file.length();
  }

  private void setPerms(Path path, String perms) {
    if (Utils.isWindows() == false) {
      try {
        Files.setPosixFilePermissions( path, PosixFilePermissions.fromString(perms) );
      }
      catch(IOException e) {
        throw new RuntimeException(e.getMessage());
      }
    }
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
    File file = new File(testDir + pathSep + fileName);
    file.delete();
  }

  // @Repeat(times=1000)
  @Test
  public void testAsyncFileConcurrency() throws Exception {
    String fileName = "some-file.dat";

    AtomicReference<AsyncFile> arFile = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions(), ar -> {
      if (ar.succeeded()) {
        AsyncFile af = ar.result();
        arFile.set(af);
      } else {
        fail(ar.cause().getMessage());
      }
      latch.countDown();
    });
    awaitLatch(latch);

    AsyncFile af = arFile.get();

    Buffer buff = Buffer.buffer(randomByteArray(4096));
    for (int i = 0; i < 100000; i++) {
      af.write(buff);
    }

    af.close(onSuccess(v -> {
      testComplete();
    }));

    await();
  }

  @Test
  public void testAtomicMove() throws Exception {
    String source = "foo.txt";
    String middle = "baz.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);

    try {
      Files.move(new File(testDir, source).toPath(), new File(testDir, middle).toPath(), StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      throw new AssumptionViolatedException("Atomic move unsupported");
    }

    FileSystem fs = vertx.fileSystem();
    String from = testDir + pathSep + middle;
    String to = testDir + pathSep + target;
    CopyOptions options = new CopyOptions().setAtomicMove(true);

    fs.move(from, to, options, onSuccess(v -> {
      assertFalse(fileExists(middle));
      assertTrue(fileExists(target));
      complete();
    }));
    await();
  }

  @Test
  public void testCopyReplaceExisting() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);

    FileSystem fs = vertx.fileSystem();
    String from = testDir + pathSep + source;
    String to = testDir + pathSep + target;
    CopyOptions options = new CopyOptions().setReplaceExisting(true);

    fs.copy(from, to, options, onSuccess(v -> {
      fs.readFile(from, onSuccess(expected -> {
        fs.readFile(to, onSuccess(actual -> {
          assertEquals(expected, actual);
          complete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testCopyNoReplaceExisting() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);

    FileSystem fs = vertx.fileSystem();
    String from = testDir + pathSep + source;
    String to = testDir + pathSep + target;
    CopyOptions options = new CopyOptions();

    fs.copy(from, to, options, onFailure(t -> {
      assertThat(t, instanceOf(FileSystemException.class));
      assertThat(t.getCause(), instanceOf(FileAlreadyExistsException.class));
      complete();
    }));
    await();
  }

  @Test
  public void testCopyFileAttributes() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);

    try {
      Files.setPosixFilePermissions(new File(testDir, source).toPath(), EnumSet.of(PosixFilePermission.OWNER_READ));
    } catch (UnsupportedOperationException e) {
      throw new AssumptionViolatedException("POSIX file perms unsupported");
    }

    FileSystem fs = vertx.fileSystem();
    String from = testDir + pathSep + source;
    String to = testDir + pathSep + target;
    CopyOptions options = new CopyOptions().setCopyAttributes(false);

    fs.copy(from, to, options, onSuccess(v -> {
      fs.props(from, onSuccess(expected -> {
        fs.props(from, onSuccess(actual -> {
          assertEquals(expected.creationTime(), actual.creationTime());
          assertEquals(expected.lastModifiedTime(), actual.lastModifiedTime());
          vertx.<Set<PosixFilePermission>>executeBlocking(fut -> {
            try {
              fut.complete(Files.getPosixFilePermissions(new File(testDir, target).toPath(), LinkOption.NOFOLLOW_LINKS));
            } catch (IOException e) {
              fut.fail(e);
            }
          }, onSuccess(perms -> {
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ), perms);
            complete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testCopyNoFollowLinks() throws Exception {
    // Symlinks require a modified security policy in Windows. -- See http://stackoverflow.com/questions/23217460/how-to-create-soft-symbolic-link-using-java-nio-files
    Assume.assumeFalse(Utils.isWindows());

    String source = "foo.txt";
    String link = "link.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);

    try {
      Files.createSymbolicLink(new File(testDir, link).toPath(), new File(testDir, source).toPath());
    } catch (UnsupportedOperationException e) {
      throw new AssumptionViolatedException("Links unsupported");
    }

    FileSystem fs = vertx.fileSystem();
    String from = testDir + pathSep + link;
    String to = testDir + pathSep + target;
    CopyOptions options = new CopyOptions().setNofollowLinks(true);

    fs.copy(from, to, options, onSuccess(v -> {
      fs.lprops(to, onSuccess(props -> {
        assertTrue(props.isSymbolicLink());
        fs.readFile(from, onSuccess(expected -> {
          fs.readFile(to, onSuccess(actual -> {
            assertEquals(expected, actual);
            complete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testCreateTempDirectory() {
    FileSystem fs = vertx.fileSystem();
    fs.createTempDirectory("project", onSuccess(tempDirectory -> {
      assertNotNull(tempDirectory);
      assertTrue(Files.exists(Paths.get(tempDirectory)));
      complete();
    }));
    await();
  }

  @Test
  public void testCreateTempDirectoryBlocking() {
    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectoryBlocking("project");
    assertTrue(Files.exists(Paths.get(tempDirectory)));
  }

  @Test
  public void testCreateTempDirectoryWithPerms() {
    Assume.assumeFalse(Utils.isWindows());
    FileSystem fs = vertx.fileSystem();
    fs.createTempDirectory("project", DEFAULT_DIR_PERMS, onSuccess(tempDirectory -> {
      try {
        String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(Paths.get(tempDirectory)));
        assertEquals(perms, DEFAULT_DIR_PERMS);
      } catch (IOException e) {
        fail(e);
      }
      complete();
    }));
    await();
  }

  @Test
  public void testCreateTempDirectoryWithPermsBlocking() throws Exception {
    Assume.assumeFalse(Utils.isWindows());

    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectoryBlocking("project", DEFAULT_DIR_PERMS);
    String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(Paths.get(tempDirectory)));
    assertEquals(perms, DEFAULT_DIR_PERMS);
  }

  @Test
  public void testCreateTempDirectoryWithDirectory() {
    FileSystem fs = vertx.fileSystem();
    fs.createTempDirectory(testDir, "project", null, onSuccess(tempDirectory -> {
      Path path = Paths.get(tempDirectory);
      assertTrue(Files.exists(path));
      assertTrue(path.startsWith(testDir));
      complete();
    }));
    await();
  }

  @Test
  public void testCreateTempDirectoryWithDirectoryBlocking() {
    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectoryBlocking(testDir, "project", null);
    Path path = Paths.get(tempDirectory);
    assertTrue(Files.exists(Paths.get(tempDirectory)));
    assertTrue(path.startsWith(testDir));
  }


  @Test
  public void testCreateTempFile() {
    FileSystem fs = vertx.fileSystem();
    fs.createTempFile("project", ".tmp", onSuccess(tempFile -> {
      assertNotNull(tempFile);
      assertTrue(Files.exists(Paths.get(tempFile)));
      complete();
    }));
    await();
  }

  @Test
  public void testCreateTempFileBlocking() {
    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFileBlocking("project", ".tmp");
    assertNotNull(tempFile);
    assertTrue(Files.exists(Paths.get(tempFile)));
  }

  @Test
  public void testCreateTempFileWithDirectory() {
    FileSystem fs = vertx.fileSystem();
    fs.createTempFile(testDir, "project", ".tmp", null, onSuccess(tempFile -> {
      assertNotNull(tempFile);
      Path path = Paths.get(tempFile);
      assertTrue(Files.exists(path));
      assertTrue(path.startsWith(testDir));
      complete();
    }));
    await();
  }

  @Test
  public void testCreateTempFileWithDirectoryBlocking() {
    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFileBlocking(testDir, "project", ".tmp", null);
    assertNotNull(tempFile);
    Path path = Paths.get(tempFile);
    assertTrue(Files.exists(path));
    assertTrue(path.startsWith(testDir));
  }

  @Test
  public void testCreateTempFileWithPerms() {
    Assume.assumeFalse(Utils.isWindows());

    FileSystem fs = vertx.fileSystem();
    fs.createTempFile("project", ".tmp", DEFAULT_FILE_PERMS, onSuccess(tempFile -> {
      Path path = Paths.get(tempFile);
      assertTrue(Files.exists(path));
      try {
        String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
        assertEquals(perms, DEFAULT_FILE_PERMS);
      } catch (IOException e) {
        fail(e);
      }
    }));
  }


  @Test
  public void testCreateTempFileWithPermsBlocking() throws Exception {
    Assume.assumeFalse(Utils.isWindows());

    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFileBlocking("project", ".tmp", DEFAULT_FILE_PERMS);
    Path path = Paths.get(tempFile);
    assertTrue(Files.exists(path));
    String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    assertEquals(perms, DEFAULT_FILE_PERMS);
  }
}
