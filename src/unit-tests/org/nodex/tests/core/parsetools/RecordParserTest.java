package org.nodex.tests.core.parsetools;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.parsetools.RecordParser;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * User: tim
 * Date: 29/06/11
 * Time: 08:21
 */

public class RecordParserTest {

  @BeforeClass
  public void setUp() {
  }

  @AfterClass
  public void tearDown() {
    System.out.println("tear down");
  }

  @Test
  public void delimitedSingleChar() {
    String line = "This is a line";
    //doTestDelimited(line, "\n", "UTF-8", 1, line);
    doTestDelimited(line, "\n", "UTF-8", line.length() / 2, line);
    //doTestDelimited(line, "\n", "UTF-8", line.length(), line);
    //doTestDelimited(line, "\n", "UTF-8", line.length() - 1, line);
    //doTestDelimited(line, "\n", "UTF-8", line.length() + 1, line);
  }

  private void doTestDelimited(final String input, String delim, final String enc, int chunkSize, final String... output) {
    final String[] results = new String[output.length];
    DataHandler out = new DataHandler() {
      public void onData(Buffer buff) {
        int pos = 0;
        String str = buff.toString(enc);
        results[pos++] = str;
      }
    };
    RecordParser parser = RecordParser.newDelimited(delim, enc, out);
    String inpLine = input + delim;
    int pos = 0;
    while (pos < inpLine.length()) {
      int end = pos + chunkSize -1;
      end = end < inpLine.length() ? end : inpLine.length() - 1;
      String sub = inpLine.substring(pos, end);
      System.out.println("sub is:" + sub + "END");
      parser.onData(Buffer.fromString(sub));
      pos += chunkSize;
    }

    for (int i = 0; i < output.length; i++) {
      assert output[i].equals(results[i]);
    }
  }


}
