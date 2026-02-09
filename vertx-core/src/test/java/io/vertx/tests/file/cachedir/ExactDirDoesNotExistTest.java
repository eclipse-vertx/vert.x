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
import org.junit.Assert;
import org.junit.Test;

public class ExactDirDoesNotExistTest extends VertxTestBase {

  private final Path cacheBaseDir;

  public ExactDirDoesNotExistTest() throws IOException {
    cacheBaseDir = Files.createTempDirectory("cache-does-not-exist");
    Files.deleteIfExists(cacheBaseDir);
    Assert.assertFalse(Files.exists(cacheBaseDir));
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
      Assert.assertEquals(cacheBaseDir.resolve("conf.json").toFile().getCanonicalPath(), file.getCanonicalPath());
    }
  }
}
