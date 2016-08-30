package io.vertx.core.impl;

import java.util.Map;

@FunctionalInterface
public interface SnapshotsClusterMapLoader {
	Map<String, String> get();
}
