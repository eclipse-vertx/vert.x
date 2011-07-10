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
  }


  @Test
  public void doDelimited() {
    delimitedBytes(new byte[] {(byte)'\n'});
    delimitedBytes(new byte[] {(byte)'\r', (byte)'\n'});
    delimitedBytes(new byte[] {0, 3, 2, 5, 6, 4, 6});

    //TODO some random chunk sizes for a specific input


  }

  /*
  This test creates some input data which contains <lines> lines of lengths in randm order between 0 and lines
  And then passes them into the RecordParser in chunk sizes from 0 to twice the total input buffer size
   */
  private void delimitedBytes(byte[] delim) {
    int lines = 100;
    Buffer[] expected = new Buffer[lines];

    //We create lines of length zero to <lines> and shuffle them
    List<Buffer> lineList = new ArrayList<Buffer>();
    for (int i = 0; i < lines; i++) {
      byte[] line = new byte[i];
      for (int j = 0; j < i; j++) {
        //Choose a random byte which isn't a delim
        byte rand;
        do {
          rand = (byte)((int)(Math.random() * 255) - 128);
        } while (rand == delim[0]); // Make sure we don't get a delim by chance
        line[j] = rand;
      }
      lineList.add(Buffer.newWrapped(line));
    }
    Collections.shuffle(lineList);
    expected = lineList.toArray(expected);
    int totLength = lines * (lines - 1) / 2; // The sum of 0...(lines - 1)
    Buffer inp = Buffer.newDynamic(totLength + lines * delim.length);
    for (int i = 0; i < lines; i++) {
      inp.append(expected[i]);
      inp.append(Buffer.newWrapped(delim));
    }

    //We then try every combination of chunk size up to twice the input string length
    for (int i = 1; i < inp.length() * 2; i++) {
      doTestDelimitedBytes(inp, delim, new Integer[] {i}, expected);
    }

    //Then we try a sequence of random chunk sizes
    List<Integer> chunkSizes = new ArrayList<Integer>();
    for (int i = 1; i < lines / 5; i++) {
      chunkSizes.add(i);
    }
    //Repeat a few times
    for (int i = 0; i < 10; i++) {
      Collections.shuffle(chunkSizes);
      doTestDelimitedBytes(inp, delim, chunkSizes.toArray(new Integer[] {}), expected);
    }
  }

  private void doTestDelimitedBytes(final Buffer input, byte[] delim, Integer[] chunkSizes, final Buffer... expected) {
    final Buffer[] results = new Buffer[expected.length];
    DataHandler out = new DataHandler() {
      int pos;
      public void onData(Buffer buff) {
        results[pos++] = buff;
      }
    };
    RecordParser parser = RecordParser.newDelimited(delim, out);
    int pos = 0;
    int chunkPos = 0;
    while (pos < input.length()) {
      int chunkSize = chunkSizes[chunkPos++];
      if (chunkPos == chunkSizes.length) chunkPos = 0;
      int end = pos + chunkSize;
      end = end <= input.length() ? end : input.length();
      Buffer sub = input.slice(pos, end);
      parser.onData(sub);
      pos += chunkSize;
    }

    for (int i = 0; i < expected.length; i++) {
      assert buffersEqual(expected[i], results[i]) : "Expected:" + expected[i] + " length:" + expected[i].length() +
          " Actual:" + results[i] + " length:" + results[i].length();
    }
  }

  private boolean buffersEqual(Buffer b1, Buffer b2) {
    if (b1.length() != b2.length()) return false;
    for (int i = 0; i < b1.length(); i++) {
      if (b1.byteAt(i) != b2.byteAt(i)) return false;
    }
    return true;
  }

}
