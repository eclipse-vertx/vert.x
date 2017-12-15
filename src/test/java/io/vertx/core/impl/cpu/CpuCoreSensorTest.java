package io.vertx.core.impl.cpu;

import io.vertx.core.impl.cpu.CpuCoreSensor;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Tests that we can read the number of CPUs from /proc/self/status files.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CpuCoreSensorTest {
  @Test
  public void readRegular() throws Exception {
    File file = new File("src/test/resources/cpus/status-1.txt");
    assertThat(CpuCoreSensor.readCPUMask(file), is(1));
  }

  @Test
  public void readRegular2() throws Exception {
    File file = new File("src/test/resources/cpus/status-2.txt");
    assertThat(CpuCoreSensor.readCPUMask(file), is(2));
  }

  @Test
  public void readMissingFile() throws Exception {
    File file = new File("src/test/resources/cpus/does-not-exist");
    assertThat(CpuCoreSensor.readCPUMask(file), is(-1));
  }

  @Test
  public void readMissingEntry() throws Exception {
    File file = new File("src/test/resources/cpus/missing.txt");
    assertThat(CpuCoreSensor.readCPUMask(file), is(-1));
  }

  @Test
  public void readCorruptedFile() throws Exception {
    File file = new File("src/test/resources/cpus/corrupted.txt");
    assertThat(CpuCoreSensor.readCPUMask(file), is(-1));
  }

}
