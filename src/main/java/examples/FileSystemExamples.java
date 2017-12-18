/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.streams.Pump;

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

    public void asyncAPIExamples() {
        Vertx vertx = Vertx.vertx();

        // Read a file
        vertx.fileSystem().readFile("target/classes/readme.txt", result -> {
            if (result.succeeded()) {
                System.out.println(result.result());
            } else {
                System.err.println("Oh oh ..." + result.cause());
            }
        });

        // Copy a file
        vertx.fileSystem().copy("target/classes/readme.txt", "target/classes/readme2.txt", result -> {
            if (result.succeeded()) {
                System.out.println("File copied");
            } else {
                System.err.println("Oh oh ..." + result.cause());
            }
        });

        // Write a file
        vertx.fileSystem().writeFile("target/classes/hello.txt", Buffer.buffer("Hello"), result -> {
            if (result.succeeded()) {
                System.out.println("File written");
            } else {
                System.err.println("Oh oh ..." + result.cause());
            }
        });

        // Check existence and delete
        vertx.fileSystem().exists("target/classes/junk.txt", result -> {
            if (result.succeeded() && result.result()) {
                vertx.fileSystem().delete("target/classes/junk.txt", r -> {
                    System.out.println("File deleted");
                });
            } else {
                System.err.println("Oh oh ... - cannot delete the file: " + result.cause());
            }
        });
    }

    public void asyncFileWrite() {
        Vertx vertx = Vertx.vertx();
        vertx.fileSystem().open("target/classes/hello.txt", new OpenOptions(), result -> {
            if (result.succeeded()) {
                AsyncFile file = result.result();
                Buffer buff = Buffer.buffer("foo");
                for (int i = 0; i < 5; i++) {
                    file.write(buff, buff.length() * i, ar -> {
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

    public void asyncFileRead() {
        Vertx vertx = Vertx.vertx();
        vertx.fileSystem().open("target/classes/les_miserables.txt", new OpenOptions(), result -> {
            if (result.succeeded()) {
                AsyncFile file = result.result();
                Buffer buff = Buffer.buffer(1000);
                for (int i = 0; i < 10; i++) {
                    file.read(buff, i * 100, i * 100, 100, ar -> {
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

    public void asyncFilePump() {
        Vertx vertx = Vertx.vertx();
        final AsyncFile output = vertx.fileSystem().openBlocking("target/classes/plagiary.txt", new OpenOptions());

        vertx.fileSystem().open("target/classes/les_miserables.txt", new OpenOptions(), result -> {
            if (result.succeeded()) {
                AsyncFile file = result.result();
                Pump.pump(file, output).start();
                file.endHandler((r) -> {
                    System.out.println("Copy done");
                });
            } else {
                System.err.println("Cannot open file " + result.cause());
            }
        });
    }
}

