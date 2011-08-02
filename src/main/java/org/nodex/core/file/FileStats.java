package org.nodex.core.file;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

/**
 * User: tim
 * Date: 02/08/11
 * Time: 16:29
 */
public class FileStats {

  public final Date creationTime;
  public final Date lastAccessTime;
  public final Date lastModifiedTime;
  public final boolean isDirectory;
  public final boolean isOther;
  public final boolean isRegularFile;
  public final boolean isSymbolicLink;
  public final long size;

  FileStats(BasicFileAttributes attrs) {
    creationTime = new Date(attrs.creationTime().toMillis());
    lastModifiedTime =  new Date(attrs.lastModifiedTime().toMillis());
    lastAccessTime =  new Date(attrs.lastAccessTime().toMillis());
    isDirectory = attrs.isDirectory();
    isOther = attrs.isOther();
    isRegularFile = attrs.isRegularFile();
    isSymbolicLink = attrs.isSymbolicLink();
    size = attrs.size();
  }
}
