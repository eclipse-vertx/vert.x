package org.nodex.java.core.file;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

/**
 * <p>Represents properties of a file on the file system</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileProps {

  /**
   * The date the file was created
   */
  public final Date creationTime;

  /**
   * The date the file was last accessed
   */
  public final Date lastAccessTime;

  /**
   * The date the file was last modified
   */
  public final Date lastModifiedTime;

  /**
   * Is the file a directory?
   */
  public final boolean isDirectory;

  /**
   * Is the file some other type? (I.e. not a directory, regular file or symbolic link)
   */
  public final boolean isOther;

  /**
   * Is the file a regular file?
   */
  public final boolean isRegularFile;

  /**
   * Is the file a symbolic link?
   */
  public final boolean isSymbolicLink;

  /**
   * The size of the file, in bytes
   */
  public final long size;

  FileProps(BasicFileAttributes attrs) {
    creationTime = new Date(attrs.creationTime().toMillis());
    lastModifiedTime = new Date(attrs.lastModifiedTime().toMillis());
    lastAccessTime = new Date(attrs.lastAccessTime().toMillis());
    isDirectory = attrs.isDirectory();
    isOther = attrs.isOther();
    isRegularFile = attrs.isRegularFile();
    isSymbolicLink = attrs.isSymbolicLink();
    size = attrs.size();
  }
}
