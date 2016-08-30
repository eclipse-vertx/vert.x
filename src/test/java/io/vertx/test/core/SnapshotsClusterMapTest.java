package io.vertx.test.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import io.vertx.core.impl.SnapshotsClusterMap;
import io.vertx.core.impl.SnapshotsClusterMapLoader;

public class SnapshotsClusterMapTest extends VertxTestBase {
	@Test(timeout=1000)
	public void testOneSnap() throws Exception {
		final Map<String, String> newmap = new HashMap<String, String>();
		newmap.put("found", "found-value");
		final SnapshotsClusterMap map = new SnapshotsClusterMap(vertx(), () -> newmap, 1, 30);
		Assert.assertNull(map.getValueFromAllSnapshots("notfound"));
		while(map.getValueFromAllSnapshots("found") == null);
	}
	
	@Test(timeout=1000)
	public void testTenSnapsOverFive() throws Exception {
		final AtomicInteger counter = new AtomicInteger();
		final SnapshotsClusterMapLoader loader = () -> {
			final int count = counter.incrementAndGet();
			final Map<String, String> newmap = new HashMap<String, String>();
			for(int i = 0; i < count; i++) {
				newmap.put("key"+i, "value"+i);
			}
			return newmap;
		};
		
		final SnapshotsClusterMap map = new SnapshotsClusterMap(vertx(), loader, 5, 30);
		Assert.assertNull(map.getValueFromAllSnapshots("key10"));
		for(int i = 1; i <= 10; i++) {
			while(map.getValueFromAllSnapshots("key"+i) == null);	
		}
		Assert.assertNotNull(map.getValueFromAllSnapshots("key10"));
	}
}
