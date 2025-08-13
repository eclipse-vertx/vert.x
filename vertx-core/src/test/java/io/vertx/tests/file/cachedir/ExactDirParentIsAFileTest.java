package io.vertx.tests.file.cachedir;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.test.core.VertxTestBase;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class ExactDirParentIsAFileTest {

  private final Path cacheBaseDir;

  public ExactDirParentIsAFileTest() throws IOException {
    Path cacheBaseDirParent = Paths.get(System.getProperty("java.io.tmpdir", "."), "cache-parent");
    Files.deleteIfExists(cacheBaseDirParent);
    Files.createFile(cacheBaseDirParent);
    Assert.assertTrue(Files.exists(cacheBaseDirParent));
    Assert.assertFalse(Files.isDirectory(cacheBaseDirParent));
    this.cacheBaseDir = cacheBaseDirParent.resolve("actual-cache");
    Assert.assertFalse(Files.exists(cacheBaseDir));
  }

  @Test
  public void test() {
    Assert.assertThrows(IllegalStateException.class, () -> {
      Vertx.builder().with(new VertxOptions().setFileSystemOptions(new FileSystemOptions().setFileCachingEnabled(true).setExactFileCacheDir(cacheBaseDir.toAbsolutePath().toString()))).build();
    });
  }
}
