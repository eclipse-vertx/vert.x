package org.vertx.java.deploy.impl.rhino;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.tools.shell.ShellContextFactory;

/**
 * Created by IntelliJ IDEA.
 * User: jock
 * Date: 9/7/12
 * Time: 10:21 PM
 */

public class RhinoDebugContextFactory extends ShellContextFactory {
    protected void onContextCreated(Context cx) {
      super.onContextCreated(cx);
      cx.getWrapFactory().setJavaPrimitiveWrap(false);
    }
}
