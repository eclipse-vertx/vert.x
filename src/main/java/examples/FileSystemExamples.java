/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;

/**
 * Created by tim on 09/01/15.
 */
public class FileSystemExamples {

  public void example1(Vertx vertx) {
    FileSystem fs = vertx.fileSystem();

    // Copy file from foo.txt to bar.txt
    fs.copy("foo.txt", "bar.txt")
      .onComplete(res -> {
        if (res.succeeded()) {
          // Copied ok!
        } else {
          // Something went wrong
        }
      });
  }

  public void example2(Vertx vertx) {
    FileSystem fs = vertx.fileSystem();

    // Copy file from foo.txt to bar.txt synchronously
    fs.copyBlocking("foo.txt", "bar.txt");
  }

  public void example3(FileSystem fileSystem) {
    OpenOptions options = new OpenOptions();
    fileSystem
      .open("myfile.txt", options)
      .onComplete(res -> {
        if (res.succeeded()) {
          AsyncFile file = res.result();
        } else {
          // Something went wrong!
        }
      });
  }

  public void asyncAPIExamples(Vertx vertx) {
    // Read a file
    vertx.fileSystem()
      .readFile("target/classes/readme.txt")
      .onComplete(result -> {
        if (result.succeeded()) {
          System.out.println(result.result());
        } else {
          System.err.println("Oh oh ..." + result.cause());
        }
      });

    // Copy a file
    vertx.fileSystem()
      .copy("target/classes/readme.txt", "target/classes/readme2.txt")
      .onComplete(result -> {
        if (result.succeeded()) {
          System.out.println("File copied");
        } else {
          System.err.println("Oh oh ..." + result.cause());
        }
      });

    // Write a file
    vertx.fileSystem()
      .writeFile("target/classes/hello.txt", Buffer.buffer("Hello"))
      .onComplete(result -> {
        if (result.succeeded()) {
          System.out.println("File written");
        } else {
          System.err.println("Oh oh ..." + result.cause());
        }
      });

    // Check existence and delete
    vertx.fileSystem()
      .exists("target/classes/junk.txt")
      .compose(exist -> {
        if (exist) {
          return vertx.fileSystem().delete("target/classes/junk.txt");
        } else {
          return Future.failedFuture("File does not exist");
        }
      }).onComplete(result -> {
        if (result.succeeded()) {
          System.out.println("File deleted");
        } else {
          System.err.println("Oh oh ... - cannot delete the file: " + result.cause().getMessage());
        }
      });
  }

  public void asyncFileWrite(Vertx vertx) {
    vertx.fileSystem()
      .open("target/classes/hello.txt", new OpenOptions())
      .onComplete(result -> {
        if (result.succeeded()) {
          AsyncFile file = result.result();
          Buffer buff = Buffer.buffer("foo");
          for (int i = 0; i < 5; i++) {
            file
              .write(buff, buff.length() * i)
              .onComplete(ar -> {
                if (ar.succeeded()) {
                  System.out.println("Written ok!");
                  // etc
                } else {
                  System.err.println("Failed to write: " + ar.cause());
                }
              });
          }
        } else {
          System.err.println("Cannot open file " + result.cause());
        }
      });
  }

  public void asyncFileRead(Vertx vertx) {
    vertx.fileSystem()
      .open("target/classes/les_miserables.txt", new OpenOptions())
      .onComplete(result -> {
        if (result.succeeded()) {
          AsyncFile file = result.result();
          Buffer buff = Buffer.buffer(1000);
          for (int i = 0; i < 10; i++) {
            file
              .read(buff, i * 100, i * 100, 100)
              .onComplete(ar -> {
                if (ar.succeeded()) {
                  System.out.println("Read ok!");
                } else {
                  System.err.println("Failed to write: " + ar.cause());
                }
              });
          }
        } else {
          System.err.println("Cannot open file " + result.cause());
        }
      });
  }

  public void asyncFilePipe(Vertx vertx) {
    final AsyncFile output = vertx.fileSystem().openBlocking("target/classes/plagiary.txt", new OpenOptions());

    vertx.fileSystem()
      .open("target/classes/les_miserables.txt", new OpenOptions())
      .compose(file -> file
        .pipeTo(output)
        .eventually(v -> file.close()))
      .onComplete(result -> {
        if (result.succeeded()) {
          System.out.println("Copy done");
        } else {
          System.err.println("Cannot copy file " + result.cause().getMessage());
        }
      });
  }
}

