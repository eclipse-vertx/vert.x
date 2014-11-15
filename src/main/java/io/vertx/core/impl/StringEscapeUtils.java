/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
* Modified from original form by Tim Fox
*/
package io.vertx.core.impl;


import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

/**
 *
 * Adapted from Apache Commons code
 *
 *
 * <p>Escapes and unescapes <code>String</code>s for
 * Java, Java Script, HTML, XML, and SQL.</p>
 * <p></p>
 * <p>#ThreadSafe#</p>
 *
 * @author Apache Software Foundation
 * @author Apache Jakarta Turbine
 * @author Purple Technology
 * @author <a href="mailto:alex@purpletech.com">Alexander Day Chaffee</a>
 * @author Antony Riley
 * @author Helge Tesgaard
 * @author <a href="sean@boohai.com">Sean Brown</a>
 * @author <a href="mailto:ggregory@seagullsw.com">Gary Gregory</a>
 * @author Phil Steitz
 * @author Pete Gieser
 * @version $Id: StringEscapeUtils.java 1057072 2011-01-10 01:55:57Z niallp $
 * @since 2.0
 */
public class StringEscapeUtils {

  /**
   * <p><code>StringEscapeUtils</code> instances should NOT be constructed in
   * standard programming.</p>
   * <p></p>
   * <p>Instead, the class should be used as:</p>
   * <pre>StringEscapeUtils.escapeJava("foo");</pre>
   * <p></p>
   * <p>This constructor is public to permit tools that require a JavaBean
   * instance to operate.</p>
   */
  public StringEscapeUtils() {
    super();
  }

  // Java and JavaScript
  //--------------------------------------------------------------------------

  /**
   * <p>Escapes the characters in a <code>String</code> using Java String rules.</p>
   * <p></p>
   * <p>Deals correctly with quotes and control-chars (tab, backslash, cr, ff, etc.) </p>
   * <p></p>
   * <p>So a tab becomes the characters <code>'\\'</code> and
   * <code>'t'</code>.</p>
   * <p></p>
   * <p>The only difference between Java strings and JavaScript strings
   * is that in JavaScript, a single quote must be escaped.</p>
   * <p></p>
   * <p>Example:</p>
   * <pre>
   * input string: He didn't say, "Stop!"
   * output string: He didn't say, \"Stop!\"
   * </pre>
   *
   *
   * @param str String to escape values in, may be null
   * @return String with escaped values, <code>null</code> if null string input
   */
  public static String escapeJava(String str) throws Exception {
    return escapeJavaStyleString(str, false, false);
  }

  /**
   * <p>Escapes the characters in a <code>String</code> using Java String rules to
   * a <code>Writer</code>.</p>
   * <p></p>
   * <p>A <code>null</code> string input has no effect.</p>
   *
   * @param out Writer to write escaped string into
   * @param str String to escape values in, may be null
   * @throws IllegalArgumentException if the Writer is <code>null</code>
   * @throws java.io.IOException              if error occurs on underlying Writer
   * @see #escapeJava(String)
   */
  public static void escapeJava(Writer out, String str) throws IOException {
    escapeJavaStyleString(out, str, false, false);
  }

  /**
   * <p>Escapes the characters in a <code>String</code> using JavaScript String rules.</p>
   * <p>Escapes any values it finds into their JavaScript String form.
   * Deals correctly with quotes and control-chars (tab, backslash, cr, ff, etc.) </p>
   * <p></p>
   * <p>So a tab becomes the characters <code>'\\'</code> and
   * <code>'t'</code>.</p>
   * <p></p>
   * <p>The only difference between Java strings and JavaScript strings
   * is that in JavaScript, a single quote must be escaped.</p>
   * <p></p>
   * <p>Example:</p>
   * <pre>
   * input string: He didn't say, "Stop!"
   * output string: He didn\'t say, \"Stop!\"
   * </pre>
   *
   * @param str String to escape values in, may be null
   * @return String with escaped values, <code>null</code> if null string input
   */
  public static String escapeJavaScript(String str) throws Exception {
    return escapeJavaStyleString(str, true, true);
  }

  /**
   * <p>Escapes the characters in a <code>String</code> using JavaScript String rules
   * to a <code>Writer</code>.</p>
   * <p></p>
   * <p>A <code>null</code> string input has no effect.</p>
   *
   * @param out Writer to write escaped string into
   * @param str String to escape values in, may be null
   * @throws IllegalArgumentException if the Writer is <code>null</code>
   * @throws java.io.IOException              if error occurs on underlying Writer
   * @see #escapeJavaScript(String)
   */
  public static void escapeJavaScript(Writer out, String str) throws Exception {
    escapeJavaStyleString(out, str, true, true);
  }

  /**
   * <p>Worker method for the {@link #escapeJavaScript(String)} method.</p>
   *
   * @param str                String to escape values in, may be null
   * @param escapeSingleQuotes escapes single quotes if <code>true</code>
   * @param escapeForwardSlash TODO
   * @return the escaped string
   */
  private static String escapeJavaStyleString(String str, boolean escapeSingleQuotes, boolean escapeForwardSlash)
      throws Exception {
    if (str == null) {
      return null;
    }
    StringWriter writer = new StringWriter(str.length() * 2);
    escapeJavaStyleString(writer, str, escapeSingleQuotes, escapeForwardSlash);
    return writer.toString();
  }

  /**
   * <p>Worker method for the {@link #escapeJavaScript(String)} method.</p>
   *
   * @param out                write to receieve the escaped string
   * @param str                String to escape values in, may be null
   * @param escapeSingleQuote  escapes single quotes if <code>true</code>
   * @param escapeForwardSlash TODO
   * @throws java.io.IOException if an IOException occurs
   */
  private static void escapeJavaStyleString(Writer out, String str, boolean escapeSingleQuote,
                                            boolean escapeForwardSlash) throws IOException {
    if (out == null) {
      throw new IllegalArgumentException("The Writer must not be null");
    }
    if (str == null) {
      return;
    }
    int sz;
    sz = str.length();
    for (int i = 0; i < sz; i++) {
      char ch = str.charAt(i);

      // handle unicode
      if (ch > 0xfff) {
        out.write("\\u" + hex(ch));
      } else if (ch > 0xff) {
        out.write("\\u0" + hex(ch));
      } else if (ch > 0x7f) {
        out.write("\\u00" + hex(ch));
      } else if (ch < 32) {
        switch (ch) {
          case '\b':
            out.write('\\');
            out.write('b');
            break;
          case '\n':
            out.write('\\');
            out.write('n');
            break;
          case '\t':
            out.write('\\');
            out.write('t');
            break;
          case '\f':
            out.write('\\');
            out.write('f');
            break;
          case '\r':
            out.write('\\');
            out.write('r');
            break;
          default:
            if (ch > 0xf) {
              out.write("\\u00" + hex(ch));
            } else {
              out.write("\\u000" + hex(ch));
            }
            break;
        }
      } else {
        switch (ch) {
          case '\'':
            if (escapeSingleQuote) {
              out.write('\\');
            }
            out.write('\'');
            break;
          case '"':
            out.write('\\');
            out.write('"');
            break;
          case '\\':
            out.write('\\');
            out.write('\\');
            break;
          case '/':
            if (escapeForwardSlash) {
              out.write('\\');
            }
            out.write('/');
            break;
          default:
            out.write(ch);
            break;
        }
      }
    }
  }

  /**
   * <p>Returns an upper case hexadecimal <code>String</code> for the given
   * character.</p>
   *
   * @param ch The character to convert.
   * @return An upper case hexadecimal <code>String</code>
   */
  private static String hex(char ch) {
    return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
  }

  /**
   * <p>Unescapes any Java literals found in the <code>String</code>.
   * For example, it will turn a sequence of <code>'\'</code> and
   * <code>'n'</code> into a newline character, unless the <code>'\'</code>
   * is preceded by another <code>'\'</code>.</p>
   *
   * @param str the <code>String</code> to unescape, may be null
   * @return a new unescaped <code>String</code>, <code>null</code> if null string input
   */
  public static String unescapeJava(String str) throws Exception {
    if (str == null) {
      return null;
    }
    StringWriter writer = new StringWriter(str.length());
    unescapeJava(writer, str);
    return writer.toString();
  }

  /**
   * <p>Unescapes any Java literals found in the <code>String</code> to a
   * <code>Writer</code>.</p>
   * <p></p>
   * <p>For example, it will turn a sequence of <code>'\'</code> and
   * <code>'n'</code> into a newline character, unless the <code>'\'</code>
   * is preceded by another <code>'\'</code>.</p>
   * <p></p>
   * <p>A <code>null</code> string input has no effect.</p>
   *
   * @param out the <code>Writer</code> used to output unescaped characters
   * @param str the <code>String</code> to unescape, may be null
   * @throws IllegalArgumentException if the Writer is <code>null</code>
   * @throws java.io.IOException              if error occurs on underlying Writer
   */
  public static void unescapeJava(Writer out, String str) throws Exception {
    if (out == null) {
      throw new IllegalArgumentException("The Writer must not be null");
    }
    if (str == null) {
      return;
    }
    int sz = str.length();
    StringBuilder unicode = new StringBuilder();
    boolean hadSlash = false;
    boolean inUnicode = false;
    for (int i = 0; i < sz; i++) {
      char ch = str.charAt(i);
      if (inUnicode) {
        // if in unicode, then we're reading unicode
        // values in somehow
        unicode.append(ch);
        if (unicode.length() == 4) {
          // unicode now contains the four hex digits
          // which represents our unicode character
          int value = Integer.parseInt(unicode.toString(), 16);
          out.write((char) value);
          unicode.setLength(0);
          inUnicode = false;
          hadSlash = false;
        }
        continue;
      }
      if (hadSlash) {
        // handle an escaped value
        hadSlash = false;
        switch (ch) {
          case '\\':
            out.write('\\');
            break;
          case '\'':
            out.write('\'');
            break;
          case '\"':
            out.write('"');
            break;
          case 'r':
            out.write('\r');
            break;
          case 'f':
            out.write('\f');
            break;
          case 't':
            out.write('\t');
            break;
          case 'n':
            out.write('\n');
            break;
          case 'b':
            out.write('\b');
            break;
          case 'u': {
            // uh-oh, we're in unicode country....
            inUnicode = true;
            break;
          }
          default:
            out.write(ch);
            break;
        }
        continue;
      } else if (ch == '\\') {
        hadSlash = true;
        continue;
      }
      out.write(ch);
    }
    if (hadSlash) {
      // then we're in the weird case of a \ at the end of the
      // string, let's output it anyway.
      out.write('\\');
    }
  }

  /**
   * <p>Unescapes any JavaScript literals found in the <code>String</code>.</p>
   * <p></p>
   * <p>For example, it will turn a sequence of <code>'\'</code> and <code>'n'</code>
   * into a newline character, unless the <code>'\'</code> is preceded by another
   * <code>'\'</code>.</p>
   *
   * @param str the <code>String</code> to unescape, may be null
   * @return A new unescaped <code>String</code>, <code>null</code> if null string input
   * @see #unescapeJava(String)
   */
  public static String unescapeJavaScript(String str) throws Exception {
    return unescapeJava(str);
  }

  /**
   * <p>Unescapes any JavaScript literals found in the <code>String</code> to a
   * <code>Writer</code>.</p>
   * <p></p>
   * <p>For example, it will turn a sequence of <code>'\'</code> and <code>'n'</code>
   * into a newline character, unless the <code>'\'</code> is preceded by another
   * <code>'\'</code>.</p>
   * <p></p>
   * <p>A <code>null</code> string input has no effect.</p>
   *
   * @param out the <code>Writer</code> used to output unescaped characters
   * @param str the <code>String</code> to unescape, may be null
   * @throws IllegalArgumentException if the Writer is <code>null</code>
   * @throws java.io.IOException              if error occurs on underlying Writer
   * @see #unescapeJava(java.io.Writer, String)
   */
  public static void unescapeJavaScript(Writer out, String str) throws Exception {
    unescapeJava(out, str);
  }

}





