package org.vertx.java.core.json.impl;

/**
 * <p>Encodes and decodes to and from Base64 notation.</p>
 * <p>Homepage: <a href="http://iharder.net/base64">http://iharder.net/base64</a>.</p>
 * <p></p>
 * <p>The <tt>options</tt> parameter, which appears in a few places, is used to pass
 * several pieces of information to the encoder. In the "higher level" methods such as
 * encodeBytes( bytes, options ) the options parameter can be used to indicate such
 * things as first gzipping the bytes before encoding them, not inserting linefeeds
 * (though that breaks strict Base64 compatibility), and encoding using the URL-safe
 * and Ordered dialects.</p>
 * <p></p>
 * <p>The constants defined in Base64 can be OR-ed together to combine options, so you
 * might make a call like this:</p>
 * <p></p>
 * <code>String encoded = Base64.encodeBytes( mybytes, Base64.GZIP | Base64.DONT_BREAK_LINES );</code>
 * <p></p>
 * <p>to compress the data before encoding it and then making the output have no newline characters.</p>
 * <p></p>
 * <p></p>
 * <p>
 * Change Log:
 * </p>
 * <ul>
 * <li>v2.2.2 - Fixed encodeFileToFile and decodeFileToFile to use the
 * Base64.InputStream class to encode and decode on the fly which uses
 * less memory than encoding/decoding an entire file into memory before writing.</li>
 * <li>v2.2.1 - Fixed bug using URL_SAFE and ORDERED encodings. Fixed bug
 * when using very small files (~&lt; 40 bytes).</li>
 * <li>v2.2 - Added some helper methods for encoding/decoding directly from
 * one file to the next. Also added a main() method to support command line
 * encoding/decoding from one file to the next. Also added these Base64 dialects:
 * <ol>
 * <li>The default is RFC3548 format.</li>
 * <li>Calling Base64.setFormat(Base64.BASE64_FORMAT.URLSAFE_FORMAT) generates
 * URL and file name friendly format as described in Section 4 of RFC3548.
 * http://www.faqs.org/rfcs/rfc3548.html</li>
 * <li>Calling Base64.setFormat(Base64.BASE64_FORMAT.ORDERED_FORMAT) generates
 * URL and file name friendly format that preserves lexical ordering as described
 * in http://www.faqs.org/qa/rfcc-1940.html</li>
 * </ol>
 * Special thanks to Jim Kellerman at <a href="http://www.powerset.com/">http://www.powerset.com/</a>
 * for contributing the new Base64 dialects.
 * </li>
 * <li>v2.1 - Cleaned up javadoc comments and unused variables and methods. Added
 * some convenience methods for reading and writing to and from files.</li>
 * <li>v2.0.2 - Now specifies UTF-8 encoding in places where the code fails on systems
 * with other encodings (like EBCDIC).</li>
 * <li>v2.0.1 - Fixed an error when decoding a single byte, that is, when the
 * encoded data was a single byte.</li>
 * <li>v2.0 - I got rid of methods that used booleans to set options.
 * Now everything is more consolidated and cleaner. The code now detects
 * when data that's being decoded is gzip-compressed and will decompress it
 * automatically. Generally things are cleaner. You'll probably have to
 * change some method calls that you were making to support the new
 * options format (<tt>int</tt>s that you "OR" together).</li>
 * <li>v1.5.1 - Fixed bug when decompressing and decoding to a
 * byte[] using <tt>decode( String s, boolean gzipCompressed )</tt>.
 * Added the ability to "suspend" encoding in the Output Stream so
 * you can turn on and off the encoding if you need to embed base64
 * data in an otherwise "normal" stream (like an XML file).</li>
 * <li>v1.5 - Output stream pases on flush() command but doesn't do anything itself.
 * This helps when using GZIP streams.
 * Added the ability to GZip-compress objects before encoding them.</li>
 * <li>v1.4 - Added helper methods to read/write files.</li>
 * <li>v1.3.6 - Fixed OutputStream.flush() so that 'position' is reset.</li>
 * <li>v1.3.5 - Added flag to turn on and off line breaks. Fixed bug in input stream
 * where last buffer being read, if not completely full, was not returned.</li>
 * <li>v1.3.4 - Fixed when "improperly padded stream" error was thrown at the wrong time.</li>
 * <li>v1.3.3 - Fixed I/O streams which were totally messed up.</li>
 * </ul>
 * <p></p>
 * <p>
 * I am placing this code in the Public Domain. Do with it as you will.
 * This software comes with no guarantees or warranties but with
 * plenty of well-wishing instead!
 * Please visit <a href="http://iharder.net/base64">http://iharder.net/base64</a>
 * periodically to check for updates or to contribute improvements.
 * </p>
 *
 * @author Robert Harder
 * @author rob@iharder.net
 * @version 2.2.2
 */
public class Base64 {

  /* ********  P U B L I C   F I E L D S  ******** */

  /**
   * No options specified. Value is zero.
   */
  public final static int NO_OPTIONS = 0;

  /**
   * Specify encoding.
   */
  public final static int ENCODE = 1;

  /**
   * Specify decoding.
   */
  public final static int DECODE = 0;

  /**
   * Specify that data should be gzip-compressed.
   */
  public final static int GZIP = 2;

  /**
   * Don't break lines when encoding (violates strict Base64 specification)
   */
  public final static int DONT_BREAK_LINES = 8;

  /**
   * Encode using Base64-like encoding that is URL- and Filename-safe as described
   * in Section 4 of RFC3548:
   * <a href="http://www.faqs.org/rfcs/rfc3548.html">http://www.faqs.org/rfcs/rfc3548.html</a>.
   * It is important to note that data encoded this way is <em>not</em> officially valid Base64,
   * or at the very least should not be called Base64 without also specifying that is
   * was encoded using the URL- and Filename-safe dialect.
   */
  public final static int URL_SAFE = 16;

  /**
   * Encode using the special "ordered" dialect of Base64 described here:
   * <a href="http://www.faqs.org/qa/rfcc-1940.html">http://www.faqs.org/qa/rfcc-1940.html</a>.
   */
  public final static int ORDERED = 32;

  /* ********  P R I V A T E   F I E L D S  ******** */

  /**
   * Maximum line length (76) of Base64 output.
   */
  private final static int MAX_LINE_LENGTH = 76;

  /**
   * The equals sign (=) as a byte.
   */
  private final static byte EQUALS_SIGN = (byte) '=';

  /**
   * The new line character (\n) as a byte.
   */
  private final static byte NEW_LINE = (byte) '\n';

  /**
   * Preferred encoding.
   */
  private final static String PREFERRED_ENCODING = "UTF-8";

  // I think I end up not using the BAD_ENCODING indicator.
  // private final static byte BAD_ENCODING = -9; // Indicates error in encoding
  private final static byte WHITE_SPACE_ENC = -5; // Indicates white space in encoding

  private final static byte EQUALS_SIGN_ENC = -1; // Indicates equals sign in encoding

  /* ********  S T A N D A R D   B A S E 6 4   A L P H A B E T  ******** */

  /**
   * The 64 valid Base64 values.
   */
  // private final static byte[] ALPHABET;
  /* Host platform me be something funny like EBCDIC, so we hardcode these values. */
  private final static byte[] _STANDARD_ALPHABET = {(byte) 'A',
      (byte) 'B',
      (byte) 'C',
      (byte) 'D',
      (byte) 'E',
      (byte) 'F',
      (byte) 'G',
      (byte) 'H',
      (byte) 'I',
      (byte) 'J',
      (byte) 'K',
      (byte) 'L',
      (byte) 'M',
      (byte) 'N',
      (byte) 'O',
      (byte) 'P',
      (byte) 'Q',
      (byte) 'R',
      (byte) 'S',
      (byte) 'T',
      (byte) 'U',
      (byte) 'V',
      (byte) 'W',
      (byte) 'X',
      (byte) 'Y',
      (byte) 'Z',
      (byte) 'a',
      (byte) 'b',
      (byte) 'c',
      (byte) 'd',
      (byte) 'e',
      (byte) 'f',
      (byte) 'g',
      (byte) 'h',
      (byte) 'i',
      (byte) 'j',
      (byte) 'k',
      (byte) 'l',
      (byte) 'm',
      (byte) 'n',
      (byte) 'o',
      (byte) 'p',
      (byte) 'q',
      (byte) 'r',
      (byte) 's',
      (byte) 't',
      (byte) 'u',
      (byte) 'v',
      (byte) 'w',
      (byte) 'x',
      (byte) 'y',
      (byte) 'z',
      (byte) '0',
      (byte) '1',
      (byte) '2',
      (byte) '3',
      (byte) '4',
      (byte) '5',
      (byte) '6',
      (byte) '7',
      (byte) '8',
      (byte) '9',
      (byte) '+',
      (byte) '/'};

  /**
   * Translates a Base64 value to either its 6-bit reconstruction value
   * or a negative number indicating some other meaning.
   */
  private final static byte[] _STANDARD_DECODABET = {-9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 0 - 8
      -5,
      -5, // Whitespace: Tab and Linefeed
      -9,
      -9, // Decimal 11 - 12
      -5, // Whitespace: Carriage Return
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 14 - 26
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 27 - 31
      -5, // Whitespace: Space
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 33 - 42
      62, // Plus sign at decimal 43
      -9,
      -9,
      -9, // Decimal 44 - 46
      63, // Slash at decimal 47
      52,
      53,
      54,
      55,
      56,
      57,
      58,
      59,
      60,
      61, // Numbers zero through nine
      -9,
      -9,
      -9, // Decimal 58 - 60
      -1, // Equals sign at decimal 61
      -9,
      -9,
      -9, // Decimal 62 - 64
      0,
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      9,
      10,
      11,
      12,
      13, // Letters 'A' through 'N'
      14,
      15,
      16,
      17,
      18,
      19,
      20,
      21,
      22,
      23,
      24,
      25, // Letters 'O' through 'Z'
      -9,
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 91 - 96
      26,
      27,
      28,
      29,
      30,
      31,
      32,
      33,
      34,
      35,
      36,
      37,
      38, // Letters 'a' through 'm'
      39,
      40,
      41,
      42,
      43,
      44,
      45,
      46,
      47,
      48,
      49,
      50,
      51, // Letters 'n' through 'z'
      -9,
      -9,
      -9,
      -9 // Decimal 123 - 126
      /*,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 127 - 139
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 140 - 152
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 153 - 165
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 166 - 178
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 179 - 191
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 192 - 204
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 205 - 217
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 218 - 230
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 231 - 243
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9         // Decimal 244 - 255 */
  };

  /* ********  U R L   S A F E   B A S E 6 4   A L P H A B E T  ******** */

  /**
   * Used in the URL- and Filename-safe dialect described in Section 4 of RFC3548:
   * <a href="http://www.faqs.org/rfcs/rfc3548.html">http://www.faqs.org/rfcs/rfc3548.html</a>.
   * Notice that the last two bytes become "hyphen" and "underscore" instead of "plus" and "slash."
   */
  private final static byte[] _URL_SAFE_ALPHABET = {(byte) 'A',
      (byte) 'B',
      (byte) 'C',
      (byte) 'D',
      (byte) 'E',
      (byte) 'F',
      (byte) 'G',
      (byte) 'H',
      (byte) 'I',
      (byte) 'J',
      (byte) 'K',
      (byte) 'L',
      (byte) 'M',
      (byte) 'N',
      (byte) 'O',
      (byte) 'P',
      (byte) 'Q',
      (byte) 'R',
      (byte) 'S',
      (byte) 'T',
      (byte) 'U',
      (byte) 'V',
      (byte) 'W',
      (byte) 'X',
      (byte) 'Y',
      (byte) 'Z',
      (byte) 'a',
      (byte) 'b',
      (byte) 'c',
      (byte) 'd',
      (byte) 'e',
      (byte) 'f',
      (byte) 'g',
      (byte) 'h',
      (byte) 'i',
      (byte) 'j',
      (byte) 'k',
      (byte) 'l',
      (byte) 'm',
      (byte) 'n',
      (byte) 'o',
      (byte) 'p',
      (byte) 'q',
      (byte) 'r',
      (byte) 's',
      (byte) 't',
      (byte) 'u',
      (byte) 'v',
      (byte) 'w',
      (byte) 'x',
      (byte) 'y',
      (byte) 'z',
      (byte) '0',
      (byte) '1',
      (byte) '2',
      (byte) '3',
      (byte) '4',
      (byte) '5',
      (byte) '6',
      (byte) '7',
      (byte) '8',
      (byte) '9',
      (byte) '-',
      (byte) '_'};

  /**
   * Used in decoding URL- and Filename-safe dialects of Base64.
   */
  private final static byte[] _URL_SAFE_DECODABET = {-9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 0 - 8
      -5,
      -5, // Whitespace: Tab and Linefeed
      -9,
      -9, // Decimal 11 - 12
      -5, // Whitespace: Carriage Return
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 14 - 26
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 27 - 31
      -5, // Whitespace: Space
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 33 - 42
      -9, // Plus sign at decimal 43
      -9, // Decimal 44
      62, // Minus sign at decimal 45
      -9, // Decimal 46
      -9, // Slash at decimal 47
      52,
      53,
      54,
      55,
      56,
      57,
      58,
      59,
      60,
      61, // Numbers zero through nine
      -9,
      -9,
      -9, // Decimal 58 - 60
      -1, // Equals sign at decimal 61
      -9,
      -9,
      -9, // Decimal 62 - 64
      0,
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      9,
      10,
      11,
      12,
      13, // Letters 'A' through 'N'
      14,
      15,
      16,
      17,
      18,
      19,
      20,
      21,
      22,
      23,
      24,
      25, // Letters 'O' through 'Z'
      -9,
      -9,
      -9,
      -9, // Decimal 91 - 94
      63, // Underscore at decimal 95
      -9, // Decimal 96
      26,
      27,
      28,
      29,
      30,
      31,
      32,
      33,
      34,
      35,
      36,
      37,
      38, // Letters 'a' through 'm'
      39,
      40,
      41,
      42,
      43,
      44,
      45,
      46,
      47,
      48,
      49,
      50,
      51, // Letters 'n' through 'z'
      -9,
      -9,
      -9,
      -9 // Decimal 123 - 126
      /*,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 127 - 139
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 140 - 152
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 153 - 165
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 166 - 178
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 179 - 191
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 192 - 204
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 205 - 217
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 218 - 230
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 231 - 243
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9         // Decimal 244 - 255 */
  };

  /* ********  O R D E R E D   B A S E 6 4   A L P H A B E T  ******** */

  /**
   * I don't get the point of this technique, but it is described here:
   * <a href="http://www.faqs.org/qa/rfcc-1940.html">http://www.faqs.org/qa/rfcc-1940.html</a>.
   */
  private final static byte[] _ORDERED_ALPHABET = {(byte) '-',
      (byte) '0',
      (byte) '1',
      (byte) '2',
      (byte) '3',
      (byte) '4',
      (byte) '5',
      (byte) '6',
      (byte) '7',
      (byte) '8',
      (byte) '9',
      (byte) 'A',
      (byte) 'B',
      (byte) 'C',
      (byte) 'D',
      (byte) 'E',
      (byte) 'F',
      (byte) 'G',
      (byte) 'H',
      (byte) 'I',
      (byte) 'J',
      (byte) 'K',
      (byte) 'L',
      (byte) 'M',
      (byte) 'N',
      (byte) 'O',
      (byte) 'P',
      (byte) 'Q',
      (byte) 'R',
      (byte) 'S',
      (byte) 'T',
      (byte) 'U',
      (byte) 'V',
      (byte) 'W',
      (byte) 'X',
      (byte) 'Y',
      (byte) 'Z',
      (byte) '_',
      (byte) 'a',
      (byte) 'b',
      (byte) 'c',
      (byte) 'd',
      (byte) 'e',
      (byte) 'f',
      (byte) 'g',
      (byte) 'h',
      (byte) 'i',
      (byte) 'j',
      (byte) 'k',
      (byte) 'l',
      (byte) 'm',
      (byte) 'n',
      (byte) 'o',
      (byte) 'p',
      (byte) 'q',
      (byte) 'r',
      (byte) 's',
      (byte) 't',
      (byte) 'u',
      (byte) 'v',
      (byte) 'w',
      (byte) 'x',
      (byte) 'y',
      (byte) 'z'};

  /**
   * Used in decoding the "ordered" dialect of Base64.
   */
  private final static byte[] _ORDERED_DECODABET = {-9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 0 - 8
      -5,
      -5, // Whitespace: Tab and Linefeed
      -9,
      -9, // Decimal 11 - 12
      -5, // Whitespace: Carriage Return
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 14 - 26
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 27 - 31
      -5, // Whitespace: Space
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9,
      -9, // Decimal 33 - 42
      -9, // Plus sign at decimal 43
      -9, // Decimal 44
      0, // Minus sign at decimal 45
      -9, // Decimal 46
      -9, // Slash at decimal 47
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      9,
      10, // Numbers zero through nine
      -9,
      -9,
      -9, // Decimal 58 - 60
      -1, // Equals sign at decimal 61
      -9,
      -9,
      -9, // Decimal 62 - 64
      11,
      12,
      13,
      14,
      15,
      16,
      17,
      18,
      19,
      20,
      21,
      22,
      23, // Letters 'A' through 'M'
      24,
      25,
      26,
      27,
      28,
      29,
      30,
      31,
      32,
      33,
      34,
      35,
      36, // Letters 'N' through 'Z'
      -9,
      -9,
      -9,
      -9, // Decimal 91 - 94
      37, // Underscore at decimal 95
      -9, // Decimal 96
      38,
      39,
      40,
      41,
      42,
      43,
      44,
      45,
      46,
      47,
      48,
      49,
      50, // Letters 'a' through 'm'
      51,
      52,
      53,
      54,
      55,
      56,
      57,
      58,
      59,
      60,
      61,
      62,
      63, // Letters 'n' through 'z'
      -9,
      -9,
      -9,
      -9 // Decimal 123 - 126
      /*,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 127 - 139
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 140 - 152
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 153 - 165
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 166 - 178
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 179 - 191
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 192 - 204
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 205 - 217
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 218 - 230
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 231 - 243
-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9         // Decimal 244 - 255 */
  };

  /* ********  D E T E R M I N E   W H I C H   A L H A B E T  ******** */

  /**
   * Returns one of the _SOMETHING_ALPHABET byte arrays depending on
   * the options specified.
   * It's possible, though silly, to specify ORDERED and URLSAFE
   * in which case one of them will be picked, though there is
   * no guarantee as to which one will be picked.
   */
  private final static byte[] getAlphabet(final int options) {
    if ((options & Base64.URL_SAFE) == Base64.URL_SAFE) {
      return Base64._URL_SAFE_ALPHABET;
    } else if ((options & Base64.ORDERED) == Base64.ORDERED) {
      return Base64._ORDERED_ALPHABET;
    } else {
      return Base64._STANDARD_ALPHABET;
    }

  } // end getAlphabet

  /**
   * Returns one of the _SOMETHING_DECODABET byte arrays depending on
   * the options specified.
   * It's possible, though silly, to specify ORDERED and URL_SAFE
   * in which case one of them will be picked, though there is
   * no guarantee as to which one will be picked.
   */
  private final static byte[] getDecodabet(final int options) {
    if ((options & Base64.URL_SAFE) == Base64.URL_SAFE) {
      return Base64._URL_SAFE_DECODABET;
    } else if ((options & Base64.ORDERED) == Base64.ORDERED) {
      return Base64._ORDERED_DECODABET;
    } else {
      return Base64._STANDARD_DECODABET;
    }

  } // end getAlphabet

  /**
   * Defeats instantiation.
   */
  private Base64() {
  }

  /**
   * Encodes or decodes two files from the command line;
   * <strong>feel free to delete this method (in fact you probably should)
   * if you're embedding this code into a larger program.</strong>
   */
  public final static void main(final String[] args) {
    if (args.length < 3) {
      Base64.usage("Not enough arguments.");
    } // end if: args.length < 3
    else {
      String flag = args[0];
      String infile = args[1];
      String outfile = args[2];
      if (flag.equals("-e")) {
        Base64.encodeFileToFile(infile, outfile);
      } // end if: encode
      else if (flag.equals("-d")) {
        Base64.decodeFileToFile(infile, outfile);
      } // end else if: decode
      else {
        Base64.usage("Unknown flag: " + flag);
      } // end else
    } // end else
  } // end main

  /**
   * Prints command line usage.
   *
   * @param msg A message to include with usage info.
   */
  private final static void usage(final String msg) {
    System.err.println(msg);
    System.err.println("Usage: java Base64 -e|-d inputfile outputfile");
  } // end usage

  /* ********  E N C O D I N G   M E T H O D S  ******** */

  /**
   * Encodes up to the first three bytes of array <var>threeBytes</var>
   * and returns a four-byte array in Base64 notation.
   * The actual number of significant bytes in your array is
   * given by <var>numSigBytes</var>.
   * The array <var>threeBytes</var> needs only be as big as
   * <var>numSigBytes</var>.
   * Code can reuse a byte array by passing a four-byte array as <var>b4</var>.
   *
   * @param b4          A reusable byte array to reduce array instantiation
   * @param threeBytes  the array to convert
   * @param numSigBytes the number of significant bytes in your array
   * @return four byte array in Base64 notation.
   * @since 1.5.1
   */
  private static byte[] encode3to4(final byte[] b4, final byte[] threeBytes, final int numSigBytes, final int options) {
    Base64.encode3to4(threeBytes, 0, numSigBytes, b4, 0, options);
    return b4;
  } // end encode3to4

  /**
   * <p>Encodes up to three bytes of the array <var>source</var>
   * and writes the resulting four Base64 bytes to <var>destination</var>.
   * The source and destination arrays can be manipulated
   * anywhere along their length by specifying
   * <var>srcOffset</var> and <var>destOffset</var>.
   * This method does not check to make sure your arrays
   * are large enough to accomodate <var>srcOffset</var> + 3 for
   * the <var>source</var> array or <var>destOffset</var> + 4 for
   * the <var>destination</var> array.
   * The actual number of significant bytes in your array is
   * given by <var>numSigBytes</var>.</p>
   * <p>This is the lowest level of the encoding methods with
   * all possible parameters.</p>
   *
   * @param source      the array to convert
   * @param srcOffset   the index where conversion begins
   * @param numSigBytes the number of significant bytes in your array
   * @param destination the array to hold the conversion
   * @param destOffset  the index where output will be put
   * @return the <var>destination</var> array
   * @since 1.3
   */
  private static byte[] encode3to4(final byte[] source,
                                   final int srcOffset,
                                   final int numSigBytes,
                                   final byte[] destination,
                                   final int destOffset,
                                   final int options) {
    byte[] ALPHABET = Base64.getAlphabet(options);

    // 1 2 3
    // 01234567890123456789012345678901 Bit position
    // --------000000001111111122222222 Array position from threeBytes
    // --------| || || || | Six bit groups to index ALPHABET
    // >>18 >>12 >> 6 >> 0 Right shift necessary
    // 0x3f 0x3f 0x3f Additional AND

    // Create buffer with zero-padding if there are only one or two
    // significant bytes passed in the array.
    // We have to shift left 24 in order to flush out the 1's that appear
    // when Java treats a value as negative that is cast from a byte to an int.
    int inBuff = (numSigBytes > 0 ? source[srcOffset] << 24 >>> 8 : 0) | (numSigBytes > 1 ? source[srcOffset + 1] << 24 >>> 16
        : 0) |
        (numSigBytes > 2 ? source[srcOffset + 2] << 24 >>> 24 : 0);

    switch (numSigBytes) {
      case 3:
        destination[destOffset] = ALPHABET[(inBuff >>> 18)];
        destination[destOffset + 1] = ALPHABET[inBuff >>> 12 & 0x3f];
        destination[destOffset + 2] = ALPHABET[inBuff >>> 6 & 0x3f];
        destination[destOffset + 3] = ALPHABET[inBuff & 0x3f];
        return destination;

      case 2:
        destination[destOffset] = ALPHABET[(inBuff >>> 18)];
        destination[destOffset + 1] = ALPHABET[inBuff >>> 12 & 0x3f];
        destination[destOffset + 2] = ALPHABET[inBuff >>> 6 & 0x3f];
        destination[destOffset + 3] = Base64.EQUALS_SIGN;
        return destination;

      case 1:
        destination[destOffset] = ALPHABET[(inBuff >>> 18)];
        destination[destOffset + 1] = ALPHABET[inBuff >>> 12 & 0x3f];
        destination[destOffset + 2] = Base64.EQUALS_SIGN;
        destination[destOffset + 3] = Base64.EQUALS_SIGN;
        return destination;

      default:
        return destination;
    } // end switch
  } // end encode3to4

  /**
   * Serializes an object and returns the Base64-encoded
   * version of that serialized object. If the object
   * cannot be serialized or there is another error,
   * the method will return <tt>null</tt>.
   * The object is not GZip-compressed before being encoded.
   *
   * @param serializableObject The object to encode
   * @return The Base64-encoded object
   * @since 1.4
   */
  public static String encodeObject(final java.io.Serializable serializableObject) {
    return Base64.encodeObject(serializableObject, Base64.NO_OPTIONS);
  } // end encodeObject

  /**
   * Serializes an object and returns the Base64-encoded
   * version of that serialized object. If the object
   * cannot be serialized or there is another error,
   * the method will return <tt>null</tt>.
   * <p></p>
   * Valid options:<pre>
   *   GZIP: gzip-compresses object before encoding it.
   *   DONT_BREAK_LINES: don't break lines at 76 characters
   *     <i>Note: Technically, this makes your encoding non-compliant.</i>
   * </pre>
   * <p></p>
   * Example: <code>encodeObject( myObj, Base64.GZIP )</code> or
   * <p></p>
   * Example: <code>encodeObject( myObj, Base64.GZIP | Base64.DONT_BREAK_LINES )</code>
   *
   * @param serializableObject The object to encode
   * @param options            Specified options
   * @return The Base64-encoded object
   * @see Base64#GZIP
   * @see Base64#DONT_BREAK_LINES
   * @since 2.0
   */
  public static String encodeObject(final java.io.Serializable serializableObject, final int options) {
    // Streams
    java.io.ByteArrayOutputStream baos = null;
    java.io.OutputStream b64os = null;
    java.io.ObjectOutputStream oos = null;
    java.util.zip.GZIPOutputStream gzos = null;

    // Isolate options
    int gzip = options & Base64.GZIP;
    int dontBreakLines = options & Base64.DONT_BREAK_LINES;

    try {
      // ObjectOutputStream -> (GZIP) -> Base64 -> ByteArrayOutputStream
      baos = new java.io.ByteArrayOutputStream();
      b64os = new Base64.OutputStream(baos, Base64.ENCODE | options);

      // GZip?
      if (gzip == Base64.GZIP) {
        gzos = new java.util.zip.GZIPOutputStream(b64os);
        oos = new java.io.ObjectOutputStream(gzos);
      } // end if: gzip
      else {
        oos = new java.io.ObjectOutputStream(b64os);
      }

      oos.writeObject(serializableObject);
    } // end try
    catch (java.io.IOException e) {
      e.printStackTrace();
      return null;
    } // end catch
    finally {
      try {
        oos.close();
      } catch (Exception e) {
      }
      try {
        gzos.close();
      } catch (Exception e) {
      }
      try {
        b64os.close();
      } catch (Exception e) {
      }
      try {
        baos.close();
      } catch (Exception e) {
      }
    } // end finally

    // Return value according to relevant encoding.
    try {
      return new String(baos.toByteArray(), Base64.PREFERRED_ENCODING);
    } // end try
    catch (java.io.UnsupportedEncodingException uue) {
      return new String(baos.toByteArray());
    } // end catch

  } // end encode

  /**
   * Encodes a byte array into Base64 notation.
   * Does not GZip-compress data.
   *
   * @param source The data to convert
   * @since 1.4
   */
  public static String encodeBytes(final byte[] source) {
    return Base64.encodeBytes(source, 0, source.length, Base64.NO_OPTIONS);
  } // end encodeBytes

  /**
   * Encodes a byte array into Base64 notation.
   * <p></p>
   * Valid options:<pre>
   *   GZIP: gzip-compresses object before encoding it.
   *   DONT_BREAK_LINES: don't break lines at 76 characters
   *     <i>Note: Technically, this makes your encoding non-compliant.</i>
   * </pre>
   * <p></p>
   * Example: <code>encodeBytes( myData, Base64.GZIP )</code> or
   * <p></p>
   * Example: <code>encodeBytes( myData, Base64.GZIP | Base64.DONT_BREAK_LINES )</code>
   *
   * @param source  The data to convert
   * @param options Specified options
   * @see Base64#GZIP
   * @see Base64#DONT_BREAK_LINES
   * @since 2.0
   */
  public static String encodeBytes(final byte[] source, final int options) {
    return Base64.encodeBytes(source, 0, source.length, options);
  } // end encodeBytes

  /**
   * Encodes a byte array into Base64 notation.
   * Does not GZip-compress data.
   *
   * @param source The data to convert
   * @param off    Offset in array where conversion should begin
   * @param len    Length of data to convert
   * @since 1.4
   */
  public static String encodeBytes(final byte[] source, final int off, final int len) {
    return Base64.encodeBytes(source, off, len, Base64.NO_OPTIONS);
  } // end encodeBytes

  /**
   * Encodes a byte array into Base64 notation.
   * <p></p>
   * Valid options:<pre>
   *   GZIP: gzip-compresses object before encoding it.
   *   DONT_BREAK_LINES: don't break lines at 76 characters
   *     <i>Note: Technically, this makes your encoding non-compliant.</i>
   * </pre>
   * <p></p>
   * Example: <code>encodeBytes( myData, Base64.GZIP )</code> or
   * <p></p>
   * Example: <code>encodeBytes( myData, Base64.GZIP | Base64.DONT_BREAK_LINES )</code>
   *
   * @param source  The data to convert
   * @param off     Offset in array where conversion should begin
   * @param len     Length of data to convert
   * @param options options alphabet type is pulled from this (standard, url-safe, ordered)
   * @see Base64#GZIP
   * @see Base64#DONT_BREAK_LINES
   * @since 2.0
   */
  public static String encodeBytes(final byte[] source, final int off, final int len, final int options) {
    // Isolate options
    int dontBreakLines = options & Base64.DONT_BREAK_LINES;
    int gzip = options & Base64.GZIP;

    // Compress?
    if (gzip == Base64.GZIP) {
      java.io.ByteArrayOutputStream baos = null;
      java.util.zip.GZIPOutputStream gzos = null;
      Base64.OutputStream b64os = null;

      try {
        // GZip -> Base64 -> ByteArray
        baos = new java.io.ByteArrayOutputStream();
        b64os = new Base64.OutputStream(baos, Base64.ENCODE | options);
        gzos = new java.util.zip.GZIPOutputStream(b64os);

        gzos.write(source, off, len);
        gzos.close();
      } // end try
      catch (java.io.IOException e) {
        e.printStackTrace();
        return null;
      } // end catch
      finally {
        try {
          gzos.close();
        } catch (Exception e) {
        }
        try {
          b64os.close();
        } catch (Exception e) {
        }
        try {
          baos.close();
        } catch (Exception e) {
        }
      } // end finally

      // Return value according to relevant encoding.
      try {
        return new String(baos.toByteArray(), Base64.PREFERRED_ENCODING);
      } // end try
      catch (java.io.UnsupportedEncodingException uue) {
        return new String(baos.toByteArray());
      } // end catch
    } // end if: compress

    // Else, don't compress. Better not to use streams at all then.
    else {
      // Convert option to boolean in way that code likes it.
      boolean breakLines = dontBreakLines == 0;

      int len43 = len * 4 / 3;
      byte[] outBuff = new byte[len43 + (len % 3 > 0 ? 4 : 0) // Account for padding
          +
          (breakLines ? len43 / Base64.MAX_LINE_LENGTH : 0)]; // New lines
      int d = 0;
      int e = 0;
      int len2 = len - 2;
      int lineLength = 0;
      for (; d < len2; d += 3, e += 4) {
        Base64.encode3to4(source, d + off, 3, outBuff, e, options);

        lineLength += 4;
        if (breakLines && lineLength == Base64.MAX_LINE_LENGTH) {
          outBuff[e + 4] = Base64.NEW_LINE;
          e++;
          lineLength = 0;
        } // end if: end of line
      } // en dfor: each piece of array

      if (d < len) {
        Base64.encode3to4(source, d + off, len - d, outBuff, e, options);
        e += 4;
      } // end if: some padding needed

      // Return value according to relevant encoding.
      try {
        return new String(outBuff, 0, e, Base64.PREFERRED_ENCODING);
      } // end try
      catch (java.io.UnsupportedEncodingException uue) {
        return new String(outBuff, 0, e);
      } // end catch

    } // end else: don't compress

  } // end encodeBytes

  /* ********  D E C O D I N G   M E T H O D S  ******** */

  /**
   * Decodes four bytes from array <var>source</var>
   * and writes the resulting bytes (up to three of them)
   * to <var>destination</var>.
   * The source and destination arrays can be manipulated
   * anywhere along their length by specifying
   * <var>srcOffset</var> and <var>destOffset</var>.
   * This method does not check to make sure your arrays
   * are large enough to accomodate <var>srcOffset</var> + 4 for
   * the <var>source</var> array or <var>destOffset</var> + 3 for
   * the <var>destination</var> array.
   * This method returns the actual number of bytes that
   * were converted from the Base64 encoding.
   * <p>This is the lowest level of the decoding methods with
   * all possible parameters.</p>
   *
   * @param source      the array to convert
   * @param srcOffset   the index where conversion begins
   * @param destination the array to hold the conversion
   * @param destOffset  the index where output will be put
   * @param options     alphabet type is pulled from this (standard, url-safe, ordered)
   * @return the number of decoded bytes converted
   * @since 1.3
   */
  private static int decode4to3(final byte[] source,
                                final int srcOffset,
                                final byte[] destination,
                                final int destOffset,
                                final int options) {
    byte[] DECODABET = Base64.getDecodabet(options);

    // Example: Dk==
    if (source[srcOffset + 2] == Base64.EQUALS_SIGN) {
      // Two ways to do the same thing. Don't know which way I like best.
      // int outBuff = ( ( DECODABET[ source[ srcOffset ] ] << 24 ) >>> 6 )
      // | ( ( DECODABET[ source[ srcOffset + 1] ] << 24 ) >>> 12 );
      int outBuff = (DECODABET[source[srcOffset]] & 0xFF) << 18 | (DECODABET[source[srcOffset + 1]] & 0xFF) << 12;

      destination[destOffset] = (byte) (outBuff >>> 16);
      return 1;
    }

    // Example: DkL=
    else if (source[srcOffset + 3] == Base64.EQUALS_SIGN) {
      // Two ways to do the same thing. Don't know which way I like best.
      // int outBuff = ( ( DECODABET[ source[ srcOffset ] ] << 24 ) >>> 6 )
      // | ( ( DECODABET[ source[ srcOffset + 1 ] ] << 24 ) >>> 12 )
      // | ( ( DECODABET[ source[ srcOffset + 2 ] ] << 24 ) >>> 18 );
      int outBuff = (DECODABET[source[srcOffset]] & 0xFF) << 18 | (DECODABET[source[srcOffset + 1]] & 0xFF) << 12 |
          (DECODABET[source[srcOffset + 2]] & 0xFF) << 6;

      destination[destOffset] = (byte) (outBuff >>> 16);
      destination[destOffset + 1] = (byte) (outBuff >>> 8);
      return 2;
    }

    // Example: DkLE
    else {
      try {
        // Two ways to do the same thing. Don't know which way I like best.
        // int outBuff = ( ( DECODABET[ source[ srcOffset ] ] << 24 ) >>> 6 )
        // | ( ( DECODABET[ source[ srcOffset + 1 ] ] << 24 ) >>> 12 )
        // | ( ( DECODABET[ source[ srcOffset + 2 ] ] << 24 ) >>> 18 )
        // | ( ( DECODABET[ source[ srcOffset + 3 ] ] << 24 ) >>> 24 );
        int outBuff = (DECODABET[source[srcOffset]] & 0xFF) << 18 | (DECODABET[source[srcOffset + 1]] & 0xFF) << 12 |
            (DECODABET[source[srcOffset + 2]] & 0xFF) << 6 |
            DECODABET[source[srcOffset + 3]] &
                0xFF;

        destination[destOffset] = (byte) (outBuff >> 16);
        destination[destOffset + 1] = (byte) (outBuff >> 8);
        destination[destOffset + 2] = (byte) outBuff;

        return 3;
      } catch (Exception e) {
        System.out.println("" + source[srcOffset] + ": " + DECODABET[source[srcOffset]]);
        System.out.println("" + source[srcOffset + 1] + ": " + DECODABET[source[srcOffset + 1]]);
        System.out.println("" + source[srcOffset + 2] + ": " + DECODABET[source[srcOffset + 2]]);
        System.out.println("" + source[srcOffset + 3] + ": " + DECODABET[source[srcOffset + 3]]);
        return -1;
      } // end catch
    }
  } // end decodeToBytes

  /**
   * Very low-level access to decoding ASCII characters in
   * the form of a byte array. Does not support automatically
   * gunzipping or any other "fancy" features.
   *
   * @param source The Base64 encoded data
   * @param off    The offset of where to begin decoding
   * @param len    The length of characters to decode
   * @return decoded data
   * @since 1.3
   */
  public static byte[] decode(final byte[] source, final int off, final int len, final int options) {
    byte[] DECODABET = Base64.getDecodabet(options);

    int len34 = len * 3 / 4;
    byte[] outBuff = new byte[len34]; // Upper limit on size of output
    int outBuffPosn = 0;

    byte[] b4 = new byte[4];
    int b4Posn = 0;
    int i = 0;
    byte sbiCrop = 0;
    byte sbiDecode = 0;
    for (i = off; i < off + len; i++) {
      sbiCrop = (byte) (source[i] & 0x7f); // Only the low seven bits
      sbiDecode = DECODABET[sbiCrop];

      if (sbiDecode >= Base64.WHITE_SPACE_ENC) // White space, Equals sign or better
      {
        if (sbiDecode >= Base64.EQUALS_SIGN_ENC) {
          b4[b4Posn++] = sbiCrop;
          if (b4Posn > 3) {
            outBuffPosn += Base64.decode4to3(b4, 0, outBuff, outBuffPosn, options);
            b4Posn = 0;

            // If that was the equals sign, break out of 'for' loop
            if (sbiCrop == Base64.EQUALS_SIGN) {
              break;
            }
          } // end if: quartet built

        } // end if: equals sign or better

      } // end if: white space, equals sign or better
      else {
        System.err.println("Bad Base64 input character at " + i + ": " + source[i] + "(decimal)");
        return null;
      } // end else:
    } // each input character

    byte[] out = new byte[outBuffPosn];
    System.arraycopy(outBuff, 0, out, 0, outBuffPosn);
    return out;
  } // end decode

  /**
   * Decodes data from Base64 notation, automatically
   * detecting gzip-compressed data and decompressing it.
   *
   * @param s the string to decode
   * @return the decoded data
   * @since 1.4
   */
  public static byte[] decode(final String s) {
    return Base64.decode(s, Base64.NO_OPTIONS);
  }

  /**
   * Decodes data from Base64 notation, automatically
   * detecting gzip-compressed data and decompressing it.
   *
   * @param s       the string to decode
   * @param options encode options such as URL_SAFE
   * @return the decoded data
   * @since 1.4
   */
  public static byte[] decode(final String s, final int options) {
    byte[] bytes;
    try {
      bytes = s.getBytes(Base64.PREFERRED_ENCODING);
    } // end try
    catch (java.io.UnsupportedEncodingException uee) {
      bytes = s.getBytes();
    } // end catch
    // </change>

    // Decode
    bytes = Base64.decode(bytes, 0, bytes.length, options);

    // Check to see if it's gzip-compressed
    // GZIP Magic Two-Byte Number: 0x8b1f (35615)
    if (bytes != null && bytes.length >= 4) {

      int head = bytes[0] & 0xff | bytes[1] << 8 & 0xff00;
      if (java.util.zip.GZIPInputStream.GZIP_MAGIC == head) {
        java.io.ByteArrayInputStream bais = null;
        java.util.zip.GZIPInputStream gzis = null;
        java.io.ByteArrayOutputStream baos = null;
        byte[] buffer = new byte[2048];
        int length = 0;

        try {
          baos = new java.io.ByteArrayOutputStream();
          bais = new java.io.ByteArrayInputStream(bytes);
          gzis = new java.util.zip.GZIPInputStream(bais);

          while ((length = gzis.read(buffer)) >= 0) {
            baos.write(buffer, 0, length);
          } // end while: reading input

          // No error? Get new bytes.
          bytes = baos.toByteArray();

        } // end try
        catch (java.io.IOException e) {
          // Just return originally-decoded bytes
        } // end catch
        finally {
          try {
            baos.close();
          } catch (Exception e) {
          }
          try {
            gzis.close();
          } catch (Exception e) {
          }
          try {
            bais.close();
          } catch (Exception e) {
          }
        } // end finally

      } // end if: gzipped
    } // end if: bytes.length >= 2

    return bytes;
  } // end decode

  /**
   * Attempts to decode Base64 data and deserialize a Java
   * Object within. Returns <tt>null</tt> if there was an error.
   *
   * @param encodedObject The Base64 data to decode
   * @return The decoded and deserialized object
   * @since 1.5
   */
  public static Object decodeToObject(final String encodedObject) {
    // Decode and gunzip if necessary
    byte[] objBytes = Base64.decode(encodedObject);

    java.io.ByteArrayInputStream bais = null;
    java.io.ObjectInputStream ois = null;
    Object obj = null;

    try {
      bais = new java.io.ByteArrayInputStream(objBytes);
      ois = new java.io.ObjectInputStream(bais);

      obj = ois.readObject();
    } // end try
    catch (java.io.IOException e) {
      e.printStackTrace();
      obj = null;
    } // end catch
    catch (java.lang.ClassNotFoundException e) {
      e.printStackTrace();
      obj = null;
    } // end catch
    finally {
      try {
        bais.close();
      } catch (Exception e) {
      }
      try {
        ois.close();
      } catch (Exception e) {
      }
    } // end finally

    return obj;
  } // end decodeObject

  /**
   * Convenience method for encoding data to a file.
   *
   * @param dataToEncode byte array of data to encode in base64 form
   * @param filename     Filename for saving encoded data
   * @return <tt>true</tt> if successful, <tt>false</tt> otherwise
   * @since 2.1
   */
  public static boolean encodeToFile(final byte[] dataToEncode, final String filename) {
    boolean success = false;
    Base64.OutputStream bos = null;
    try {
      bos = new Base64.OutputStream(new java.io.FileOutputStream(filename), Base64.ENCODE);
      bos.write(dataToEncode);
      success = true;
    } // end try
    catch (java.io.IOException e) {

      success = false;
    } // end catch: IOException
    finally {
      try {
        bos.close();
      } catch (Exception e) {
      }
    } // end finally

    return success;
  } // end encodeToFile

  /**
   * Convenience method for decoding data to a file.
   *
   * @param dataToDecode Base64-encoded data as a string
   * @param filename     Filename for saving decoded data
   * @return <tt>true</tt> if successful, <tt>false</tt> otherwise
   * @since 2.1
   */
  public static boolean decodeToFile(final String dataToDecode, final String filename) {
    boolean success = false;
    Base64.OutputStream bos = null;
    try {
      bos = new Base64.OutputStream(new java.io.FileOutputStream(filename), Base64.DECODE);
      bos.write(dataToDecode.getBytes(Base64.PREFERRED_ENCODING));
      success = true;
    } // end try
    catch (java.io.IOException e) {
      success = false;
    } // end catch: IOException
    finally {
      try {
        bos.close();
      } catch (Exception e) {
      }
    } // end finally

    return success;
  } // end decodeToFile

  /**
   * Convenience method for reading a base64-encoded
   * file and decoding it.
   *
   * @param filename Filename for reading encoded data
   * @return decoded byte array or null if unsuccessful
   * @since 2.1
   */
  public static byte[] decodeFromFile(final String filename) {
    byte[] decodedData = null;
    Base64.InputStream bis = null;
    try {
      // Set up some useful variables
      java.io.File file = new java.io.File(filename);
      byte[] buffer = null;
      int length = 0;
      int numBytes = 0;

      // Check for size of file
      if (file.length() > Integer.MAX_VALUE) {
        System.err.println("File is too big for this convenience method (" + file.length() + " bytes).");
        return null;
      } // end if: file too big for int index
      buffer = new byte[(int) file.length()];

      // Open a stream
      bis = new Base64.InputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(file)), Base64.DECODE);

      // Read until done
      while ((numBytes = bis.read(buffer, length, 4096)) >= 0) {
        length += numBytes;
      }

      // Save in a variable to return
      decodedData = new byte[length];
      System.arraycopy(buffer, 0, decodedData, 0, length);

    } // end try
    catch (java.io.IOException e) {
      System.err.println("Error decoding from file " + filename);
    } // end catch: IOException
    finally {
      try {
        bis.close();
      } catch (Exception e) {
      }
    } // end finally

    return decodedData;
  } // end decodeFromFile

  /**
   * Convenience method for reading a binary file
   * and base64-encoding it.
   *
   * @param filename Filename for reading binary data
   * @return base64-encoded string or null if unsuccessful
   * @since 2.1
   */
  public static String encodeFromFile(final String filename) {
    String encodedData = null;
    Base64.InputStream bis = null;
    try {
      // Set up some useful variables
      java.io.File file = new java.io.File(filename);
      byte[] buffer = new byte[Math.max((int) (file.length() * 1.4), 40)]; // Need max() for math on small files
      // (v2.2.1)
      int length = 0;
      int numBytes = 0;

      // Open a stream
      bis = new Base64.InputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(file)), Base64.ENCODE);

      // Read until done
      while ((numBytes = bis.read(buffer, length, 4096)) >= 0) {
        length += numBytes;
      }

      // Save in a variable to return
      encodedData = new String(buffer, 0, length, Base64.PREFERRED_ENCODING);

    } // end try
    catch (java.io.IOException e) {
      System.err.println("Error encoding from file " + filename);
    } // end catch: IOException
    finally {
      try {
        bis.close();
      } catch (Exception e) {
      }
    } // end finally

    return encodedData;
  } // end encodeFromFile

  /**
   * Reads <tt>infile</tt> and encodes it to <tt>outfile</tt>.
   *
   * @param infile  Input file
   * @param outfile Output file
   * @return true if the operation is successful
   * @since 2.2
   */
  public static boolean encodeFileToFile(final String infile, final String outfile) {
    boolean success = false;
    java.io.InputStream in = null;
    java.io.OutputStream out = null;
    try {
      in = new Base64.InputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(infile)),
          Base64.ENCODE);
      out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(outfile));
      byte[] buffer = new byte[65536]; // 64K
      int read = -1;
      while ((read = in.read(buffer)) >= 0) {
        out.write(buffer, 0, read);
      } // end while: through file
      success = true;
    } catch (java.io.IOException exc) {
      exc.printStackTrace();
    } finally {
      try {
        in.close();
      } catch (Exception exc) {
      }
      try {
        out.close();
      } catch (Exception exc) {
      }
    } // end finally

    return success;
  } // end encodeFileToFile

  /**
   * Reads <tt>infile</tt> and decodes it to <tt>outfile</tt>.
   *
   * @param infile  Input file
   * @param outfile Output file
   * @return true if the operation is successful
   * @since 2.2
   */
  public static boolean decodeFileToFile(final String infile, final String outfile) {
    boolean success = false;
    java.io.InputStream in = null;
    java.io.OutputStream out = null;
    try {
      in = new Base64.InputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(infile)),
          Base64.DECODE);
      out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(outfile));
      byte[] buffer = new byte[65536]; // 64K
      int read = -1;
      while ((read = in.read(buffer)) >= 0) {
        out.write(buffer, 0, read);
      } // end while: through file
      success = true;
    } catch (java.io.IOException exc) {
      exc.printStackTrace();
    } finally {
      try {
        in.close();
      } catch (Exception exc) {
      }
      try {
        out.close();
      } catch (Exception exc) {
      }
    } // end finally

    return success;
  } // end decodeFileToFile

  /* ********  I N N E R   C L A S S   I N P U T S T R E A M  ******** */

  /**
   * A {@link Base64.InputStream} will read data from another
   * <tt>java.io.InputStream</tt>, given in the constructor,
   * and encode/decode to/from Base64 notation on the fly.
   *
   * @see Base64
   * @since 1.3
   */
  public static class InputStream extends java.io.FilterInputStream {
    private final boolean encode; // Encoding or decoding

    private int position; // Current position in the buffer

    private final byte[] buffer; // Small buffer holding converted data

    private final int bufferLength; // Length of buffer (3 or 4)

    private int numSigBytes; // Number of meaningful bytes in the buffer

    private int lineLength;

    private final boolean breakLines; // Break lines at less than 80 characters

    private final int options; // Record options used to create the stream.

    private final byte[] alphabet; // Local copies to avoid extra method calls

    private final byte[] decodabet; // Local copies to avoid extra method calls

    /**
     * Constructs a {@link Base64.InputStream} in DECODE mode.
     *
     * @param in the <tt>java.io.InputStream</tt> from which to read data.
     * @since 1.3
     */
    public InputStream(final java.io.InputStream in) {
      this(in, Base64.DECODE);
    } // end constructor

    /**
     * Constructs a {@link Base64.InputStream} in
     * either ENCODE or DECODE mode.
     * <p></p>
     * Valid options:<pre>
     *   ENCODE or DECODE: Encode or Decode as data is read.
     *   DONT_BREAK_LINES: don't break lines at 76 characters
     *     (only meaningful when encoding)
     *     <i>Note: Technically, this makes your encoding non-compliant.</i>
     * </pre>
     * <p></p>
     * Example: <code>new Base64.InputStream( in, Base64.DECODE )</code>
     *
     * @param in      the <tt>java.io.InputStream</tt> from which to read data.
     * @param options Specified options
     * @see Base64#ENCODE
     * @see Base64#DECODE
     * @see Base64#DONT_BREAK_LINES
     * @since 2.0
     */
    public InputStream(final java.io.InputStream in, final int options) {
      super(in);
      breakLines = (options & Base64.DONT_BREAK_LINES) != Base64.DONT_BREAK_LINES;
      encode = (options & Base64.ENCODE) == Base64.ENCODE;
      bufferLength = encode ? 4 : 3;
      buffer = new byte[bufferLength];
      position = -1;
      lineLength = 0;
      this.options = options; // Record for later, mostly to determine which alphabet to use
      alphabet = Base64.getAlphabet(options);
      decodabet = Base64.getDecodabet(options);
    } // end constructor

    /**
     * Reads enough of the input stream to convert
     * to/from Base64 and returns the next byte.
     *
     * @return next byte
     * @since 1.3
     */
    @Override
    public int read() throws java.io.IOException {
      // Do we need to get data?
      if (position < 0) {
        if (encode) {
          byte[] b3 = new byte[3];
          int numBinaryBytes = 0;
          for (int i = 0; i < 3; i++) {
            try {
              int b = in.read();

              // If end of stream, b is -1.
              if (b >= 0) {
                b3[i] = (byte) b;
                numBinaryBytes++;
              } // end if: not end of stream

            } // end try: read
            catch (java.io.IOException e) {
              // Only a problem if we got no data at all.
              if (i == 0) {
                throw e;
              }

            } // end catch
          } // end for: each needed input byte

          if (numBinaryBytes > 0) {
            Base64.encode3to4(b3, 0, numBinaryBytes, buffer, 0, options);
            position = 0;
            numSigBytes = 4;
          } // end if: got data
          else {
            return -1;
          }
        } // end if: encoding

        // Else decoding
        else {
          byte[] b4 = new byte[4];
          int i = 0;
          for (i = 0; i < 4; i++) {
            // Read four "meaningful" bytes:
            int b = 0;
            do {
              b = in.read();
            }
            while (b >= 0 && decodabet[b & 0x7f] <= Base64.WHITE_SPACE_ENC);

            if (b < 0) {
              break; // Reads a -1 if end of stream
            }

            b4[i] = (byte) b;
          } // end for: each needed input byte

          if (i == 4) {
            numSigBytes = Base64.decode4to3(b4, 0, buffer, 0, options);
            position = 0;
          } // end if: got four characters
          else if (i == 0) {
            return -1;
          } else {
            // Must have broken out from above.
            throw new java.io.IOException("Improperly padded Base64 input.");
          }

        } // end else: decode
      } // end else: get data

      // Got data?
      if (position >= 0) {
        // End of relevant data?
        if ( /*!encode &&*/position >= numSigBytes) {
          return -1;
        }

        if (encode && breakLines && lineLength >= Base64.MAX_LINE_LENGTH) {
          lineLength = 0;
          return '\n';
        } // end if
        else {
          lineLength++; // This isn't important when decoding
          // but throwing an extra "if" seems
          // just as wasteful.

          int b = buffer[position++];

          if (position >= bufferLength) {
            position = -1;
          }

          return b & 0xFF; // This is how you "cast" a byte that's
          // intended to be unsigned.
        } // end else
      } // end if: position >= 0
      else {
        // When JDK1.4 is more accepted, use an assertion here.
        throw new java.io.IOException("Error in Base64 code reading stream.");
      }
    } // end read

    /**
     * Calls {@link #read()} repeatedly until the end of stream
     * is reached or <var>len</var> bytes are read.
     * Returns number of bytes read into array or -1 if
     * end of stream is encountered.
     *
     * @param dest array to hold values
     * @param off  offset for array
     * @param len  max number of bytes to read into array
     * @return bytes read into array or -1 if end of stream is encountered.
     * @since 1.3
     */
    @Override
    public int read(final byte[] dest, final int off, final int len) throws java.io.IOException {
      int i;
      int b;
      for (i = 0; i < len; i++) {
        b = read();

        // if( b < 0 && i == 0 )
        // return -1;

        if (b >= 0) {
          dest[off + i] = (byte) b;
        } else if (i == 0) {
          return -1;
        } else {
          break; // Out of 'for' loop
        }
      } // end for: each byte read
      return i;
    } // end read

  } // end inner class InputStream

  /* ********  I N N E R   C L A S S   O U T P U T S T R E A M  ******** */

  /**
   * A {@link Base64.OutputStream} will write data to another
   * <tt>java.io.OutputStream</tt>, given in the constructor,
   * and encode/decode to/from Base64 notation on the fly.
   *
   * @see Base64
   * @since 1.3
   */
  public static class OutputStream extends java.io.FilterOutputStream {
    private final boolean encode;

    private int position;

    private byte[] buffer;

    private final int bufferLength;

    private int lineLength;

    private final boolean breakLines;

    private final byte[] b4; // Scratch used in a few places

    private boolean suspendEncoding;

    private final int options; // Record for later

    private final byte[] alphabet; // Local copies to avoid extra method calls

    private final byte[] decodabet; // Local copies to avoid extra method calls

    /**
     * Constructs a {@link Base64.OutputStream} in ENCODE mode.
     *
     * @param out the <tt>java.io.OutputStream</tt> to which data will be written.
     * @since 1.3
     */
    public OutputStream(final java.io.OutputStream out) {
      this(out, Base64.ENCODE);
    } // end constructor

    /**
     * Constructs a {@link Base64.OutputStream} in
     * either ENCODE or DECODE mode.
     * <p></p>
     * Valid options:<pre>
     *   ENCODE or DECODE: Encode or Decode as data is read.
     *   DONT_BREAK_LINES: don't break lines at 76 characters
     *     (only meaningful when encoding)
     *     <i>Note: Technically, this makes your encoding non-compliant.</i>
     * </pre>
     * <p></p>
     * Example: <code>new Base64.OutputStream( out, Base64.ENCODE )</code>
     *
     * @param out     the <tt>java.io.OutputStream</tt> to which data will be written.
     * @param options Specified options.
     * @see Base64#ENCODE
     * @see Base64#DECODE
     * @see Base64#DONT_BREAK_LINES
     * @since 1.3
     */
    public OutputStream(final java.io.OutputStream out, final int options) {
      super(out);
      breakLines = (options & Base64.DONT_BREAK_LINES) != Base64.DONT_BREAK_LINES;
      encode = (options & Base64.ENCODE) == Base64.ENCODE;
      bufferLength = encode ? 3 : 4;
      buffer = new byte[bufferLength];
      position = 0;
      lineLength = 0;
      suspendEncoding = false;
      b4 = new byte[4];
      this.options = options;
      alphabet = Base64.getAlphabet(options);
      decodabet = Base64.getDecodabet(options);
    } // end constructor

    /**
     * Writes the byte to the output stream after
     * converting to/from Base64 notation.
     * When encoding, bytes are buffered three
     * at a time before the output stream actually
     * gets a write() call.
     * When decoding, bytes are buffered four
     * at a time.
     *
     * @param theByte the byte to write
     * @since 1.3
     */
    @Override
    public void write(final int theByte) throws java.io.IOException {
      // Encoding suspended?
      if (suspendEncoding) {
        super.out.write(theByte);
        return;
      } // end if: supsended

      // Encode?
      if (encode) {
        buffer[position++] = (byte) theByte;
        if (position >= bufferLength) // Enough to encode.
        {
          out.write(Base64.encode3to4(b4, buffer, bufferLength, options));

          lineLength += 4;
          if (breakLines && lineLength >= Base64.MAX_LINE_LENGTH) {
            out.write(Base64.NEW_LINE);
            lineLength = 0;
          } // end if: end of line

          position = 0;
        } // end if: enough to output
      } // end if: encoding

      // Else, Decoding
      else {
        // Meaningful Base64 character?
        if (decodabet[theByte & 0x7f] > Base64.WHITE_SPACE_ENC) {
          buffer[position++] = (byte) theByte;
          if (position >= bufferLength) // Enough to output.
          {
            int len = Base64.decode4to3(buffer, 0, b4, 0, options);
            out.write(b4, 0, len);
            // out.write( Base64.decode4to3( buffer ) );
            position = 0;
          } // end if: enough to output
        } // end if: meaningful base64 character
        else if (decodabet[theByte & 0x7f] != Base64.WHITE_SPACE_ENC) {
          throw new java.io.IOException("Invalid character in Base64 data.");
        }
      } // end else: decoding
    } // end write

    /**
     * Calls {@link #write(int)} repeatedly until <var>len</var>
     * bytes are written.
     *
     * @param theBytes array from which to read bytes
     * @param off      offset for array
     * @param len      max number of bytes to read into array
     * @since 1.3
     */
    @Override
    public void write(final byte[] theBytes, final int off, final int len) throws java.io.IOException {
      // Encoding suspended?
      if (suspendEncoding) {
        super.out.write(theBytes, off, len);
        return;
      } // end if: supsended

      for (int i = 0; i < len; i++) {
        write(theBytes[off + i]);
      } // end for: each byte written

    } // end write

    /**
     * Method added by PHIL. [Thanks, PHIL. -Rob]
     * This pads the buffer without closing the stream.
     */
    public void flushBase64() throws java.io.IOException {
      if (position > 0) {
        if (encode) {
          out.write(Base64.encode3to4(b4, buffer, position, options));
          position = 0;
        } // end if: encoding
        else {
          throw new java.io.IOException("Base64 input not properly padded.");
        }
      } // end if: buffer partially full

    } // end flush

    /**
     * Flushes and closes (I think, in the superclass) the stream.
     *
     * @since 1.3
     */
    @Override
    public void close() throws java.io.IOException {
      // 1. Ensure that pending characters are written
      flushBase64();

      // 2. Actually close the stream
      // Base class both flushes and closes.
      super.close();

      buffer = null;
      out = null;
    } // end close

    /**
     * Suspends encoding of the stream.
     * May be helpful if you need to embed a piece of
     * base640-encoded data in a stream.
     *
     * @since 1.5.1
     */
    public void suspendEncoding() throws java.io.IOException {
      flushBase64();
      suspendEncoding = true;
    } // end suspendEncoding

    /**
     * Resumes encoding of the stream.
     * May be helpful if you need to embed a piece of
     * base640-encoded data in a stream.
     *
     * @since 1.5.1
     */
    public void resumeEncoding() {
      suspendEncoding = false;
    } // end resumeEncoding

  } // end inner class OutputStream

} // end class Base64
