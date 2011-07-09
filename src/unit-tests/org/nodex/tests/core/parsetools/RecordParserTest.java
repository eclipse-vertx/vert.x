package org.nodex.tests.core.parsetools;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.parsetools.RecordParser;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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
  public void delimited() {
    delimited("\n");
  }

  private void delimited(String delim) {
    int lines = 100;
    String[] expected = new String[lines];
    StringBuilder inp = new StringBuilder();

    //We create lines of length zero to <lines> and shuffle them
    List<String> lineList = new ArrayList<String>();
    for (int i = 0; i < lines; i++) {
      StringBuilder line = new StringBuilder();
      for (int j = 0; j < i; j++) {
        line.append('X'); //FIXME this should be random
      }
      lineList.add(line.toString());
    }
    Collections.shuffle(lineList);
    expected = lineList.toArray(expected);
    for (int i = 0; i < lines; i++) {
      inp.append(expected[i]).append(delim);
    }

    String sinp = inp.toString();

    //We then try every combination of chunk size up to twice the input string length, again in a random order
    List<Integer> chunkSizes = new ArrayList<Integer>();
    for (int i = 1; i < sinp.length() * 2; i++) {
      chunkSizes.add(i);
    }
    Collections.shuffle(chunkSizes);
    for (Integer i: chunkSizes) {
      doTestDelimited(sinp, "\n", "UTF-8", i, expected);
    }
  }

  private void doTestDelimited(final String input, String delim, final String enc, int chunkSize, final String... expected) {
    final String[] results = new String[expected.length];
    DataHandler out = new DataHandler() {
      int pos;
      public void onData(Buffer buff) {
        String str = buff.toString(enc);
        results[pos++] = str;
      }
    };
    RecordParser parser = RecordParser.newDelimited(delim, enc, out);
    int pos = 0;
    while (pos < input.length()) {
      int end = pos + chunkSize;
      end = end <= input.length() ? end : input.length();
      String sub = input.substring(pos, end);
      //System.out.println("sub is:" + sub + "END");
      parser.onData(Buffer.fromString(sub));
      pos += chunkSize;
    }

    for (int i = 0; i < expected.length; i++) {
      assert expected[i].equals(results[i]) : "Expected:" + expected[i] + " length:" + expected[i].length() + "\nActual:" + results[i];
    }
  }


}
