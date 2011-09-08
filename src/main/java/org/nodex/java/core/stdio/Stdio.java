package org.nodex.java.core.stdio;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Stdio {

  public static final InStream in = new InStream(System.in);

  public static final OutStream out = new OutStream(System.out);

  public static final OutStream err = new OutStream(System.err);


}
