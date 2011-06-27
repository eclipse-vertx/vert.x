package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.parsetools.LineSplitter;

import java.util.HashMap;
import java.util.Map;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:44
 */
public class Parser extends Callback<Buffer> {

  public Parser(Callback<Frame> output) {
    this.output = output;
  }

  private Callback<Buffer> headerHandler = new Callback<Buffer>() {
    public void onEvent(Buffer buffer) {
      handleLine(buffer);
    }
  };

  private Callback<Buffer> bodyHandler = new Callback<Buffer>() {
    public void onEvent(Buffer buffer) {
      handleBody(buffer);
    }
  };

  private final Callback<Frame> output;
  private Map<String, String> headers = new HashMap<String, String>();
  private LineSplitter headerParser = new LineSplitter((byte)'\n', headerHandler);
  private LineSplitter bodyParser = new LineSplitter((byte)0, bodyHandler);
  private String command;
  private boolean inHeaders = true;

  private void handleLine(Buffer buffer) {
    String line = buffer.toString().trim();
    if (command == null) {
      command = line;
    }
    else if ("".equals(line)) {
      //End of headers
      bodyParser.reset(headerParser.remainingBuffer());
      inHeaders = false;
    } else {
      String[] aline = line.split(":");
      headers.put(aline[0], aline[1]);
    }
  }

  private void handleBody(Buffer buffer) {
    Frame frame = new Frame(command, headers, buffer);
    command = null;
    headers.clear();
    inHeaders = true;
    headerParser.reset(bodyParser.remainingBuffer());
    output.onEvent(frame);
  }

  public void onEvent(Buffer buffer) {
    if (inHeaders) {
      headerParser.onEvent(buffer);
    } else {
      bodyParser.onEvent(buffer);
    }
  }
}
