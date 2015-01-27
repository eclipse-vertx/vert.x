/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.net.NetSocket;

/**
 * Created by tim on 09/01/15.
 */
public class FileSystemExamples {

  public void example1(Vertx vertx) {
    FileSystem fs = vertx.fileSystem();

    // Copy file from foo.txt to bar.txt
    fs.copy("foo.txt", "bar.txt", res -> {
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
    fileSystem.open("myfile.txt", options, res -> {
      if (res.succeeded()) {
        AsyncFile file = res.result();
      } else {
        // Something went wrong!
      }
    });
  }


}
