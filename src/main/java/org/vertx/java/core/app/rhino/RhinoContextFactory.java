package org.vertx.java.core.app.rhino;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * We need to make sure any Java primitive types are passed into Rhino code as the corresponding JS types
 * By default Rhino doesn't do this.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RhinoContextFactory extends ContextFactory {

  protected void onContextCreated(Context cx) {
    super.onContextCreated(cx);
    cx.getWrapFactory().setJavaPrimitiveWrap(false);
  }
}


