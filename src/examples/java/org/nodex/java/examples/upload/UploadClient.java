package org.nodex.java.examples.upload;

import org.nodex.java.core.Completion;
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.SimpleEventHandler;
import org.nodex.java.core.file.AsyncFile;
import org.nodex.java.core.file.FileSystem;
import org.nodex.java.core.http.HttpClientRequest;
import org.nodex.java.core.http.HttpClientResponse;
import org.nodex.java.core.streams.Pump;

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

    final HttpClientRequest req = new org.nodex.java.core.http.HttpClient().setPort(8080).setHost("localhost").put("/some-url", new EventHandler<HttpClientResponse>() {
      public void onEvent(HttpClientResponse response) {
        System.out.println("File uploaded " + response.statusCode);
      }
    });

    String filename = "upload.txt";

    // For a non-chunked upload you need to specify size of upload up-front
    req.putHeader("Content-Length", Files.size(Paths.get(filename)));

    // For a chunked upload you don't need to specify size, just do:
    // req.setChunked(true);

    FileSystem.instance.open(filename, new CompletionHandler<AsyncFile>() {
      public void onEvent(Completion<AsyncFile> completion) {
        final AsyncFile file = completion.result;
        Pump pump = new Pump(file.getReadStream(), req);
        pump.start();

        file.getReadStream().endHandler(new SimpleEventHandler() {
          public void onEvent() {

            file.close(new CompletionHandler<Void>() {
              public void onEvent(Completion<Void> completion) {
                if (completion.succeeded()) {
                  req.end();
                  System.out.println("Sent request");
                } else {
                  completion.exception.printStackTrace(System.err);
                }
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
