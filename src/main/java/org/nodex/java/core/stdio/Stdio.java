package org.nodex.java.core.stdio;

/**
 * User: tim
 * Date: 17/08/11
 * Time: 08:42
 */
public class Stdio {

  public static final InStream in = new InStream(System.in);

  public static final OutStream out = new OutStream(System.out);

  public static final OutStream err = new OutStream(System.err);


}
