/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.addons.old.stomp;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.parsetools.RecordParser;

import java.util.HashMap;
import java.util.Map;

public class Parser implements Handler<Buffer> {

  public Parser(FrameHandler output) {
    this.output = output;
  }

  private final FrameHandler output;
  private Map<String, String> headers = new HashMap<>();
  private static final byte[] EOL_DELIM = new byte[]{'\n'};
  private static final byte[] EOM_DELIM = new byte[]{0};
  private final RecordParser frameParser = RecordParser.newDelimited(EOL_DELIM, new Handler<Buffer>() {
    public void handle(Buffer line) {
      handleLine(line);
    }
  });
  private String command;
  private boolean inHeaders = true;

  public void handle(Buffer buffer) {
    frameParser.handle(buffer);
  }

  private void handleLine(Buffer buffer) {
    String line = buffer.toString().trim();
    if (inHeaders) {
      if (command == null) {
        command = line;
      } else if ("".equals(line)) {
        //End of headers
        inHeaders = false;
        String sHeader = headers.get("content-length");
        if (sHeader != null) {
          int contentLength = Integer.valueOf(sHeader);
          frameParser.fixedSizeMode(contentLength);
        } else {
          frameParser.delimitedMode(EOM_DELIM);
        }
      } else {
        String[] aline = line.split(":");
        headers.put(aline[0], aline[1]);
      }
    } else {
      Frame frame = new Frame(command, headers, buffer);
      command = null;
      headers = new HashMap<>();
      inHeaders = true;
      frameParser.delimitedMode(EOL_DELIM);
      output.onFrame(frame);
    }
  }
}
