package org.nodex.examples.upload;

import org.nodex.core.CompletionHandler;
import org.nodex.core.EventHandler;
import org.nodex.core.SimpleEventHandler;
import org.nodex.core.NodexMain;
import org.nodex.core.file.AsyncFile;
import org.nodex.core.file.FileSystem;
import org.nodex.core.http.HttpClientRequest;
import org.nodex.core.http.HttpClientResponse;
import org.nodex.core.streams.Pump;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * User: tim
 * Date: 25/08/11
 * Time: 11:30
 */
public class UploadClient extends NodexMain {
  public static void main(String[] args) throws Exception {
    new UploadClient().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {

    final HttpClientRequest req = new org.nodex.core.http.HttpClient().setPort(8080).setHost("localhost").put("/some-url", new EventHandler<HttpClientResponse>() {
      public void onEvent(HttpClientResponse response) {
        System.out.println("File uploaded " + response.statusCode);
      }
    });

    String filename = "upload.txt";

    // For a non-chunked upload you need to specify size of upload up-front
    req.setContentLength(Files.size(Paths.get(filename)));

    // For a chunked upload you don't need to specify size, just do:
    // req.setChunked(true);

    FileSystem.instance.open(filename, new CompletionHandler<AsyncFile>() {
      public void onCompletion(final AsyncFile file) {

        Pump pump = new Pump(file.getReadStream(), req);
        pump.start();

        file.getReadStream().endHandler(new SimpleEventHandler() {
          public void onEvent() {

            file.close(new CompletionHandler<Void>() {
              public void onCompletion(Void v) {
                req.end();
                System.out.println("Sent request");
              }
              public void onException(Exception e) {
                e.printStackTrace(System.err);
              }
            });
          }
        });
      }

      public void onException(Exception e) {
        e.printStackTrace();
      }
    });

  }
}
