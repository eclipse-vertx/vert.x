package org.nodex.java.core.file;

/**
 * User: tim
 * Date: 02/08/11
 * Time: 12:46
 */
public class FileSystemStats {
  public final long totalSpace;
  public final long unallocatedSpace;
  public final long usableSpace;

  FileSystemStats(long totalSpace, long unallocatedSpace, long usableSpace) {
    this.totalSpace = totalSpace;
    this.unallocatedSpace = unallocatedSpace;
    this.usableSpace = usableSpace;
  }
}
