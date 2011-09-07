package org.nodex.java.core.file;

/**
 * <p>Represents properties of the file system</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystemProps {
  /**
   * The total space on the file system, in bytes
   */
  public final long totalSpace;

  /**
   * The total un-allocated space on the file syste, in bytes
   */
  public final long unallocatedSpace;

  /**
   * The total usable space on the file system, in bytes
   */
  public final long usableSpace;

  FileSystemProps(long totalSpace, long unallocatedSpace, long usableSpace) {
    this.totalSpace = totalSpace;
    this.unallocatedSpace = unallocatedSpace;
    this.usableSpace = usableSpace;
  }
}
