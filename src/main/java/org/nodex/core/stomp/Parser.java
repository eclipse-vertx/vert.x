package org.nodex.core.stomp;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.parsetools.RecordParser;

import java.util.HashMap;
import java.util.Map;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:44
 */
public class Parser extends DataHandler {

  public Parser(FrameHandler output) {
    this.output = output;
  }

  private final FrameHandler output;
  private Map<String, String> headers = new HashMap<String, String>();
  private static final byte[] EOL_DELIM = new byte[]{(byte) '\n'};
  private static final byte[] EOM_DELIM = new byte[]{0};
  private final RecordParser frameParser = RecordParser.newDelimited(EOL_DELIM, new DataHandler() {
    public void onData(Buffer line) {
      handleLine(line);
    }
  });
  private String command;
  private boolean inHeaders = true;

  public void onData(Buffer buffer) {
    frameParser.onData(buffer);
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
      headers = new HashMap<String, String>();
      inHeaders = true;
      frameParser.delimitedMode(EOL_DELIM);
      output.onFrame(frame);
    }
  }
}
