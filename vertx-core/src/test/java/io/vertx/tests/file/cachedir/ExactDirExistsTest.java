package io.vertx.tests.file.cachedir;

import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.test.core.VertxTestBase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class ExactDirExistsTest extends VertxTestBase {

  private final Path cacheBaseDir;

  public ExactDirExistsTest() throws IOException {
    cacheBaseDir = Paths.get(System.getProperty("java.io.tmpdir", "."), "cache-exists");
    Files.deleteIfExists(cacheBaseDir);
    Files.createDirectories(cacheBaseDir);
    Assert.assertTrue(Files.exists(cacheBaseDir));
    Assert.assertTrue(Files.isDirectory(cacheBaseDir));
  }

  @Override
  protected VertxOptions getOptions() {
    return new VertxOptions(super.getOptions())
      .setFileSystemOptions(new FileSystemOptions()
        .setFileCachingEnabled(true)
        .setFileCacheDir(cacheBaseDir.toAbsolutePath().toString())
        .setFileCacheDirAsExactPath(true));
  }

  @Test
  public void test() throws IOException {
    try (FileResolver fileResolver = ((VertxInternal) vertx).fileResolver()) {
      File file = fileResolver.resolve("conf.json");
      // When exact path is used and dir exists, a suffix is appended
      String resolvedDir = file.getCanonicalFile().getParent();
      Assert.assertTrue(resolvedDir.startsWith(cacheBaseDir.toFile().getCanonicalPath()));
    }
  }
}
