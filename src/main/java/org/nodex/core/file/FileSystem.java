package org.nodex.core.file;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.buffer.Buffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * User: timfox
 * Date: 29/06/2011
 * Time: 21:52
 */
public class FileSystem {

  public static FileSystem instance = new FileSystem();

  // File meta operations

  public void rename(String from, String to, NoArgCallback onCompletion) {

  }

  public void truncate(String path, int len, NoArgCallback onCompletion) {

  }

  public void chmod(String path, int mode, NoArgCallback onCompletion) {

  }

  public void link(String src, String dest, NoArgCallback onCompletion) {

  }

  public void symlink(String src, String dest, NoArgCallback onCompletion) {

  }

  public void unlink(String path, NoArgCallback onCompletion) {

  }

  public void mkdir(String path, int mode, NoArgCallback onCompletion) {

  }

  public void readDir(String path, NoArgCallback onCompletion) {

  }

  public void stat(String path, Callback<Map<String, String>> callback) {

  }

  public void lstat(String path, Callback<Map<String, String>> callback) {

  }

  public void fstat(int fd, Callback<Map<String, String>> callback) {

  }

  // Close and open

  public void close(int fd, NoArgCallback onCompletion) {

  }

  public void open(String path, int flags, int mode, Callback<Integer> onCompletion) {

  }

  // Random access

  public void write(int fd, Buffer buffer, int offset, int length, int position, NoArgCallback onCompletion) {

  }

  public void read(int fd, Buffer buffer, int offset, int length, int position, NoArgCallback onCompletion) {

  }

  // Read and write entire files (data will arrive in chunks)

  public void readFile(String path, String encoding, Callback<String> dataCallback) {

  }

  public void readFile(String path, Callback<Buffer> dataCallback) {
    //For now we just fake this
    System.out.println("Reading file " + path);
    try {
      File f= new File(path);
      byte[] bytes = new byte[(int)f.length()];
      FileInputStream fis = new FileInputStream(f);
      fis.read(bytes);
      fis.close();
      Buffer buff = Buffer.newWrapped(bytes);
      dataCallback.onEvent(buff);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void writeFile(String path, String data, String encoding, NoArgCallback onCompletion) {

  }

  public void writeFile(String path, Buffer data, NoArgCallback onCompletion) {

  }


}
