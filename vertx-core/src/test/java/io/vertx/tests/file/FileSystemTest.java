/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.file;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.*;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.file.AsyncFileInternal;
import io.vertx.core.internal.streams.WriteResult;
import io.vertx.core.file.impl.AsyncFileImpl;
import io.vertx.core.impl.Utils;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase2;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static io.vertx.test.core.FutureAssert.assertThat;
import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystemTest extends VertxTestBase2 {

  private static final String DEFAULT_DIR_PERMS = "rwxr-xr-x";
  private static final String DEFAULT_FILE_PERMS = "rw-r--r--";

  private String pathSep;
  private String testDir;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Before
  public void before() throws Exception {
    java.nio.file.FileSystem fs = FileSystems.getDefault();
    pathSep = fs.getSeparator();
    File ftestDir = testFolder.newFolder();
    testDir = ftestDir.toString();
  }

  @Test
  public void testIllegalArguments() {
    assertNullPointerException(() -> vertx.fileSystem().copy(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().copy("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().copyBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().copyBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().copyRecursive(null, "ignored", true));
    assertNullPointerException(() -> vertx.fileSystem().copyRecursive("ignored", null, true));
    assertNullPointerException(() -> vertx.fileSystem().copyRecursiveBlocking(null, "ignored", true));
    assertNullPointerException(() -> vertx.fileSystem().copyRecursiveBlocking("ignored", null, true));
    assertNullPointerException(() -> vertx.fileSystem().move(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().move("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().moveBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().moveBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().truncate(null, 0));
    assertNullPointerException(() -> vertx.fileSystem().truncateBlocking(null, 0));
    assertNullPointerException(() -> vertx.fileSystem().chmod(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chmod("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().chmodBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chmodBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().chmodRecursive(null, "ignored", "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chmodRecursive("ignored", null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chmodRecursiveBlocking(null, "ignored", "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chmodRecursiveBlocking("ignored", null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chown(null, "ignored", "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().chownBlocking(null, "ignored", "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().props(null));
    assertNullPointerException(() -> vertx.fileSystem().propsBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().lprops(null));
    assertNullPointerException(() -> vertx.fileSystem().lpropsBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().link(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().link("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().linkBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().linkBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().symlink(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().symlink("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().symlinkBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().symlinkBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().unlink(null));
    assertNullPointerException(() -> vertx.fileSystem().unlinkBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().readSymlink(null));
    assertNullPointerException(() -> vertx.fileSystem().readSymlinkBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().delete(null));
    assertNullPointerException(() -> vertx.fileSystem().deleteBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().deleteRecursive(null));
    assertNullPointerException(() -> vertx.fileSystem().deleteRecursiveBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().mkdir(null));
    assertNullPointerException(() -> vertx.fileSystem().mkdirBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().mkdir(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().mkdirBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().mkdirs(null));
    assertNullPointerException(() -> vertx.fileSystem().mkdirsBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().mkdirs(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().mkdirsBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().readDir(null));
    assertNullPointerException(() -> vertx.fileSystem().readDirBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().readDir(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().readDirBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().readFile(null));
    assertNullPointerException(() -> vertx.fileSystem().readFileBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().writeFile(null, Buffer.buffer()));
    assertNullPointerException(() -> vertx.fileSystem().writeFile("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().writeFileBlocking(null, Buffer.buffer()));
    assertNullPointerException(() -> vertx.fileSystem().writeFileBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().open(null, new OpenOptions()));
    assertNullPointerException(() -> vertx.fileSystem().open("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().openBlocking(null, new OpenOptions()));
    assertNullPointerException(() -> vertx.fileSystem().openBlocking("ignored", null));
    assertNullPointerException(() -> vertx.fileSystem().createFile(null));
    assertNullPointerException(() -> vertx.fileSystem().createFileBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().createFile(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().createFileBlocking(null, "ignored"));
    assertNullPointerException(() -> vertx.fileSystem().exists(null));
    assertNullPointerException(() -> vertx.fileSystem().existsBlocking(null));
    assertNullPointerException(() -> vertx.fileSystem().fsProps(null));
    assertNullPointerException(() -> vertx.fileSystem().fsPropsBlocking(null));

    String fileName = "some-file.dat";
    AsyncFile asyncFile = vertx.fileSystem().openBlocking(testDir + pathSep + fileName, new OpenOptions());

    assertNullPointerException(() -> asyncFile.write(null));
    assertIllegalArgumentException(() -> asyncFile.setWriteQueueMaxSize(1));
    assertIllegalArgumentException(() -> asyncFile.setWriteQueueMaxSize(0));
    assertIllegalArgumentException(() -> asyncFile.setWriteQueueMaxSize(-1));
    assertNullPointerException(() -> asyncFile.write(null, 0));
    assertIllegalArgumentException(() -> asyncFile.write(Buffer.buffer(), -1));

    assertNullPointerException(() -> asyncFile.read(null, 0, 0, 0));
    assertIllegalArgumentException(() -> asyncFile.read(Buffer.buffer(), -1, 0, 0));
    assertIllegalArgumentException(() -> asyncFile.read(Buffer.buffer(), 0, -1, 0));
    assertIllegalArgumentException(() -> asyncFile.read(Buffer.buffer(), 0, 0, -1));
  }

  @Test
  public void testSimpleCopy() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    copy(source, target, false).await();
    Assert.assertTrue(fileExists(source));
    Assert.assertTrue(fileExists(target));
  }

  @Test
  public void testSimpleCopyFileAlreadyExists() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);
    assertFileSystemException(copy(source, target, false));
    Assert.assertTrue(fileExists(source));
    Assert.assertTrue(fileExists(target));
  }

  @Test
  public void testCopyIntoDir() throws Exception {
    String source = "foo.txt";
    String dir = "some-dir";
    String target = dir + pathSep + "bar.txt";
    mkDir(dir);
    createFileWithJunk(source, 100);
    copy(source, target, false).await();
    Assert.assertTrue(fileExists(source));
    Assert.assertTrue(fileExists(target));
  }

  @Test
  public void testCopyEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    mkDir(source);
    copy(source, target, false).await();
    Assert.assertTrue(fileExists(source));
    Assert.assertTrue(fileExists(target));
  }

  @Test
  public void testCopyNonEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    String file1 = pathSep + "somefile.bar";
    mkDir(source);
    createFileWithJunk(source + file1, 100);
    copy(source, target, false).await();
    Assert.assertTrue(fileExists(source));
    Assert.assertTrue(fileExists(target));
    Assert.assertFalse(fileExists(target + file1));
  }

  @Test
  public void testFailCopyDirAlreadyExists() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    mkDir(source);
    mkDir(target);
    assertFileSystemException(copy(source, target, false));
    Assert.assertTrue(fileExists(source));
    Assert.assertTrue(fileExists(target));
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
    copy(dir, target, true).await();
    Assert.assertTrue(fileExists(dir));
    Assert.assertTrue(fileExists(target));
    Assert.assertTrue(fileExists(target + file1));
    Assert.assertTrue(fileExists(target + file2));
    Assert.assertTrue(fileExists(target + pathSep + dir2 + file3));
  }

  private Future<Void> copy(String source, String target, boolean recursive) {
    if (recursive) {
      return vertx.fileSystem().copyRecursive(testDir + pathSep + source, testDir + pathSep + target, true);
    } else {
      return vertx.fileSystem().copy(testDir + pathSep + source, testDir + pathSep + target);
    }
  }

  @Test
  public void testSimpleMove() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    move(source, target).await();
    Assert.assertFalse(fileExists(source));
    Assert.assertTrue(fileExists(target));
  }

  @Test
  public void testSimpleMoveFileAlreadyExists() throws Exception {
    String source = "foo.txt";
    String target = "bar.txt";
    createFileWithJunk(source, 100);
    createFileWithJunk(target, 100);
    assertFileSystemException(move(source, target));
    Assert.assertTrue(fileExists(source));
    Assert.assertTrue(fileExists(target));
  }

  @Test
  public void testMoveEmptyDir() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    mkDir(source);
    move(source, target).await();
    Assert.assertFalse(fileExists(source));
    Assert.assertTrue(fileExists(target));
  }

  @Test
  public void testMoveEmptyDirTargetExists() throws Exception {
    String source = "some-dir";
    String target = "some-other-dir";
    mkDir(source);
    mkDir(target);
    assertFileSystemException(move(source, target));
    Assert.assertTrue(fileExists(source));
    Assert.assertTrue(fileExists(target));
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
    move(dir, target).await();
    Assert.assertFalse(fileExists(dir));
    Assert.assertTrue(fileExists(target));
    Assert.assertTrue(fileExists(target + file1));
    Assert.assertTrue(fileExists(target + file2));
    Assert.assertTrue(fileExists(target + pathSep + dir2 + file3));
  }

  private Future<Void> move(String source, String target) {
    return vertx.fileSystem().move(testDir + pathSep + source, testDir + pathSep + target);
  }

  @Test
  public void testTruncate() throws Exception {
    String file1 = "some-file.dat";
    long initialLen = 1000;
    long truncatedLen = 534;
    createFileWithJunk(file1, initialLen);
    Assert.assertEquals(initialLen, fileLength(file1));
    truncate(file1, truncatedLen).await();
    Assert.assertEquals(truncatedLen, fileLength(file1));
  }

  @Test
  public void testTruncateExtendsFile() throws Exception {
    String file1 = "some-file.dat";
    long initialLen = 500;
    long truncatedLen = 1000;
    createFileWithJunk(file1, initialLen);
    Assert.assertEquals(initialLen, fileLength(file1));
    truncate(file1, truncatedLen).await();
    Assert.assertEquals(truncatedLen, fileLength(file1));
  }

  @Test
  public void testTruncateFileDoesNotExist() {
    String file1 = "some-file.dat";
    long truncatedLen = 534;
    assertFileSystemException(truncate(file1, truncatedLen));
  }

  private Future<Void> truncate(String file, long truncatedLen) {
    return vertx.fileSystem().truncate(testDir + pathSep + file, truncatedLen);
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
    chmod(file1, perms, null).await();
    assertPerms(perms, file1);
    deleteFile(file1);
  }

  private void assertPerms(String perms, String file1) {
    if (!Utils.isWindows()) {
      Assert.assertEquals(perms, getPerms(file1));
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
    chmod(dir, perms, dirPerms).await();
    assertPerms(dirPerms, dir);
    assertPerms(perms, dir + file1);
    assertPerms(perms, dir + file2);
    assertPerms(dirPerms, dir + pathSep + dir2);
    assertPerms(perms, dir + pathSep + dir2 + file3);
    deleteDir(dir);
  }

  @Test
  public void testChownToRootFails() throws Exception {
    Assume.assumeFalse("Running as root, chown to root will succeed", "root".equals(System.getProperty("user.name")));
    testChownFails("root");
  }

  @Test
  public void testChownToNotExistingUserFails() throws Exception {
    testChownFails("jfhfhjejweg");
  }

  private void testChownFails(String user) throws Exception {
    String file1 = "some-file.dat";
    createFileWithJunk(file1, 100);
    assertThat(vertx.fileSystem().chown(testDir + pathSep + file1, user, null))
      .isFailure()
      .await();
    deleteFile(file1);
  }

  @Test
  public void testChownToOwnUser() throws Exception {
    String file1 = "some-file.dat";
    createFileWithJunk(file1, 100);
    String fullPath = testDir + pathSep + file1;
    Path path = Paths.get(fullPath);
    UserPrincipal owner = Files.getOwner(path);
    String user = owner.getName();
    vertx.fileSystem().chown(fullPath, user, null).await();
    deleteFile(file1);
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
    vertx.fileSystem().chown(fullPath, null, group.getName()).await();
    deleteFile(file1);
  }

  private Future<Void> chmod(String file, String perms, String dirPerms) {
    if (Files.isDirectory(Paths.get(testDir + pathSep + file))) {
      assertPerms(DEFAULT_DIR_PERMS, file);
    } else {
      assertPerms(DEFAULT_FILE_PERMS, file);
    }
    if (dirPerms != null) {
      return vertx.fileSystem().chmodRecursive(testDir + pathSep + file, perms, dirPerms);
    } else {
      return vertx.fileSystem().chmod(testDir + pathSep + file, perms);
    }
  }

  @Test
  public void testProps() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;

    // The times are quite inaccurate so we give 1 second leeway
    long start = 1000 * (System.currentTimeMillis() / 1000 - 1);
    createFileWithJunk(fileName, fileSize);

    FileProps st = vertx.fileSystem().props(testDir + pathSep + fileName).await();
    Assert.assertNotNull(st);
    Assert.assertEquals(fileSize, st.size());
    Assert.assertTrue(st.creationTime() >= start);
    Assert.assertTrue(st.lastAccessTime() >= start);
    Assert.assertTrue(st.lastModifiedTime() >= start);
    Assert.assertFalse(st.isDirectory());
    Assert.assertTrue(st.isRegularFile());
    Assert.assertFalse(st.isSymbolicLink());
  }

  @Test
  public void testPropsFileDoesNotExist() {
    String fileName = "some-file.txt";
    assertFileSystemException(vertx.fileSystem().props(testDir + pathSep + fileName));
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

    FileProps st = vertx.fileSystem().props(testDir + pathSep + linkName).await();
    Assert.assertNotNull(st);
    Assert.assertEquals(fileSize, st.size());
    Assert.assertTrue(st.creationTime() >= start);
    Assert.assertTrue(st.creationTime() <= end);
    Assert.assertTrue(st.lastAccessTime() >= start);
    Assert.assertTrue(st.lastAccessTime() <= end);
    Assert.assertTrue(st.lastModifiedTime() >= start);
    Assert.assertTrue(st.lastModifiedTime() <= end);
    Assert.assertFalse(st.isDirectory());
    Assert.assertFalse(st.isOther());
    Assert.assertTrue(st.isRegularFile());
    Assert.assertFalse(st.isSymbolicLink());
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
    FileProps st = vertx.fileSystem().lprops(testDir + pathSep + linkName).await();
    Assert.assertNotNull(st);
    Assert.assertTrue(st.isSymbolicLink());
  }

  @Test
  public void testLink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String linkName = "some-link.txt";
    vertx.fileSystem().link(testDir + pathSep + linkName, testDir + pathSep + fileName).await();
    Assert.assertEquals(fileSize, fileLength(linkName));
    Assert.assertFalse(Files.isSymbolicLink(Paths.get(testDir + pathSep + linkName)));
  }

  @Test
  public void testSymLink() throws Exception {
    // Symlinks require a modified security policy in Windows. -- See http://stackoverflow.com/questions/23217460/how-to-create-soft-symbolic-link-using-java-nio-files
    Assume.assumeFalse(Utils.isWindows());
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String symlinkName = "some-sym-link.txt";
    // Symlink is relative
    vertx.fileSystem().symlink(testDir + pathSep + symlinkName, fileName).await();
    Assert.assertEquals(fileSize, fileLength(symlinkName));
    Assert.assertTrue(Files.isSymbolicLink(Paths.get(testDir + pathSep + symlinkName)));
    // Now try reading it
    String read = vertx.fileSystem().readSymlinkBlocking(testDir + pathSep + symlinkName);
    Assert.assertEquals(fileName, read);
  }

  @Test
  public void testUnlink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String linkName = "some-link.txt";
    Files.createLink(Paths.get(testDir + pathSep + linkName), Paths.get(testDir + pathSep + fileName));
    Assert.assertEquals(fileSize, fileLength(linkName));
    vertx.fileSystem().unlink(testDir + pathSep + linkName).await();
    Assert.assertFalse(fileExists(linkName));
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
    Assert.assertEquals(fileName, vertx.fileSystem().readSymlink(testDir + pathSep + linkName).await());
  }

  @Test
  public void testSimpleDelete() throws Exception {
    String fileName = "some-file.txt";
    createFileWithJunk(fileName, 100);
    Assert.assertTrue(fileExists(fileName));
    delete(fileName, false).await();
    Assert.assertFalse(fileExists(fileName));
  }

  @Test
  public void testDeleteEmptyDir() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    Assert.assertTrue(fileExists(dirName));
    delete(dirName, false).await();
    Assert.assertFalse(fileExists(dirName));
  }

  @Test
  public void testDeleteNonExistent() {
    String dirName = "some-dir";
    Assert.assertFalse(fileExists(dirName));
    assertFileSystemException(delete(dirName, false));
  }

  @Test
  public void testDeleteNonEmptyFails() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    String file1 = "some-file.txt";
    createFileWithJunk(dirName + pathSep + file1, 100);
    assertFileSystemException(delete(dirName, false));
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
    delete(dir, true).await();
    Assert.assertFalse(fileExists(dir));
  }

  private Future<Void> delete(String fileName, boolean recursive) {
    if (recursive) {
      return vertx.fileSystem().deleteRecursive(testDir + pathSep + fileName);
    } else {
      return vertx.fileSystem().delete(testDir + pathSep + fileName);
    }
  }

  @Test
  public void testMkdirSimple() {
    String dirName = "some-dir";
    mkdir(dirName, null, false).await();
    Assert.assertTrue(fileExists(dirName));
    Assert.assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
  }

  @Test
  public void testMkdirWithParentsFails() {
    String dirName = "top-dir" + pathSep + "some-dir";
    assertFileSystemException(mkdir(dirName, null, false));
  }

  @Test
  public void testMkdirWithPerms() {
    String dirName = "some-dir";
    String perms = "rwx--x--x";
    mkdir(dirName, perms, false).await();
    Assert.assertTrue(fileExists(dirName));
    Assert.assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
    assertPerms(perms, dirName);
  }

  @Test
  public void testMkdirAlreadyExist() {
    String dirName = "some-dir";
    mkdir(dirName, null, false).await();
    assertFileSystemException(mkdir(dirName, null, false));
    Assert.assertTrue(fileExists(dirName));
    Assert.assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
  }

  @Test
  public void testMkdirCreateParents() {
    String dirName = "top-dir" + pathSep + "/some-dir";
    mkdir(dirName, null, true).await();
    Assert.assertTrue(fileExists(dirName));
    Assert.assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
  }

  @Test
  public void testMkdirCreateParentsWithPerms() {
    String dirName = "top-dir" + pathSep + "/some-dir";
    String perms = "rwx--x--x";
    mkdir(dirName, perms, true).await();
    Assert.assertTrue(fileExists(dirName));
    Assert.assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
    assertPerms(perms, dirName);
  }

  @Test
  public void testMkdirCreateParentsDirExist() {
    String dirName = "some-dir";
    mkdir(dirName, null, true).await();
    mkdir(dirName, null, true).await();
    Assert.assertTrue(fileExists(dirName));
    Assert.assertTrue(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
  }

  @Test
  public void testMkdirCreateParentsFileExist() throws Exception {
    String dirName = "some-dir";
    createFileWithJunk(dirName, 1024);
    assertFileSystemException(mkdir(dirName, null, true));
    Assert.assertTrue(fileExists(dirName));
    Assert.assertFalse(Files.isDirectory(Paths.get(testDir + pathSep + dirName)));
  }

  private Future<Void> mkdir(String dirName, String perms, boolean createParents) {
    if (createParents) {
      if (perms != null) {
        return vertx.fileSystem().mkdirs(testDir + pathSep + dirName, perms);
      } else {
        return vertx.fileSystem().mkdirs(testDir + pathSep + dirName);
      }
    } else {
      if (perms != null) {
        return vertx.fileSystem().mkdir(testDir + pathSep + dirName, perms);
      } else {
        return vertx.fileSystem().mkdir(testDir + pathSep + dirName);
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
    List<String> fileNames = vertx.fileSystem().readDir(testDir + pathSep + dirName).await();
    Assert.assertEquals(numFiles, fileNames.size());
    Set<String> fset = new HashSet<>(fileNames);
    String root = new File(testDir + pathSep + dirName).getCanonicalPath();
    for (int i = 0; i < numFiles; i++) {
      Assert.assertTrue(fset.contains(root + pathSep + "file-" + i + ".dat"));
    }
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
    List<String> fileNames = vertx.fileSystem().readDir(testDir + pathSep + dirName, "foo.+").await();
    Assert.assertEquals(numFiles, fileNames.size());
    Set<String> fset = new HashSet<>(fileNames);
    String root = new File(testDir + pathSep + dirName).getCanonicalPath();
    for (int i = 0; i < numFiles; i++) {
      Assert.assertTrue(fset.contains(root + pathSep + "foo-" + i + ".txt"));
    }
  }

  @Test
  public void testReadFile() throws Exception {
    byte[] content = TestUtils.randomByteArray(1000);
    String fileName = "some-file.dat";
    createFile(fileName, content);
    Buffer buff = vertx.fileSystem().readFile(testDir + pathSep + fileName).await();
    Assert.assertEquals(Buffer.buffer(content), buff);
  }

  @Test
  public void testWriteFile() throws Exception {
    byte[] content = TestUtils.randomByteArray(1000);
    Buffer buff = Buffer.buffer(content);
    String fileName = "some-file.dat";
    vertx.fileSystem().writeFile(testDir + pathSep + fileName, buff).await();
    Assert.assertTrue(fileExists(fileName));
    Assert.assertEquals(content.length, fileLength(fileName));
    Assert.assertEquals(buff, readFile(fileName));
  }

  @Test
  public void testWriteAsync() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    Buffer buff = Buffer.buffer(content);
    AsyncFile file = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    List<Future<Void>> writes = new ArrayList<>();
    for (int i = 0; i < chunks; i++) {
      Buffer chunk = buff.getBuffer(i * chunkSize, (i + 1) * chunkSize);
      Assert.assertEquals(chunkSize, chunk.length());
      writes.add(file.write(chunk, i * chunkSize));
    }
    Future.all(writes).await();
    file.close().await();
    Assert.assertTrue(fileExists(fileName));
    Assert.assertEquals(buff, readFile(fileName));
  }

  @Test
  public void testCloseFileAfterFailure() {
    if (!vertx.fileSystem().existsBlocking("/dev/full")) {
      throw new AssumptionViolatedException("/dev/full special device file is not available");
    }
    AsyncFile asyncFile = vertx.fileSystem().openBlocking("/dev/full", new OpenOptions());

    int loops = 100;
    List<Future<Throwable>> writes = new ArrayList<>();

    for (int i = 0; i < loops; i++) {
      writes.add(assertThat(asyncFile.write(TestUtils.randomBuffer(256))).isFailure());
    }
    asyncFile.close().await();
    Future.all(writes).await();
  }

  @Test
  public void testWriteEmptyAsync() {
    String fileName = "some-file.dat";
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .compose(file -> file.write(Buffer.buffer(), 0))
      .await();
  }

  @Test
  public void testReadAsync() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    Buffer expected = Buffer.buffer(content);
    createFile(fileName, content);
    AsyncFile file = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    Buffer buff = Buffer.buffer(chunks * chunkSize);
    List<Future<Buffer>> reads = new ArrayList<>();
    for (int i = 0; i < chunks; i++) {
      reads.add(file.read(buff, i * chunkSize, i * chunkSize, chunkSize));
    }
    Future.all(reads).await();
    file.close().await();
    Assert.assertEquals(expected, buff);
    for (Future<Buffer> read : reads) {
      Assert.assertEquals(buff, read.result());
    }
  }

  @Test
  public void testWriteStream() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    Buffer buff = Buffer.buffer(content);
    AsyncFile ws = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    ws.exceptionHandler(t -> Assert.fail(t.getMessage()));
    List<Future<Void>> writes = new ArrayList<>();
    for (int i = 0; i < chunks; i++) {
      Buffer chunk = buff.getBuffer(i * chunkSize, (i + 1) * chunkSize);
      Assert.assertEquals(chunkSize, chunk.length());
      writes.add(ws.write(chunk));
    }
    ws.close().await();
    Future.all(writes).await();
    Assert.assertTrue(fileExists(fileName));
    Assert.assertEquals(buff, readFile(fileName));
  }

  @Test
  public void testWritePosAdvancesAtSubmission() {
    String fileName = "some-file.dat";
    Buffer chunk = Buffer.buffer(TestUtils.randomByteArray(100));
    AsyncFile file = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    Assert.assertEquals(0, file.getWritePos());
    // The write position advances when the write is submitted, before it completes
    Future<Void> write1 = file.write(chunk);
    Assert.assertEquals(100, file.getWritePos());
    Future<Void> write2 = file.write(chunk);
    Assert.assertEquals(200, file.getWritePos());
    file.close().await();
    write1.await();
    write2.await();
  }

  @Test
  public void testSetWritePosRepositionsSubsequentWrites() throws Exception {
    String fileName = "some-file.dat";
    AsyncFile file = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    // Sequence the overlapping writes: concurrent I/O on overlapping regions has no defined order
    file.write(Buffer.buffer("aaaa")).await();
    file.setWritePos(1);
    file.write(Buffer.buffer("bb")).await();
    Assert.assertEquals(3, file.getWritePos());
    file.close().await();
    byte[] readBytes = Files.readAllBytes(Paths.get(testDir + pathSep + fileName));
    Assert.assertEquals("abba", Buffer.buffer(readBytes).toString());
  }

  @Test
  public void testPositionalWriteDoesNotAdvanceWritePos() throws Exception {
    String fileName = "some-file.dat";
    AsyncFile file = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    file.write(Buffer.buffer("aa")).await();
    Assert.assertEquals(2, file.getWritePos());
    // The explicit-position overload does not touch the stream write position
    file.write(Buffer.buffer("cc"), 4).await();
    Assert.assertEquals(2, file.getWritePos());
    file.write(Buffer.buffer("aa")).await();
    Assert.assertEquals(4, file.getWritePos());
    file.close().await();
    byte[] readBytes = Files.readAllBytes(Paths.get(testDir + pathSep + fileName));
    Assert.assertEquals("aaaacc", Buffer.buffer(readBytes).toString());
  }

  @Test
  public void testWritePosInitializedAtSizeOnAppend() throws Exception {
    String fileName = "some-file.dat";
    createFile(fileName, TestUtils.randomByteArray(123));
    AsyncFile file = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions().setAppend(true)).await();
    Assert.assertEquals(123, file.getWritePos());
    file.close().await();
  }

  @Test
  public void testWriteAfterCloseFails() {
    String fileName = "some-file.dat";
    AsyncFile file = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    Future<Void> write = file.write(Buffer.buffer("aa"));
    file.close().await();
    write.await();
    try {
      file.write(Buffer.buffer("bb"));
      Assert.fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWriteResult(Checkpoint checkpoint) {
    String fileName = "some-file.dat";
    Buffer chunk = Buffer.buffer(TestUtils.randomByteArray(1024));
    AsyncFileInternal file = (AsyncFileInternal) vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    while (true) {
      WriteResult result = file.write(chunk);
      if (!result.isWritable()) {
        result.drainHandler(v -> {
          checkpoint.succeed();
        });
        break;
      }
    }
  }

  @Test
  public void testWriteResultCallerContext(Checkpoint checkpoint) {
    String fileName = "some-file.dat";
    Buffer chunk = Buffer.buffer(TestUtils.randomByteArray(1024));
    AsyncFileInternal file = (AsyncFileInternal) vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    // The result is bound to the caller context, not the file context
    Context writerContext = ((VertxInternal) vertx).createEventLoopContext();
    writerContext.runOnContext(v -> {
      List<WriteResult> results = new ArrayList<>();
      while (true) {
        WriteResult current = file.write(chunk);
        results.add(current);
        if (!current.isWritable()) {
          CountDownLatch latch = checkpoint.asLatch(1 + results.size());
          Runnable check = () -> {
            Assert.assertSame(writerContext, Vertx.currentContext());
            latch.countDown();
          };
          for (WriteResult result : results) {
            result.onComplete(ar -> {
              check.run();
            });
          }
          current.drainHandler(v2 -> {
            check.run();
          });
          break;
        }
      }
    });
  }

  @Test
  public void testWriteResultFlushedOnClose() throws Exception {
    String fileName = "some-file.dat";
    Buffer chunk = Buffer.buffer(TestUtils.randomByteArray(1024));
    AsyncFileInternal file = (AsyncFileInternal) vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    List<Future<Void>> results = new ArrayList<>();
    for (int i = 0;i < 32;i++) {
      results.add(file.write(chunk));
    }
    // Close flushes the queued writes: every result succeeds
    file.close().await();
    Future.all(results).await();
    Assert.assertEquals(32 * 1024, Files.readAllBytes(Paths.get(testDir + pathSep + fileName)).length);
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
    AsyncFile ws = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions().setAppend(true)).await();
    ws.exceptionHandler(t -> Assert.fail(t.getMessage()));
    List<Future<Void>> writes = new ArrayList<>();
    for (int i = 0; i < chunks; i++) {
      Buffer chunk = buff.getBuffer(i * chunkSize, (i + 1) * chunkSize);
      Assert.assertEquals(chunkSize, chunk.length());
      writes.add(ws.write(chunk));
    }
    ws.close().await();
    Future.all(writes).await();
    Assert.assertTrue(fileExists(fileName));
    Assert.assertEquals(Buffer.buffer(existing).appendBuffer(buff), readFile(fileName));
  }

  @Test
  public void testWriteStreamWithCompositeBuffer() throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content1 = TestUtils.randomByteArray(chunkSize * (chunks / 2));
    byte[] content2 = TestUtils.randomByteArray(chunkSize * (chunks / 2));
    ByteBuf byteBuf = Unpooled.wrappedBuffer(content1, content2);
    Buffer buff = BufferInternal.buffer(byteBuf);
    AsyncFile ws = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    ws.exceptionHandler(t -> Assert.fail(t.getMessage()));
    Future<Void> write = ws.write(buff);
    ws.close().await();
    write.await();
    Assert.assertTrue(fileExists(fileName));
    Assert.assertEquals(buff, readFile(fileName));
    byteBuf.release();
  }

  enum ReadStrategy {

    NONE {
      @Override void init(ReadStream<Buffer> stream) { }
      @Override Future<Void> handle(ReadStream<Buffer> stream) { return Future.succeededFuture(); }
    },

    FLOWING {
      @Override
      void init(ReadStream<Buffer> stream) {
        flowing.set(true);
      }

      @Override
      Future<Void> handle(ReadStream<Buffer> stream) {
        Promise<Void> fut = Promise.promise();
        assert flowing.getAndSet(false);
        stream.pause();
        Vertx.currentContext().owner().setTimer(1, id -> {
          assert !flowing.getAndSet(true);
          stream.resume();
          fut.complete();
        });
        return fut.future();
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
      Future<Void> handle(ReadStream<Buffer> stream) {
        Promise<Void> fut = Promise.promise();
        assert fetching.getAndSet(false);
        Vertx.currentContext().owner().setTimer(1, id -> {
          assert !fetching.getAndSet(true);
          stream.fetch(1);
          fut.complete();
        });
        return fut.future();
      }
    };

    final static AtomicBoolean flowing = new AtomicBoolean();
    final static AtomicBoolean fetching = new AtomicBoolean();

    abstract void init(ReadStream<Buffer> stream);
    abstract Future<Void> handle(ReadStream<Buffer> stream);

  }

  @Test
  public void testReadStream(Checkpoint checkpoint) throws Exception {
    testReadStream(checkpoint, ReadStrategy.NONE);
  }

  @Test
  public void testReadStreamFlowing(Checkpoint checkpoint) throws Exception {
    testReadStream(checkpoint, ReadStrategy.FLOWING);
  }

  @Test
  public void testReadStreamFetch(Checkpoint checkpoint) throws Exception {
    testReadStream(checkpoint, ReadStrategy.FETCH);
  }

  private void testReadStream(Checkpoint checkpoint, ReadStrategy strategy) throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    createFile(fileName, content);
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(rs -> {
        AtomicInteger inProgress = new AtomicInteger();
        AtomicBoolean ended = new AtomicBoolean();
        Buffer buff = Buffer.buffer();
        Runnable checkEnd = () -> {
          if (ended.get() && inProgress.get() == 0) {
            rs
              .close()
              .peek(v -> Assert.assertEquals(Buffer.buffer(content), buff))
              .onComplete(checkpoint);
          }
        };
        strategy.init(rs);
        rs.handler(chunk -> {
          buff.appendBuffer(chunk);
          inProgress.incrementAndGet();
          Future<Void> fut = strategy.handle(rs);
          fut.onComplete(v -> {
            inProgress.decrementAndGet();
            checkEnd.run();
          });
        });
        rs.exceptionHandler(t -> Assert.fail(t.getMessage()));
        rs.endHandler(v -> {
          ended.set(true);
          checkEnd.run();
        });
      })
      .await();
  }

  @Test
  public void testReadStreamWithBufferSize(Checkpoint checkpoint) throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 16384;
    int chunks = 1;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    createFile(fileName, content);
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(rs -> {
        rs.setReadBufferSize(chunkSize);
        Buffer buff = Buffer.buffer();
        int[] callCount = {0};
        rs.handler((buff1) -> {
          buff.appendBuffer(buff1);
          callCount[0]++;
        });
        rs.exceptionHandler(t -> Assert.fail(t.getMessage()));
        rs.endHandler(v -> {
          rs
            .close()
            .peek(v2 -> {
              Assert.assertEquals(1, callCount[0]);
              Assert.assertEquals(Buffer.buffer(content), buff);
            })
            .onComplete(checkpoint);
        });
      })
      .await();
  }

  @Test
  public void testReadStreamSetReadPos(Checkpoint checkpoint) throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    createFile(fileName, content);
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(rs -> {
        rs.setReadPos(chunkSize * chunks / 2);
        Buffer buff = Buffer.buffer();
        rs.handler(buff::appendBuffer);
        rs.exceptionHandler(t -> Assert.fail(t.getMessage()));
        rs.endHandler(v -> {
          rs
            .close()
            .peek(v2 -> {
              Assert.assertEquals(chunkSize * chunks / 2, buff.length());
              byte[] lastHalf = new byte[chunkSize * chunks / 2];
              System.arraycopy(content, chunkSize * chunks / 2, lastHalf, 0, chunkSize * chunks / 2);
              Assert.assertEquals(Buffer.buffer(lastHalf), buff);
            })
            .onComplete(checkpoint);
        });
      })
      .await();
  }

  @Test
  public void testReadStreamSetReadLength(Checkpoint checkpoint) throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    int readLength = chunkSize * chunks / 3;
    createFile(fileName, content);
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(rs -> {
        rs.setReadLength(readLength);
        Buffer buff = Buffer.buffer();
        rs.handler(buff::appendBuffer);
        rs.exceptionHandler(t -> Assert.fail(t.getMessage()));
        rs.endHandler(v -> {
          rs
            .close()
            .peek(v2 -> {
              Assert.assertEquals(readLength, buff.length());
              byte[] firstThird = new byte[readLength];
              System.arraycopy(content, 0, firstThird, 0, readLength);
              Assert.assertEquals(Buffer.buffer(firstThird), buff);
            })
            .onComplete(checkpoint);
        });
      })
      .await();
  }

  @Test
  public void testReadStreamSetReadPosReadLengthBufferSize(Checkpoint checkpoint) throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 1000;
    int chunks = 10;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    int readLength = chunkSize * chunks / 3;
    int readPos = chunkSize * chunks / 3;
    int readBufferSize = 1000;
    int numOfReads = readLength / readBufferSize + (readLength % readBufferSize > 0? 1 : 0);
    createFile(fileName, content);
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(rs -> {
        rs.setReadPos(readPos);
        rs.setReadLength(readLength);
        rs.setReadBufferSize(readBufferSize);
        final Buffer buff = Buffer.buffer();
        final int[] appendCount = new int[] {0};
        rs.handler((rsBuff) -> {
          buff.appendBuffer(rsBuff);
          appendCount[0]++;
        });
        rs.exceptionHandler(t -> Assert.fail(t.getMessage()));
        rs.endHandler(v -> {
          rs
            .close()
            .peek(v2 -> {
              Assert.assertEquals(buff.length(), readLength);
              Assert.assertEquals(numOfReads, appendCount[0]);
              byte[] middleThird = new byte[readLength];
              System.arraycopy(content, readPos, middleThird, 0, readLength);
              Assert.assertEquals(Buffer.buffer(middleThird), buff);
            })
            .onComplete(checkpoint);
        });
      })
      .await();
  }

  @Test
  public void testReadStreamNoLock(Checkpoint checkpoint) throws Exception {
    String fileName = "some-file.dat";
    int chunkSize = 16384;
    int chunks = 1;
    byte[] content = TestUtils.randomByteArray(chunkSize * chunks);
    createFile(fileName, content);
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(rs -> {
        rs.handler(buff -> {
          Assert.assertFalse(Thread.holdsLock(rs));
        });
        rs.endHandler(v -> {
          Assert.assertFalse(Thread.holdsLock(rs));
          checkpoint.succeed();
        });
      })
      .await();
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

    AsyncFile rs = vertx.fileSystem().open(testDir + pathSep + fileName1, new OpenOptions()).await();
    //Open file for writing
    AsyncFile ws = vertx.fileSystem().open(testDir + pathSep + fileName2, new OpenOptions()).await();
    rs.pipeTo(ws).await();
    Assert.assertTrue(fileExists(fileName2));
    Assert.assertEquals(Buffer.buffer(content), readFile(fileName2));
  }

  @Test
  public void testCreateFileNoPerms() {
    testCreateFile(null);
  }

  @Test
  public void testCreateFileWithPerms() {
    testCreateFile("rwx------");
  }

  @Test
  public void testCreateFileAlreadyExists() throws Exception {
    String fileName = "some-file.dat";
    createFileWithJunk(fileName, 100);
    assertFileSystemException(vertx.fileSystem().createFile(testDir + pathSep + fileName));
  }

  private void testCreateFile(String perms) {
    String fileName = "some-file.dat";
    if (perms != null) {
      vertx.fileSystem().createFile(testDir + pathSep + fileName, perms).await();
    } else {
      vertx.fileSystem().createFile(testDir + pathSep + fileName).await();
    }
    Assert.assertTrue(fileExists(fileName));
    Assert.assertEquals(0, fileLength(fileName));
    if (perms != null) {
      assertPerms(perms, fileName);
    }
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
    Assert.assertEquals(exists, vertx.fileSystem().exists(testDir + pathSep + fileName).await());
  }

  @Test
  public void testFSProps() throws Exception {
    String fileName = "some-file.txt";
    createFileWithJunk(fileName, 1234);
    FileSystemProps props = vertx.fileSystem().fsProps(testDir + pathSep + fileName).await();
    Assert.assertNotNull(props.name());
    Assert.assertTrue(props.totalSpace() > 0);
    Assert.assertTrue(props.unallocatedSpace() > 0);
    Assert.assertTrue(props.usableSpace() > 0);
  }

  @Test
  public void testOpenOptions() {
    OpenOptions opts = new OpenOptions();
    Assert.assertNull(opts.getPerms());
    String perms = "rwxrwxrwx";
    Assert.assertEquals(opts, opts.setPerms(perms));
    Assert.assertEquals(perms, opts.getPerms());
    Assert.assertTrue(opts.isCreate());
    Assert.assertEquals(opts, opts.setCreate(false));
    Assert.assertFalse(opts.isCreate());
    Assert.assertFalse(opts.isCreateNew());
    Assert.assertEquals(opts, opts.setCreateNew(true));
    Assert.assertTrue(opts.isCreateNew());
    Assert.assertTrue(opts.isRead());
    Assert.assertEquals(opts, opts.setRead(false));
    Assert.assertFalse(opts.isRead());
    Assert.assertTrue(opts.isWrite());
    Assert.assertEquals(opts, opts.setWrite(false));
    Assert.assertFalse(opts.isWrite());
    Assert.assertFalse(opts.isDsync());
    Assert.assertEquals(opts, opts.setDsync(true));
    Assert.assertTrue(opts.isDsync());
    Assert.assertFalse(opts.isSync());
    Assert.assertEquals(opts, opts.setSync(true));
    Assert.assertTrue(opts.isSync());
    Assert.assertFalse(opts.isDeleteOnClose());
    Assert.assertEquals(opts, opts.setDeleteOnClose(true));
    Assert.assertTrue(opts.isDeleteOnClose());
    Assert.assertFalse(opts.isTruncateExisting());
    Assert.assertEquals(opts, opts.setTruncateExisting(true));
    Assert.assertTrue(opts.isTruncateExisting());
    Assert.assertFalse(opts.isSparse());
    Assert.assertEquals(opts, opts.setSparse(true));
    Assert.assertTrue(opts.isSparse());
  }

  @Test
  public void testDefaultOptionOptions() {
    OpenOptions def = new OpenOptions();
    OpenOptions json = new OpenOptions(new JsonObject());
    Assert.assertEquals(def.getPerms(), json.getPerms());
    Assert.assertEquals(def.isRead(), json.isRead());
    Assert.assertEquals(def.isWrite(), json.isWrite());
    Assert.assertEquals(def.isCreate(), json.isCreate());
    Assert.assertEquals(def.isCreateNew(), json.isCreateNew());
    Assert.assertEquals(def.isDeleteOnClose(), json.isDeleteOnClose());
    Assert.assertEquals(def.isTruncateExisting(), json.isTruncateExisting());
    Assert.assertEquals(def.isSparse(), json.isSparse());
    Assert.assertEquals(def.isSync(), json.isSync());
    Assert.assertEquals(def.isDsync(), json.isDsync());
  }

  @Test
  public void testAsyncFileCloseHandlerIsAsync(Checkpoint checkpoint) throws Exception {
    String fileName = "some-file.dat";
    createFileWithJunk(fileName, 100);
    AsyncFile file = vertx.fileSystem().openBlocking(testDir + pathSep + fileName, new OpenOptions());
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    file.close().onComplete(ar -> {
      Assert.assertNull(stack.get());
      Assert.assertTrue(Vertx.currentContext().isEventLoopContext());
      checkpoint.succeed();
    });
  }

  @Test
  public void testDrainNotCalledAfterClose() {
    String fileName = "some-file.dat";
    AtomicBoolean drainAfterClose = new AtomicBoolean();
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .compose(file -> {
        Buffer buf = TestUtils.randomBuffer(1024 * 1024);
        Future<Void> write = file.write(buf);
        file.drainHandler(v -> {
          drainAfterClose.set(true);
        });
        return Future.all(write, file.close());
      })
      .await();
    Assert.assertFalse(drainAfterClose.get());
  }

  @Test
  public void testDrainSetOnce(Checkpoint checkpoint) {
    String fileName = "some-file.dat";
    Buffer buff = TestUtils.randomBuffer(1024);
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(file -> {
        file.setWriteQueueMaxSize(1024 * 4);
        Context ctx = Vertx.currentContext();
        Assert.assertNotNull(ctx);
        AtomicInteger times = new AtomicInteger(7);
        file.drainHandler(v -> {
          Assert.assertSame(ctx, Vertx.currentContext());
          if (times.decrementAndGet() > 0) {
            while (!file.writeQueueFull()) {
              file.write(buff);
            }
          } else {
            Assert.assertEquals(0, times.get());
            checkpoint.succeed();
          }
        });
        while (!file.writeQueueFull()) {
          file.write(buff);
        }
      })
      .await();
  }

  @Test
  public void testResumeFileInEndHandler(Checkpoint checkpoint) throws Exception {
    Buffer expected = TestUtils.randomBuffer(10000);
    String fileName = "some-file.dat";
    createFile(fileName, expected.getBytes());
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(file -> {
        Buffer buffer = Buffer.buffer();
        file.endHandler(v -> {
          Assert.assertEquals(buffer.length(), expected.length());
          file.pause();
          file.resume();
          checkpoint.succeed();
        });
        file.handler(buffer::appendBuffer);
      })
      .await();
  }

  @Test
  public void testPausedEnd(Checkpoint checkpoint) throws Exception {
    String fileName = "some-file.dat";
    createFile(fileName, new byte[0]);
    AtomicBoolean paused = new AtomicBoolean(false);
    vertx.fileSystem()
      .open(testDir + pathSep + fileName, new OpenOptions())
      .peek(file -> {
        Buffer buffer = Buffer.buffer();
        paused.set(true);
        file.pause();
        vertx.setTimer(100, id -> {
          paused.set(false);
          file.resume();
        });
        file.endHandler(v -> {
          Assert.assertFalse(paused.get());
          checkpoint.succeed();
        });
        file.handler(buffer::appendBuffer);
      })
      .await();
  }

  private static void assertFileSystemException(Future<?> future) {
    assertThat(future)
      .isFailure()
      .asInstanceOf(FileSystemException.class)
      .await();
  }

  // Helper methods

  private boolean fileExists(String fileName) {
    File file = new File(testDir, fileName);
    return file.exists();
  }

  private Buffer readFile(String fileName) throws IOException {
    return Buffer.buffer(Files.readAllBytes(Paths.get(testDir + pathSep + fileName)));
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
    AsyncFile af = vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions()).await();
    Buffer buff = Buffer.buffer(randomByteArray(4096));
    List<Future<Void>> writes = new ArrayList<>();
    for (int i = 0; i < 100000; i++) {
      writes.add(af.write(buff));
    }
    af.close().await();
    Future.all(writes).await();
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

    fs.move(from, to, options).await();
    Assert.assertFalse(fileExists(middle));
    Assert.assertTrue(fileExists(target));
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

    fs.copy(from, to, options).await();
    Assert.assertEquals(fs.readFile(from).await(), fs.readFile(to).await());
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

    FileSystemException err = assertThat(fs.copy(from, to, options))
      .isFailure()
      .asInstanceOf(FileSystemException.class)
      .await();
    Assertions.assertThat(err.getCause()).isInstanceOf(FileAlreadyExistsException.class);
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

    fs.copy(from, to, options).await();
    FileProps expected = fs.props(from).await();
    FileProps actual = fs.props(from).await();
    Assert.assertEquals(expected.creationTime(), actual.creationTime());
    Assert.assertEquals(expected.lastModifiedTime(), actual.lastModifiedTime());
    Assert.assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ), Files.getPosixFilePermissions(new File(testDir, target).toPath(), LinkOption.NOFOLLOW_LINKS));
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

    fs.copy(from, to, options).await();
    Assert.assertTrue(fs.lprops(to).await().isSymbolicLink());
    Assert.assertEquals(fs.readFile(from).await(), fs.readFile(to).await());
  }

  @Test
  public void testCreateTempDirectory() {
    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectory("project").await();
    Assert.assertNotNull(tempDirectory);
    Assert.assertTrue(Files.exists(Paths.get(tempDirectory)));
  }

  @Test
  public void testCreateTempDirectoryBlocking() {
    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectoryBlocking("project");
    Assert.assertTrue(Files.exists(Paths.get(tempDirectory)));
  }

  @Test
  public void testCreateTempDirectoryWithPerms() throws Exception {
    Assume.assumeFalse(Utils.isWindows());
    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectory("project", DEFAULT_DIR_PERMS).await();
    String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(Paths.get(tempDirectory)));
    Assert.assertEquals(perms, DEFAULT_DIR_PERMS);
  }

  @Test
  public void testCreateTempDirectoryWithPermsBlocking() throws Exception {
    Assume.assumeFalse(Utils.isWindows());

    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectoryBlocking("project", DEFAULT_DIR_PERMS);
    String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(Paths.get(tempDirectory)));
    Assert.assertEquals(perms, DEFAULT_DIR_PERMS);
  }

  @Test
  public void testCreateTempDirectoryWithDirectory() {
    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectory(testDir, "project", (String) null).await();
    Path path = Paths.get(tempDirectory);
    Assert.assertTrue(Files.exists(path));
    Assert.assertTrue(path.startsWith(testDir));
  }

  @Test
  public void testCreateTempDirectoryWithDirectoryBlocking() {
    FileSystem fs = vertx.fileSystem();
    String tempDirectory = fs.createTempDirectoryBlocking(testDir, "project", null);
    Path path = Paths.get(tempDirectory);
    Assert.assertTrue(Files.exists(Paths.get(tempDirectory)));
    Assert.assertTrue(path.startsWith(testDir));
  }


  @Test
  public void testCreateTempFile() {
    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFile("project", ".tmp").await();
    Assert.assertNotNull(tempFile);
    Assert.assertTrue(Files.exists(Paths.get(tempFile)));
  }

  @Test
  public void testCreateTempFileBlocking() {
    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFileBlocking("project", ".tmp");
    Assert.assertNotNull(tempFile);
    Assert.assertTrue(Files.exists(Paths.get(tempFile)));
  }

  @Test
  public void testCreateTempFileWithDirectory() {
    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFile(testDir, "project", ".tmp", (String)null).await();
    Assert.assertNotNull(tempFile);
    Path path = Paths.get(tempFile);
    Assert.assertTrue(Files.exists(path));
    Assert.assertTrue(path.startsWith(testDir));
  }

  @Test
  public void testCreateTempFileWithDirectoryBlocking() {
    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFileBlocking(testDir, "project", ".tmp", null);
    Assert.assertNotNull(tempFile);
    Path path = Paths.get(tempFile);
    Assert.assertTrue(Files.exists(path));
    Assert.assertTrue(path.startsWith(testDir));
  }

  @Test
  public void testCreateTempFileWithPerms() throws Exception {
    Assume.assumeFalse(Utils.isWindows());

    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFile("project", ".tmp", DEFAULT_FILE_PERMS).await();
    Path path = Paths.get(tempFile);
    Assert.assertTrue(Files.exists(path));
    String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    Assert.assertEquals(perms, DEFAULT_FILE_PERMS);
  }


  @Test
  public void testCreateTempFileWithPermsBlocking() throws Exception {
    Assume.assumeFalse(Utils.isWindows());

    FileSystem fs = vertx.fileSystem();
    String tempFile = fs.createTempFileBlocking("project", ".tmp", DEFAULT_FILE_PERMS);
    Path path = Paths.get(tempFile);
    Assert.assertTrue(Files.exists(path));
    String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    Assert.assertEquals(perms, DEFAULT_FILE_PERMS);
  }

  @Test
  public void testFileSize() throws Exception {
    String fileName = "some-file.dat";
    int expected = ThreadLocalRandom.current().nextInt(1000, 2000);
    createFileWithJunk(fileName, expected);
    AsyncFile file = vertx.fileSystem().openBlocking(testDir + pathSep + fileName, new OpenOptions());
    Assert.assertEquals(expected, file.size().await().longValue());
    file.close().await();
  }

  @Test
  public void testFileSizeBlocking() throws Exception {
    String fileName = "some-file.dat";
    int expected = ThreadLocalRandom.current().nextInt(1000, 2000);
    createFileWithJunk(fileName, expected);
    AsyncFile file = vertx.fileSystem().openBlocking(testDir + pathSep + fileName, new OpenOptions());
    Assert.assertEquals(expected, file.sizeBlocking());
    file.close().await();
  }

  @Test
  public void testFileLocking() throws Exception {
    Assume.assumeFalse(Utils.isWindows());
    String path = tmpFile(".lock").getAbsolutePath();
    FileSystem fs = vertx.fileSystem();
    fs.writeFileBlocking(path, Buffer.buffer("HelloLocks"));

    AsyncFile file1 = fs.openBlocking(path, new OpenOptions());
    AsyncFile file2 = fs.openBlocking(path, new OpenOptions());

    AsyncFileLock lock1 = file1.lock(0, "Hello".length(), false).await();
    FileSystemException err = assertThat(file2.lock())
      .isFailure()
      .asInstanceOf(FileSystemException.class)
      .await();
    Assert.assertTrue(err.getCause() instanceof OverlappingFileLockException);
    AsyncFileLock lock2 = file2.lock("Hello".length(), "Locks".length(), false).await();
    lock1.release().await();
    lock2.release().await();

    file1.close().await();
    file2.close().await();
  }

  @Test
  public void testFileWithLock1() throws Exception {
    testFileWithLock((val, promise) -> promise.complete(val));
  }

  @Test
  public void testFileWithLock2() throws Exception {
    testFileWithLock((val, promise) -> promise.fail(val));
  }

  private void testFileWithLock(BiConsumer<String, Promise<String>> completer) throws Exception {
    Assume.assumeFalse(Utils.isWindows());
    String path = tmpFile(".lock").getAbsolutePath();
    FileSystem fs = vertx.fileSystem();
    fs.writeFileBlocking(path, Buffer.buffer("HelloLocks"));
    AsyncFile file = fs.openBlocking(path, new OpenOptions());
    Promise<String> promise = Promise.promise();
    CountDownLatch latch = new CountDownLatch(1);
    Future<String> res = file.withLock(() -> {
      latch.countDown();
      return promise.future();
    });
    TestUtils.awaitLatch(latch);
    assertThat(file.lock())
      .isFailure()
      .asInstanceOf(FileSystemException.class)
      .await();
    String expected = TestUtils.randomAlphaString(10);
    completer.accept(expected, promise);
    int retries = 3;
    while (true) {
      try {
        file.lock().await();
        break;
      } catch (Exception e) {
        if (e instanceof FileSystemException && retries-- > 0) {
          // Retry
          Thread.sleep(5);
        } else {
          throw e;
        }
      }
    }
    Assert.assertEquals(expected, res.recover(err -> Future.succeededFuture(err.getMessage())).await());
    file.close().await();
  }

  @Test
  public void testFileWithLockFailure() throws Exception {
    Assume.assumeFalse(Utils.isWindows());
    String path = tmpFile(".lock").getAbsolutePath();
    FileSystem fs = vertx.fileSystem();
    fs.writeFileBlocking(path, Buffer.buffer("HelloLocks"));
    AsyncFile file = fs.openBlocking(path, new OpenOptions());
    RuntimeException failure = new RuntimeException();
    Future<String> res = file.withLock(() -> {
      throw failure;
    });
    assertThat(res)
      .isFailure()
      .asInstanceOf(RuntimeException.class)
      .await();
    file
      .lock()
      .compose(AsyncFileLock::release)
      .await();
    file.close().await();
  }

  @Test
  public void testClosedAsyncFileSetNullHandler() throws Exception {
    createFileWithJunk("file.txt", 100);
    FileSystem fs = vertx.fileSystem();
    AsyncFile asyncFile = fs.openBlocking(testDir + pathSep + "file.txt", new OpenOptions().setRead(true));
    asyncFile.close().await();
    asyncFile.handler(null);
    asyncFile.exceptionHandler(null);
    asyncFile.drainHandler(null);
    asyncFile.endHandler(null);
  }
}
