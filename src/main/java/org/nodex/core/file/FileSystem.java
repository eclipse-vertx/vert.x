package org.nodex.core.file;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * User: timfox
 * Date: 29/06/2011
 * Time: 21:52
 */
public class FileSystem {

  public static FileSystem instance = new FileSystem();

  // File meta operations

  public void rename(String from, String to, Runnable onCompletion) {

  }

  public void truncate(String path, int len, Runnable onCompletion) {

  }

  public void chmod(String path, int mode, Runnable onCompletion) {

  }

  public void link(String src, String dest, Runnable onCompletion) {

  }

  public void symlink(String src, String dest, Runnable onCompletion) {

  }

  public void unlink(String path, Runnable onCompletion) {

  }

  public void mkdir(String path, int mode, Runnable onCompletion) {

  }

  public void readDir(String path, Runnable onCompletion) {

  }

  public void stat(String path, StatsHandler handler) {

  }

  public void lstat(String path, StatsHandler handler) {

  }

  public void fstat(int fd, StatsHandler handler) {

  }

  // Close and open

  public void close(int fd, Runnable onCompletion) {

  }

  public void open(String path, int flags, int mode, OpenHandler handler) {

  }

  // Random access

  public void write(int fd, Buffer buffer, int offset, int length, int position, Runnable onCompletion) {

  }

  public void read(int fd, Buffer buffer, int offset, int length, int position, Runnable onCompletion) {

  }

  // Read and write entire files (data will arrive in chunks)

  public void readFile(String path, String encoding, DataHandler handler) {

  }

  public void readFile(String path, DataHandler dataHandler) {
    //For now we just fake this
    try {
      File f = new File(path);
      byte[] bytes = new byte[(int) f.length()];
      FileInputStream fis = new FileInputStream(f);
      fis.read(bytes);
      fis.close();
      Buffer buff = Buffer.newWrapped(bytes);
      dataHandler.onData(buff);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void writeFile(String path, String data, String encoding, Runnable onCompletion) {

  }

  public void writeFile(String path, Buffer data, Runnable onCompletion) {

  }


}
