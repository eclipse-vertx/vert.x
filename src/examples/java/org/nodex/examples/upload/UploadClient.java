package org.nodex.examples.upload;

import org.nodex.core.CompletionHandlerWithResult;
import org.nodex.core.NodexMain;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.file.AsyncFile;
import org.nodex.core.file.FileSystem;
import org.nodex.core.http.HttpClientRequest;
import org.nodex.core.http.HttpClientResponse;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpResponseHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.streams.Pump;

import java.util.UUID;

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

    final HttpClientRequest req = new org.nodex.core.http.HttpClient().setPort(8080).setHost("localhost").put("/some-url", new HttpResponseHandler() {
      public void onResponse(HttpClientResponse response) {
        System.out.println("Got response " + response.statusCode);
      }
    });

    String filename = "upload.txt";

    req.setChunked(true);

    FileSystem.instance.open(filename, new CompletionHandlerWithResult<AsyncFile>() {
      public void onCompletion(AsyncFile file) {

        Pump pump = new Pump(file.getReadStream(), req);
        pump.start();

        file.getReadStream().endHandler(new Runnable() {
          public void run() {
            req.end();
          }
        });
      }

      public void onException(Exception e) {
        e.printStackTrace();
      }
    });

    //req.end();
  }
}
