package org.nodex.core.stomp;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:44
 * To change this template use File | Settings | File Templates.
 */
public class Parser extends Callback<Buffer> {

  public Parser(Callback<StompFrame> emitter) {

  }

  public void onEvent(Buffer buffer) {
    //We consume buffer events from a LineEmitter, so we always get full lines

  }
}
