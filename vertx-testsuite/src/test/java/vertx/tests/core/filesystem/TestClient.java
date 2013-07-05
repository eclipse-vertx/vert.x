/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vertx.tests.core.filesystem;

import io.netty.buffer.Unpooled;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.file.FileSystemException;
import org.vertx.java.core.file.FileSystemProps;
import org.vertx.java.core.file.impl.DefaultAsyncFile;
import org.vertx.java.core.impl.Windows;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;
import org.vertx.java.testframework.TestClientBase;
import org.vertx.java.testframework.TestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private static final String TEST_DIR = "test-tmp";
  private static final String DEFAULT_DIR_PERMS = "rwxr-xr-x";
  private static final String DEFAULT_FILE_PERMS = "rw-r--r--";

  private Map<String, Object> params;
  private String pathSep;
  private File testDir;

  @Override
  public void start() {
    super.start();
    params = vertx.sharedData().getMap("params");
    java.nio.file.FileSystem fs = FileSystems.getDefault();
    pathSep = fs.getSeparator();
    params = vertx.sharedData().getMap("params");

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
    testCopy(source, target, false, true, new VoidHandler() {
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
    testCopy(source, target, false, false, new VoidHandler() {
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
    testCopy(source, target, false, true, new VoidHandler() {
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
    testCopy(source, target, false, true, new VoidHandler() {
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
    testCopy(source, target, false, true, new VoidHandler() {
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
    testCopy(source, target, false, false, new VoidHandler() {
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
    testCopy(dir, target, true, true, new VoidHandler() {
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
    AsyncResultHandler<Void> handler = createHandler(shouldPass, afterOK);
    if (recursive) {
      vertx.fileSystem().copy(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target, true, handler);
    } else {
      vertx.fileSystem().copy(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target, handler);
    }
  }

  public void testSimpleMove() throws Exception {
    final String source = "foo.txt";
    final String target = "bar.txt";
    createFileWithJunk(source, 100);
    testMove(source, target, true, new VoidHandler() {
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
    testMove(source, target, false, new VoidHandler() {
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
    testMove(source, target, true, new VoidHandler() {
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
    testMove(source, target, false, new VoidHandler() {
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
    testMove(dir, target, true, new VoidHandler() {
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
    vertx.fileSystem().move(TEST_DIR + pathSep + source, TEST_DIR + pathSep + target, createHandler(shouldPass, afterOK));
  }

  public void testTruncate() throws Exception {
    final String file1 = "some-file.dat";
    long initialLen = 1000;
    final long truncatedLen = 534;
    createFileWithJunk(file1, initialLen);
    tu.azzert(fileLength(file1) == initialLen);
    testTruncate(file1, truncatedLen, true, new VoidHandler() {
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
    vertx.fileSystem().truncate(TEST_DIR + pathSep + file, truncatedLen, createHandler(shouldPass, afterOK));
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
    testChmod(file1, perms, null, true, new VoidHandler() {
      public void handle() {
        azzertPerms(perms, file1);
        deleteFile(file1);
      }
    });
  }

  private void azzertPerms(String perms, String file1) {
  	if (Windows.isWindows() == false) {
  		tu.azzert(perms.equals(getPerms(file1)));
  	}
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
    testChmod(dir, perms, dirPerms, true, new VoidHandler() {
      public void handle() {
        azzertPerms(dirPerms, dir);
        azzertPerms(perms, dir + file1);
        azzertPerms(perms, dir + file2);
        azzertPerms(dirPerms, dir + pathSep + dir2);
        azzertPerms(perms, dir + pathSep + dir2 + file3);
        deleteDir(dir);
      }
    });
  }

  private void testChmod(final String file, final String perms, final String dirPerms,
                         final boolean shouldPass, final Handler<Void> afterOK) throws Exception {
    if (Files.isDirectory(Paths.get(TEST_DIR + pathSep + file))) {
      azzertPerms(DEFAULT_DIR_PERMS, file);
    } else {
      azzertPerms(DEFAULT_FILE_PERMS, file);
    }
    AsyncResultHandler<Void> handler = createHandler(shouldPass, afterOK);
    if (dirPerms != null) {
      vertx.fileSystem().chmod(TEST_DIR + pathSep + file, perms, dirPerms, handler);
    } else {
      vertx.fileSystem().chmod(TEST_DIR + pathSep + file, perms, handler);
    }
  }
  
  public void testProps() throws Exception {
    String fileName = "some-file.txt";
    final long fileSize = 1234;
    
    // The times are quite inaccurate so we give 1 second leeway
    final long start = 1000 * (System.currentTimeMillis() / 1000 - 1);
    createFileWithJunk(fileName, fileSize);

    testProps(fileName, false, true, new Handler<FileProps>() {
      public void handle(FileProps st) {
        tu.azzert(st != null);
        tu.azzert(fileSize == st.size());
        tu.azzert(st.creationTime().getTime() >= start);
        tu.azzert(st.lastAccessTime().getTime() >= start);
        tu.azzert(st.lastModifiedTime().getTime() >= start);
        tu.azzert(!st.isDirectory());
        tu.azzert(!st.isOther());
        tu.azzert(st.isRegularFile());
        tu.azzert(!st.isSymbolicLink());
      }
    });
  }
  
  public void testPropsFileDoesNotExist() throws Exception {
    String fileName = "some-file.txt";
    testProps(fileName, false, false, null);
  }
  
  public void testPropsFollowLink() throws Exception {
    final String fileName = "some-file.txt";
    final long fileSize = 1234;
    
    // The times are quite inaccurate so we give 1 second leeway
    final long start = 1000 * (System.currentTimeMillis() / 1000 - 1);
    createFileWithJunk(fileName, fileSize);
    final long end = 1000 * (System.currentTimeMillis() / 1000 + 1);

    String linkName = "some-link.txt";
    Files.createSymbolicLink(Paths.get(TEST_DIR + pathSep + linkName), Paths.get(fileName));

    testProps(linkName, false, true, new Handler<FileProps>() {
      public void handle(FileProps st) {
        tu.azzert(st != null);
        tu.azzert(fileSize == st.size());
        tu.azzert(st.creationTime().getTime() >= start);
        tu.azzert(st.creationTime().getTime() <= end);
        tu.azzert(st.lastAccessTime().getTime() >= start);
        tu.azzert(st.lastAccessTime().getTime() <= end);
        tu.azzert(st.lastModifiedTime().getTime() >= start);
        tu.azzert(st.lastModifiedTime().getTime() <= end);
        tu.azzert(!st.isDirectory());
        tu.azzert(!st.isOther());
        tu.azzert(st.isRegularFile());
        tu.azzert(!st.isSymbolicLink());
      }
    });
  }

  public void testPropsDontFollowLink() throws Exception {
    final String fileName = "some-file.txt";
    final long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    String linkName = "some-link.txt";
    Files.createSymbolicLink(Paths.get(TEST_DIR + pathSep + linkName), Paths.get(fileName));
    testProps(linkName, true, true, new Handler<FileProps>() {
      public void handle(FileProps st) {
        tu.azzert(st != null);
        tu.azzert(st.isSymbolicLink());
      }
    });
  }
  
  private void testProps(final String fileName, final boolean link, final boolean shouldPass,
                         final Handler<FileProps> afterOK) throws Exception {
    AsyncResultHandler<FileProps> handler = new AsyncResultHandler<FileProps>() {
      public void handle(AsyncResult<FileProps> ar) {
        tu.checkThread();
        if (ar.failed()) {
          if (shouldPass) {
            tu.exception(ar.cause(), "stat failed");
          } else {
            tu.azzert(ar.cause() instanceof FileSystemException);
            if (afterOK != null) {
              afterOK.handle(ar.result());
            }
            tu.testComplete();
          }
        } else {
          if (shouldPass) {
            if (afterOK != null) {
              afterOK.handle(ar.result());
            }
            tu.testComplete();
          } else {
            tu.azzert(false, "stat should fail");
          }
        }
      }
    };
    if (link) {
      vertx.fileSystem().lprops(TEST_DIR + pathSep + fileName, handler);
    } else {
      vertx.fileSystem().props(TEST_DIR + pathSep + fileName, handler);
    }
  }

  public void testLink() throws Exception {
    String fileName = "some-file.txt";
    final long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    final String linkName = "some-link.txt";
    testLink(linkName, fileName, false, true, new VoidHandler() {
      public void handle() {
        tu.azzert(fileLength(linkName) == fileSize);
        tu.azzert(!Files.isSymbolicLink(Paths.get(TEST_DIR + pathSep + linkName)));
      }
    });
  }

  public void testSymLink() throws Exception {
    String fileName = "some-file.txt";
    final long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    final String symlinkName = "some-sym-link.txt";
    testLink(symlinkName, fileName, true, true, new VoidHandler() {
      public void handle() {
       tu.azzert(fileLength(symlinkName) == fileSize);
       tu.azzert(Files.isSymbolicLink(Paths.get(TEST_DIR + pathSep + symlinkName)));
      }
    });
  }

  private void testLink(final String from, final String to, final boolean symbolic,
                        final boolean shouldPass, final Handler<Void> afterOK) throws Exception {
    AsyncResultHandler<Void> handler = createHandler(shouldPass, afterOK);
    if (symbolic) {
      // Symlink is relative
      vertx.fileSystem().symlink(TEST_DIR + pathSep + from, to, handler);
    } else {
      vertx.fileSystem().link(TEST_DIR + pathSep + from, TEST_DIR + pathSep + to, handler);
    }
  }

  public void testUnlink() throws Exception {
    String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    final String linkName = "some-link.txt";
    Files.createLink(Paths.get(TEST_DIR + pathSep + linkName), Paths.get(TEST_DIR + pathSep + fileName));
    tu.azzert(fileSize == fileLength(linkName));
    AsyncResultHandler<Void> handler = createHandler(true, new VoidHandler() {
      public void handle() {
        tu.azzert(!fileExists(linkName));
      }
    });
    vertx.fileSystem().unlink(TEST_DIR + pathSep + linkName, handler);
  }

  public void testReadSymLink() throws Exception {
    final String fileName = "some-file.txt";
    long fileSize = 1234;
    createFileWithJunk(fileName, fileSize);
    final String linkName = "some-link.txt";
    Files.createSymbolicLink(Paths.get(TEST_DIR + pathSep + linkName), Paths.get(fileName));
    AsyncResultHandler<String> handler = new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> ar) {
        tu.checkThread();
        if (ar.failed()) {
          tu.exception(ar.cause(), "Read failed");
        } else {
          tu.azzert(fileName.equals(ar.result()));
          tu.testComplete();
        }
      }
    };
    vertx.fileSystem().readSymlink(TEST_DIR + pathSep + linkName, handler);
  }

  public void testSimpleDelete() throws Exception {
    final String fileName = "some-file.txt";
    createFileWithJunk(fileName, 100);
    tu.azzert(fileExists(fileName));
    testDelete(fileName, false, true, new VoidHandler() {
      public void handle() {
        tu.azzert(!fileExists(fileName));
      }
    });
  }

  public void testDeleteEmptyDir() throws Exception {
    final String dirName = "some-dir";
    mkDir(dirName);
    tu.azzert(fileExists(dirName));
    testDelete(dirName, false, true, new VoidHandler() {
      public void handle() {
        tu.azzert(!fileExists(dirName));
      }
    });
  }

  public void testDeleteNonExistent() throws Exception {
    String dirName = "some-dir";
    tu.azzert(!fileExists(dirName));
    testDelete(dirName, false, false, null);
  }

  public void testDeleteNonEmptyFails() throws Exception {
    String dirName = "some-dir";
    mkDir(dirName);
    String file1 = "some-file.txt";
    createFileWithJunk(dirName + pathSep + file1, 100);
    testDelete(dirName, false, false, null);
  }

  public void testDeleteRecursive() throws Exception {
    final String dir = "some-dir";
    String file1 = pathSep + "file1.dat";
    String file2 = pathSep + "index.html";
    String dir2 = "next-dir";
    String file3 = pathSep + "blah.java";
    mkDir(dir);
    createFileWithJunk(dir + file1, 100);
    createFileWithJunk(dir + file2, 100);
    mkDir(dir + pathSep + dir2);
    createFileWithJunk(dir + pathSep + dir2 + file3, 100);
    testDelete(dir, true, true, new VoidHandler() {
      public void handle() {
        tu.azzert(!fileExists(dir));
      }
    });
  }

  private void testDelete(final String fileName, final boolean recursive, final boolean shouldPass,
                          final Handler<Void> afterOK) throws Exception {
    AsyncResultHandler<Void> handler = createHandler(shouldPass, afterOK);
    if (recursive) {
      vertx.fileSystem().delete(TEST_DIR + pathSep + fileName, recursive, handler);
    } else {
      vertx.fileSystem().delete(TEST_DIR + pathSep + fileName, handler);
    }
  }

  public void testMkdirSimple() throws Exception {
    final String dirName = "some-dir";
    testMkdir(dirName, null, false, true, new VoidHandler() {
      public void handle() {
        tu.azzert(fileExists(dirName));
        tu.azzert(Files.isDirectory(Paths.get(TEST_DIR + pathSep + dirName)));
      }
    });
  }

  public void testMkdirWithParentsFails() throws Exception {
    String dirName = "top-dir" + pathSep + "some-dir";
    testMkdir(dirName, null, false, false, null);
  }

  public void testMkdirWithPerms() throws Exception {
    final String dirName = "some-dir";
    final String perms = "rwx--x--x";
    testMkdir(dirName, perms, false, true, new VoidHandler() {
      public void handle() {
        tu.azzert(fileExists(dirName));
        tu.azzert(Files.isDirectory(Paths.get(TEST_DIR + pathSep + dirName)));
        azzertPerms(perms, dirName);
      }
    });
  }

  public void testMkdirCreateParents() throws Exception {
    final String dirName = "top-dir" + pathSep + "/some-dir";
    testMkdir(dirName, null, true, true, new VoidHandler() {
      public void handle() {
        tu.azzert(fileExists(dirName));
        tu.azzert(Files.isDirectory(Paths.get(TEST_DIR + pathSep + dirName)));
      }
    });
  }

  public void testMkdirCreateParentsWithPerms() throws Exception {
    final String dirName = "top-dir" + pathSep + "/some-dir";
    final String perms = "rwx--x--x";
    testMkdir(dirName, perms, true, true, new VoidHandler() {
      public void handle() {
        tu.azzert(fileExists(dirName));
        tu.azzert(Files.isDirectory(Paths.get(TEST_DIR + pathSep + dirName)));
        azzertPerms(perms, dirName);
      }
    });
  }

  private void testMkdir(final String dirName, final String perms, final boolean createParents,
                         final boolean shouldPass, final Handler<Void> afterOK) throws Exception {
    AsyncResultHandler<Void> handler = createHandler(shouldPass, afterOK);
    if (createParents) {
      if (perms != null) {
        vertx.fileSystem().mkdir(TEST_DIR + pathSep + dirName, perms, createParents, handler);
      } else {
        vertx.fileSystem().mkdir(TEST_DIR + pathSep + dirName, createParents, handler);
      }
    } else {
      if (perms != null) {
        vertx.fileSystem().mkdir(TEST_DIR + pathSep + dirName, perms, handler);
      } else {
        vertx.fileSystem().mkdir(TEST_DIR + pathSep + dirName, handler);
      }
    }
  }

  public void testReadDirSimple() throws Exception {
    final String dirName = "some-dir";
    mkDir(dirName);
    final int numFiles = 10;
    for (int i = 0; i < numFiles; i++) {
      createFileWithJunk(dirName + pathSep + "file-" + i + ".dat", 100);
    }
    testReadDir(dirName, null, true, new Handler<String[]>() {
      public void handle(String[] fileNames) {
        tu.azzert(fileNames.length == numFiles);
        Set<String> fset = new HashSet<String>();
        for (int i = 0; i < numFiles; i++) {
          fset.add(fileNames[i]);
        }
        File dir = new File(TEST_DIR + pathSep + dirName);
        String root;
        try {
          root = dir.getCanonicalPath();
        } catch (IOException e) {
          tu.exception(e, "failed to get path");
          return;
        }
        for (int i = 0; i < numFiles; i++) {
          tu.azzert(fset.contains(root + pathSep + "file-" + i + ".dat"));
        }
      }
    });
  }

  public void testReadDirWithFilter() throws Exception {
    final String dirName = "some-dir";
    mkDir(dirName);
    final int numFiles = 10;
    for (int i = 0; i < numFiles; i++) {
      createFileWithJunk(dirName + pathSep + "foo-" + i + ".txt", 100);
    }
    for (int i = 0; i < numFiles; i++) {
      createFileWithJunk(dirName + pathSep + "bar-" + i + ".txt", 100);
    }
    testReadDir(dirName, "foo.+", true, new Handler<String[]>() {
      public void handle(String[] fileNames) {
        tu.azzert(fileNames.length == numFiles);
        Set<String> fset = new HashSet<>();
        for (int i = 0; i < numFiles; i++) {
          fset.add(fileNames[i]);
        }
        File dir = new File(TEST_DIR + pathSep + dirName);
        String root;
        try {
          root = dir.getCanonicalPath();
        } catch (IOException e) {
          tu.exception(e, "failed to get path");
          return;
        }
        for (int i = 0; i < numFiles; i++) {
          tu.azzert(fset.contains(root + pathSep + "foo-" + i + ".txt"));
        }
      }
    });
  }

  private void testReadDir(final String dirName, final String filter, final boolean shouldPass,
                           final Handler<String[]> afterOK) throws Exception {
    AsyncResultHandler<String[]> handler = new AsyncResultHandler<String[]>() {
      public void handle(AsyncResult<String[]> ar) {
        tu.checkThread();
        if (ar.failed()) {
          if (shouldPass) {
            tu.exception(ar.cause(), "read failed");
          } else {
            tu.azzert(ar.cause() instanceof FileSystemException);
            if (afterOK != null) {
              afterOK.handle(null);
            }
            tu.testComplete();
          }
        } else {
          if (shouldPass) {
            if (afterOK != null) {
              afterOK.handle(ar.result());
            }
            tu.testComplete();
          } else {
            tu.azzert(false, "read should fail");
          }
        }
      }
    };
    if (filter == null) {
      vertx.fileSystem().readDir(TEST_DIR + pathSep + dirName, handler);
    } else {
      vertx.fileSystem().readDir(TEST_DIR + pathSep + dirName, filter, handler);
    }
  }

  public void testReadFile() throws Exception {
    final byte[] content = TestUtils.generateRandomByteArray(1000);
    final String fileName = "some-file.dat";
    createFile(fileName, content);
    AsyncResultHandler<Buffer> handler = new AsyncResultHandler<Buffer>() {
      public void handle(AsyncResult<Buffer> ar) {
        tu.checkThread();
        if (ar.failed()) {
          tu.exception(ar.cause(), "failed to read");
        } else {
          tu.azzert(TestUtils.buffersEqual(new Buffer(content), ar.result()));
          tu.testComplete();
        }
      }
    };
    vertx.fileSystem().readFile(TEST_DIR + pathSep + fileName, handler);
  }

  public void testWriteFile() throws Exception {
    final byte[] content = TestUtils.generateRandomByteArray(1000);
    final Buffer buff = new Buffer(content);
    final String fileName = "some-file.dat";
    AsyncResultHandler<Void> handler = new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> ar) {
        tu.checkThread();
        if (ar.failed()) {
          tu.exception(ar.cause(), "failed to write");
        } else {
          tu.azzert(fileExists(fileName));
          tu.azzert(fileLength(fileName) == content.length);
          byte[] readBytes;
          try {
            readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName));
          } catch (IOException e) {
            tu.exception(e, "failed to read");
            return;
          }
          tu.azzert(TestUtils.buffersEqual(buff, new Buffer(readBytes)));
          tu.testComplete();
        }
      }
    };
    vertx.fileSystem().writeFile(TEST_DIR + pathSep + fileName, buff, handler);
  }

  public void testWriteAsync() throws Exception {
    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;

    byte[] content = TestUtils.generateRandomByteArray(chunkSize * chunks);
    final Buffer buff = new Buffer(content);

    vertx.fileSystem().open(TEST_DIR + pathSep + fileName, null, false, true, true, true, new AsyncResultHandler<AsyncFile>() {
      int count;
      public void handle(final AsyncResult<AsyncFile> arr) {
        tu.checkThread();
        if (arr.succeeded()) {
          for (int i = 0; i < chunks; i++) {
            Buffer chunk = buff.getBuffer(i * chunkSize, (i + 1) * chunkSize);
            tu.azzert(chunk.length() == chunkSize);
            arr.result().write(chunk, i * chunkSize, new AsyncResultHandler<Void>() {
              public void handle(AsyncResult<Void> ar) {
                if (ar.succeeded()) {
                  if (++count == chunks) {
                    arr.result().close(new AsyncResultHandler<Void>() {
                      @Override
                      public void handle(AsyncResult<Void> ar) {
                        tu.checkThread();
                        if (ar.failed()) {
                          tu.exception(ar.cause(), "failed to close");
                        } else {
                          tu.azzert(fileExists(fileName));
                          byte[] readBytes;
                          try {
                            readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName));
                          } catch (IOException e) {
                            tu.exception(e, "Failed to read file");
                            return;
                          }
                          Buffer read = new Buffer(readBytes);
                          tu.azzert(TestUtils.buffersEqual(buff, read));
                          tu.testComplete();
                        }
                      }
                    });
                  }
                } else {
                  tu.exception(ar.cause(), "Failed to write");
                }
              }
            });
          }
        } else {
          tu.exception(arr.cause(), "Failed to open");
        }
      }
    });
  }

  public void testReadAsync() throws Exception {
    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;
    byte[] content = TestUtils.generateRandomByteArray(chunkSize * chunks);
    final Buffer expected = new Buffer(content);
    createFile(fileName, content);
    vertx.fileSystem().open(TEST_DIR + pathSep + fileName, null, true, false, false, new AsyncResultHandler<AsyncFile>() {
      int reads;
      public void handle(final AsyncResult<AsyncFile> arr) {
        tu.checkThread();
        if (arr.succeeded()) {
          final Buffer buff = new Buffer(chunks * chunkSize);
          for (int i = 0; i < chunks; i++) {
            arr.result().read(buff, i * chunkSize, i * chunkSize, chunkSize, new AsyncResultHandler<Buffer>() {
              public void handle(final AsyncResult<Buffer> arb) {
                if (arb.succeeded()) {
                  if (++reads == chunks) {
                    arr.result().close(new AsyncResultHandler<Void>() {
                      @Override
                      public void handle(AsyncResult<Void> ar) {
                        tu.checkThread();
                        if (ar.failed()) {
                          tu.exception(ar.cause(), "failed to close");
                        } else {
                          tu.azzert(TestUtils.buffersEqual(expected, buff));
                          tu.azzert(buff == arb.result());
                          tu.testComplete();
                        }
                      }
                    });
                  }
                } else {
                  tu.exception(arb.cause(), "failed to read");
                }
              }
            });
          }
        } else {
          tu.exception(arr.cause(), "failed to open file");
        }
      }
    });
  }

  public void testWriteStream() throws Exception {
    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;
    byte[] content = TestUtils.generateRandomByteArray(chunkSize * chunks);
    final Buffer buff = new Buffer(content);
    vertx.fileSystem().open(TEST_DIR + pathSep + fileName, new AsyncResultHandler<AsyncFile>() {
      public void handle(AsyncResult<AsyncFile> ar) {
        tu.checkThread();
        if (ar.succeeded()) {
          WriteStream ws = ar.result();

          ws.exceptionHandler(new Handler<Throwable>() {
            public void handle(Throwable t) {
              tu.checkThread();
              tu.exception(t, "caught exception on stream");
            }
          });

          for (int i = 0; i < chunks; i++) {
            Buffer chunk = buff.getBuffer(i * chunkSize, (i + 1) * chunkSize);
            tu.azzert(chunk.length() == chunkSize);
            ws.write(chunk);
          }

          ar.result().close(new AsyncResultHandler<Void>() {
            public void handle(AsyncResult<Void> ar) {
              tu.checkThread();
              if (ar.failed()) {
                tu.exception(ar.cause(), "failed to close");
              } else {
                tu.azzert(fileExists(fileName));
                byte[] readBytes;
                try {
                  readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName));
                } catch (IOException e) {
                  tu.exception(e, "failed to read");
                  return;
                }
                tu.azzert(TestUtils.buffersEqual(buff, new Buffer(readBytes)));
                tu.testComplete();
              }
            }
          });
        } else {
          tu.exception(ar.cause(), "failed to open");
        }
      }
    });
  }

  public void testWriteStreamWithCompositeBuffer() throws Exception {
    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;
    byte[] content1 = TestUtils.generateRandomByteArray(chunkSize * (chunks / 2 ));
    byte[] content2 = TestUtils.generateRandomByteArray(chunkSize * (chunks / 2 ));
    final Buffer buff = new Buffer(Unpooled.wrappedBuffer(content1, content2));
    vertx.fileSystem().open(TEST_DIR + pathSep + fileName, new AsyncResultHandler<AsyncFile>() {
      public void handle(AsyncResult<AsyncFile> ar) {
        tu.checkThread();
        if (ar.succeeded()) {
          WriteStream ws = ar.result();

          ws.exceptionHandler(new Handler<Throwable>() {
            public void handle(Throwable t) {
              tu.checkThread();
              tu.exception(t, "caught exception on stream");
            }
          });

          ws.write(buff);


          ar.result().close(new AsyncResultHandler<Void>() {
            public void handle(AsyncResult<Void> ar) {
              tu.checkThread();
              if (ar.failed()) {
                tu.exception(ar.cause(), "failed to close");
              } else {
                tu.azzert(fileExists(fileName));
                byte[] readBytes;
                try {
                  readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName));
                } catch (IOException e) {
                  tu.exception(e, "failed to read");
                  return;
                }
                tu.azzert(TestUtils.buffersEqual(buff, new Buffer(readBytes)));
                tu.testComplete();
              }
            }
          });
        } else {
          tu.exception(ar.cause(), "failed to open");
        }
      }
    });
  }

  public void testReadStream() throws Exception {
    final String fileName = "some-file.dat";
    final int chunkSize = 1000;
    final int chunks = 10;
    final byte[] content = TestUtils.generateRandomByteArray(chunkSize * chunks);
    createFile(fileName, content);

    vertx.fileSystem().open(TEST_DIR + pathSep + fileName, null, true, false, false, new AsyncResultHandler<AsyncFile>() {
      public void handle(final AsyncResult<AsyncFile> ar) {
        tu.checkThread();
        if (ar.succeeded()) {
          ReadStream rs = ar.result();
          final Buffer buff = new Buffer();

          rs.dataHandler(new Handler<Buffer>() {
            public void handle(Buffer data) {
              tu.checkThread();
              buff.appendBuffer(data);
            }
          });

          rs.exceptionHandler(new Handler<Throwable>() {
            public void handle(Throwable t) {
              tu.checkThread();
              tu.exception(t, "caught exception");
            }
          });

          rs.endHandler(new VoidHandler() {
            public void handle() {
							ar.result().close(new AsyncResultHandler<Void>() {
								@Override
								public void handle(AsyncResult<Void> ar) {
									tu.checkThread();
									if (ar.failed()) {
										tu.exception(ar.cause(), "failed to close");
									} else {
										tu.checkThread();
										tu.azzert(TestUtils.buffersEqual(buff, new Buffer(content)));
										tu.testComplete();
									}
								}
							});
            }
          });
        } else {
          tu.exception(ar.cause(), "failed to open");
        }
      }
    });
  }

  public void testPumpFileStreams() throws Exception {
    final String fileName1 = "some-file.dat";
    final String fileName2 = "some-other-file.dat";

    //Non integer multiple of buffer size
    final int fileSize = (int) (DefaultAsyncFile.BUFFER_SIZE * 1000.3);
    final byte[] content = TestUtils.generateRandomByteArray(fileSize);
    createFile(fileName1, content);

    vertx.fileSystem().open(TEST_DIR + pathSep + fileName1, null, true, false, false, new AsyncResultHandler<AsyncFile>() {
      public void handle(final AsyncResult<AsyncFile> arr) {
        tu.checkThread();
        if (arr.succeeded()) {
          final ReadStream rs = arr.result();

          //Open file for writing
          vertx.fileSystem().open(TEST_DIR + pathSep + fileName2, null, true, true, true, new AsyncResultHandler<AsyncFile>() {

            public void handle(final AsyncResult<AsyncFile> ar) {
              tu.checkThread();
              if (ar.succeeded()) {

                WriteStream ws = ar.result();
                Pump p = Pump.createPump(rs, ws);
                p.start();
                rs.endHandler(new VoidHandler() {
                  public void handle() {
                    tu.checkThread();
										arr.result().close(new AsyncResultHandler<Void>() {
                      @Override
                      public void handle(AsyncResult<Void> car) {
                        tu.checkThread();
                        if (car.failed()) {
                          tu.exception(car.cause(), "failed to close");
                        } else {
                          ar.result().close(new AsyncResultHandler<Void>() {
                            public void handle(AsyncResult<Void> ar) {
                              tu.checkThread();
                              if (ar.failed()) {
                                tu.exception(ar.cause(), "failed to close");
                              } else {
                                tu.azzert(fileExists(fileName2));
                                byte[] readBytes;
                                try {
                                  readBytes = Files.readAllBytes(Paths.get(TEST_DIR + pathSep + fileName2));
                                } catch (IOException e) {
                                  tu.exception(e, "failed to read");
                                  return;
                                }
                                tu.azzert(TestUtils.buffersEqual(new Buffer(content), new Buffer(readBytes)));
                                tu.testComplete();
                              }
                            }
                          });
                        }
                      }
                    });
                  }
                });
              } else {
                tu.exception(ar.cause(), "failed to open");
              }
            }
          });
        } else {
          tu.exception(arr.cause(), "failed to open");
        }
      }
    });
  }

  public void testCreateFileNoPerms() throws Exception {
    testCreateFile(null, true);
  }

  public void testCreateFileWithPerms() throws Exception {
    testCreateFile("rwx------", true);
  }

  public void testCreateFileAlreadyExists() throws Exception {
    createFileWithJunk("some-file.dat", 100);
    testCreateFile(null, false);
  }

  private void testCreateFile(final String perms, final boolean shouldPass) throws Exception {
    final String fileName = "some-file.dat";
    AsyncResultHandler<Void> handler = new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> ar) {
        tu.checkThread();
        if (ar.failed()) {
          if (shouldPass) {
            tu.exception(ar.cause(), "failed to create");
          } else {
            tu.azzert(ar.cause() instanceof FileSystemException);
            tu.testComplete();
          }
        } else {
          if (shouldPass) {
            tu.azzert(fileExists(fileName));
            tu.azzert(0 == fileLength(fileName));
            if (perms != null) {
              azzertPerms(perms, fileName);
            }
            tu.testComplete();
          } else {
            tu.azzert(false, "test should fail");
          }
        }
      }
    };
    if (perms != null) {
      vertx.fileSystem().createFile(TEST_DIR + pathSep + fileName, perms, handler);
    } else {
      vertx.fileSystem().createFile(TEST_DIR + pathSep + fileName, handler);
    }
  }

  public void testExists() throws Exception {
    testExists(true);
  }

  public void testNotExists() throws Exception {
    testExists(false);
  }

  private void testExists(final boolean exists) throws Exception {
    final String fileName = "some-file.dat";
    if (exists) {
      createFileWithJunk(fileName, 100);
    }

    AsyncResultHandler<Boolean> handler = new AsyncResultHandler<Boolean>() {
      public void handle(AsyncResult<Boolean> ar) {
        tu.checkThread();
        if (ar.succeeded()) {
          if (exists) {
            tu.azzert(ar.result());
          } else {
            tu.azzert(!ar.result());
          }
          tu.testComplete();
        } else {
          tu.exception(ar.cause(), "failed to check");
        }
      }
    };
    vertx.fileSystem().exists(TEST_DIR + pathSep + fileName, handler);
  }

  public void testFSProps() throws Exception {
    String fileName = "some-file.txt";
    createFileWithJunk(fileName, 1234);
    testFSProps(fileName, new Handler<FileSystemProps>() {
      public void handle(FileSystemProps props) {
        tu.azzert(props.totalSpace() > 0);
        tu.azzert(props.unallocatedSpace() > 0);
        tu.azzert(props.usableSpace() > 0);
      }
    });
  }

  private void testFSProps(final String fileName,
                           final Handler<FileSystemProps> afterOK) throws Exception {
    AsyncResultHandler<FileSystemProps> handler = new AsyncResultHandler<FileSystemProps>() {
      public void handle(AsyncResult<FileSystemProps> ar) {
        tu.checkThread();
        if (ar.failed()) {
          tu.exception(ar.cause(), "props failed");
        } else {
          afterOK.handle(ar.result());
          tu.testComplete();
        }
      }
    };
    vertx.fileSystem().fsProps(TEST_DIR + pathSep + fileName, handler);
  }

  private AsyncResultHandler<Void> createHandler(final boolean shouldPass, final Handler<Void> afterOK) {
    return new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> event) {
        tu.checkThread();
        if (event.failed()) {
          if (shouldPass) {
            tu.exception(event.cause(), "operation failed");
          } else {
            tu.azzert(event.cause() instanceof FileSystemException);
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
            tu.azzert(false, "operation should fail");
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
    deleteDir(new File(TEST_DIR + pathSep + dir));
  }

  private void mkDir(String dirName) throws Exception {
    File dir = new File(TEST_DIR + pathSep + dirName);
    dir.mkdir();
    
    setPerms( Paths.get( dir.getCanonicalPath() ), DEFAULT_DIR_PERMS );
  }

  private long fileLength(String fileName) {
    File file = new File(testDir, fileName);
    return file.length();
  }

  private void setPerms(Path path, String perms) {
  	if (Windows.isWindows() == false) {
	    try {
	      Files.setPosixFilePermissions( path, PosixFilePermissions.fromString( perms ) );
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
    File file = new File(TEST_DIR + pathSep + fileName);
    file.delete();
  }
}
