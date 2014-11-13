package org.vertx.java.tests.platform;

import org.junit.Test;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.testtools.TestVerticle;

import java.io.File;

import static org.vertx.testtools.VertxAssert.*;

public class PathResolverTest extends TestVerticle {

  @Test
  public void testFileWithSpace() throws Exception {
    testPath("dir/file with space.txt");
  }

  @Test
  public void testFileWithNoSpace() throws Exception {
    testPath("dir/filewithnospace.txt");
  }

  @Test
  public void testDirWithSpace() throws Exception {
    testPath("dir with space/file.txt");
  }

  @Test
  public void testDirAndFileWithSpaces() throws Exception {
    testPath("dir with space/file with space.txt");
  }

  private void testPath(String path) throws Exception {
    String base = PathAdjuster.adjust((VertxInternal) vertx, ".");
    File fstatic = new File(base, "static");
    File file = new File(fstatic, path);
    file.getParentFile().mkdirs();
    file.createNewFile();
    File adjusted = new File(PathAdjuster.adjust((VertxInternal) vertx, "static/" + path));
    try {
      assertTrue(adjusted.exists());
      testComplete();
    } finally {
      vertx.fileSystem().deleteSync(fstatic.getAbsolutePath(), true);
    }
  }
}
