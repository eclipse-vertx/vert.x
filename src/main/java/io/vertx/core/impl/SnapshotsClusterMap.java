package io.vertx.core.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Vertx;

/**
 * Thread Safe without any locks and blocking code, used to keep track of several old version of the ClusterMap<br>
 * From the given size of the snapshot, we maintain internally an additional place for the new value processed and not already shift<br>
 * It allows us to shit elemenbt
 * @author lbuttigieg
 *
 */
public final class SnapshotsClusterMap {
	private final List<Map<String, String>> snapshots;
	
	@SuppressWarnings("unchecked")
	public SnapshotsClusterMap(final Vertx vertx, final SnapshotsClusterMapLoader loader, final int snapshotsMaxCount, final long loaderCallDelayInMillis) {
		this.snapshots = new ArrayList<>(snapshotsMaxCount + 1);
		
		// Add a supplementary space for temporary updates
		for(int c = 0; c < snapshotsMaxCount + 1; c++) {
			this.snapshots.add(null);
		}
		
		final AtomicInteger fillCount = new AtomicInteger();
		
		vertx.setPeriodic(loaderCallDelayInMillis, h -> {
			vertx.executeBlocking(blocking -> {
				Map<String, String> map = null;
				try {
					map = loader.get();
				} catch (final Throwable t) {
				}
				blocking.complete(map);
  		    }, r -> {
  		    	final Map<String, String> map = (Map<String, String>)r.result();
  		    	if (map == null) return;

  				// Not full
  				if (fillCount.get() < snapshotsMaxCount) {
  					this.snapshots.set(fillCount.getAndIncrement(), map);
  					return;
  				}
  				
  				// Full
  				this.snapshots.set(snapshots.size()-1, map);
  				for(int c = 0; c < snapshots.size()-1; c++) {
  					this.snapshots.set(c, this.snapshots.get(c+1));
  				}
  				this.snapshots.set(snapshots.size()-1, null);
  		    });
		});
	}
	
	/**
	 * Try to find and return the value associated with the top most fresh snapshot or {@code null}
	 * @param key
	 * @return the value associated with the top most fresh snapshot or {@code null}
	 */
	@Nullable
	public String getValueFromAllSnapshots(final String key) {
		for(int c = snapshots.size() - 1; c >= 0; c--) {
			final Map<String, String> map = this.snapshots.get(c);
			if (map == null) continue;
			final String value = map.get(key);
			if (value != null) return value;
		}
		return null;
	}
	
	public List<Map<String, String>> getAllSnapshots() {
		final List<Map<String, String>> allSnapshots = new ArrayList<>(snapshots.size());
		for(int c = snapshots.size() - 1; c >= 0; c--) {
			final Map<String, String> map = this.snapshots.get(c);
			if (map == null) continue;
			allSnapshots.add(map);
		}
		return allSnapshots;
	}
}
