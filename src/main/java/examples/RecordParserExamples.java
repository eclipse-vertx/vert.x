package examples;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;

/**
 * Example using the record parser.
 */
public class RecordParserExamples {


  public void example1() {
    final RecordParser parser = RecordParser.newDelimited("\n", h -> {
      System.out.println(h.toString());
    });

    parser.handle(Buffer.buffer("HELLO\nHOW ARE Y"));
    parser.handle(Buffer.buffer("OU?\nI AM"));
    parser.handle(Buffer.buffer("DOING OK"));
    parser.handle(Buffer.buffer("\n"));
  }

  public void example2() {
    RecordParser.newFixed(4, h -> {
      System.out.println(h.toString());
    });
  }
}
