/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.parsetools.RecordParser;

/**
 * Example using the record parser.
 */
public class ParseToolsExamples {


  public void recordParserExample1() {
    final RecordParser parser = RecordParser.newDelimited("\n", h -> {
      System.out.println(h.toString());
    });

    parser.handle(Buffer.buffer("HELLO\nHOW ARE Y"));
    parser.handle(Buffer.buffer("OU?\nI AM"));
    parser.handle(Buffer.buffer("DOING OK"));
    parser.handle(Buffer.buffer("\n"));
  }

  public void recordParserExample2() {
    RecordParser.newFixed(4, h -> {
      System.out.println(h.toString());
    });
  }

  public void jsonParserExample1() {

    JsonParser parser = JsonParser.newParser();

    // Set handlers for various events
    parser.handler(event -> {
      switch (event.type()) {
        case START_OBJECT:
          // Start an objet
          break;
        case END_OBJECT:
          // End an objet
          break;
        case START_ARRAY:
          // Start an array
          break;
        case END_ARRAY:
          // End an array
          break;
        case VALUE:
          // Handle a value
          String field = event.fieldName();
          if (field != null) {
            // In an object
          } else {
            // In an array or top level
            if (event.isString()) {

            } else {
              // ...
            }
          }
          break;
      }
    });
  }

  public void jsonParserExample2() {

    JsonParser parser = JsonParser.newParser();

    // start array event
    // start object event
    // "firstName":"Bob" event
    parser.handle(Buffer.buffer("[{\"firstName\":\"Bob\","));

    // "lastName":"Morane" event
    // end object event
    parser.handle(Buffer.buffer("\"lastName\":\"Morane\"},"));

    // start object event
    // "firstName":"Luke" event
    // "lastName":"Lucky" event
    // end object event
    parser.handle(Buffer.buffer("{\"firstName\":\"Luke\",\"lastName\":\"Lucky\"}"));

    // end array event
    parser.handle(Buffer.buffer("]"));

    // Always call end
    parser.end();
  }

  public void jsonParserExample3() {

    JsonParser parser = JsonParser.newParser();

    parser.objectValueMode();

    parser.handler(event -> {
      switch (event.type()) {
        case START_ARRAY:
          // Start the array
          break;
        case END_ARRAY:
          // End the array
          break;
        case VALUE:
          // Handle each object
          break;
      }
    });

    parser.handle(Buffer.buffer("[{\"firstName\":\"Bob\"},\"lastName\":\"Morane\"),...]"));
    parser.end();
  }

  public void jsonParserExample4() {

    JsonParser parser = JsonParser.newParser();

    parser.handler(event -> {
      // Start the object

      switch (event.type()) {
        case START_OBJECT:
          // Set object value mode to handle each entry, from now on the parser won't emit start object events
          parser.objectValueMode();
          break;
        case VALUE:
          // Handle each object
          // Get the field in which this object was parsed
          String id = event.fieldName();
          System.out.println("User with id " + id + " : " + event.value());
          break;
        case END_OBJECT:
          // Set the object event mode so the parser emits start/end object events again
          parser.objectEventMode();
          break;
      }
    });

    parser.handle(Buffer.buffer("{\"39877483847\":{\"firstName\":\"Bob\"},\"lastName\":\"Morane\"),...}"));
    parser.end();
  }

  public void jsonParserExample5() {

    JsonParser parser = JsonParser.newParser();

    parser.handler(event -> {
      // Start the object

      switch (event.type()) {
        case START_OBJECT:
          // Set array value mode to handle each entry, from now on the parser won't emit start array events
          parser.arrayValueMode();
          break;
        case VALUE:
          // Handle each array
          // Get the field in which this object was parsed
          System.out.println("Value : " + event.value());
          break;
        case END_OBJECT:
          // Set the array event mode so the parser emits start/end object events again
          parser.arrayEventMode();
          break;
      }
    });

    parser.handle(Buffer.buffer("[0,1,2,3,4,...]"));
    parser.end();
  }

  private static class User {
    private String firstName;
    private String lastName;
  }

  public void jsonParserExample6(JsonParser parser) {
    parser.handler(event -> {
      // Handle each object
      // Get the field in which this object was parsed
      String id = event.fieldName();
      User user = event.mapTo(User.class);
      System.out.println("User with id " + id + " : " + user.firstName + " " + user.lastName);
    });
  }

  public void jsonParserExample7() {

    JsonParser parser = JsonParser.newParser();

    parser.exceptionHandler(err -> {
      // Catch any parsing or decoding error
    });
  }
}
