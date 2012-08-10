package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.shareddata.Shareable;
import org.vertx.java.core.shareddata.impl.SharedSet;

/**
 * @author Keesun Baik
 */
public class Room implements Shareable {

	SharedSet<String> ids;

	public Room() {
		ids = new SharedSet<>();
	}

	public boolean contains(String sessinoId) {
		return ids.contains(sessinoId);
	}

	public void push(String sessionId) {
		ids.add(sessionId);
	}

	public void remove(String sessionId) {
		ids.remove(sessionId);
	}

	public String[] values() {
		return ids.toArray(new String[ids.size()]);
	}

	public int size() {
		return ids.size();
	}
}
