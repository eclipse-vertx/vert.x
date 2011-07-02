package org.nodex.core.file;

import org.nodex.core.NoArgCallback;
import org.nodex.core.buffer.Buffer;

/**
 * User: timfox
 * Date: 29/06/2011
 * Time: 21:52
 */
public class FileSystem {
  public static void rename(String from, String to, NoArgCallback onCompletion) {

  }

  public static void truncate(String path, int len, NoArgCallback onCompletion) {

  }

  public static void chmod(String path, int mode, NoArgCallback onCompletion) {

  }

  public static void link(String src, String dest, NoArgCallback onCompletion) {

  }

  public static void symlink(String src, String dest, NoArgCallback onCompletion) {

  }

  public static void unlink(String path, NoArgCallback onCompletion) {

  }

  public static void mkdir(String path, int mode, NoArgCallback onCompletion) {

  }

  public static void readDir(String path, NoArgCallback onCompletion) {

  }

  public static void close(int fd, NoArgCallback onCompletion) {

  }

  public static void open(String path, int flags, int mode, NoArgCallback onCompletion) {

  }

  public static void write(int fd, Buffer buffer, int offset, int length, int position, NoArgCallback onCompletion) {

  }

  public static void read(int fd, Buffer buffer, int offset, int length, int position, NoArgCallback onCompletion) {

  }

  public static void readFile(String path, String encoding, NoArgCallback callback) {

  }

  public static void writeFile(String path, Buffer data, String encoding, NoArgCallback callback) {

  }


}
