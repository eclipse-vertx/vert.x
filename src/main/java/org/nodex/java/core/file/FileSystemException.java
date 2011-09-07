package org.nodex.java.core.file;

/**
 * Exception thrown by the FileSystem class
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystemException extends Exception {

  /**
   * Construct a {@code FileSystemException} with a message as specified by {@code msg}
   */
  public FileSystemException(String msg) {
    super(msg);
  }
}
