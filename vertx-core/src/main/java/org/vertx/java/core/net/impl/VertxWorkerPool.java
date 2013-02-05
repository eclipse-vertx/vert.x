/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net.impl;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxWorkerPool implements WorkerPool<NioWorker> {

  private static final Logger log = LoggerFactory.getLogger(VertxWorkerPool.class);

  private int pos;

  private List<WorkerHolder> workers = new ArrayList<>();

  public synchronized NioWorker nextWorker() {
    if (workers.isEmpty()) {
      return null;
    } else {
      NioWorker worker = workers.get(pos).worker;
      pos++;
      checkPos();
      return worker;
    }
  }

  public synchronized void addWorker(NioWorker worker) {
    WorkerHolder holder = findHolder(worker);
    if (holder == null) {
      workers.add(new WorkerHolder(worker));
    } else {
      holder.count++;
    }
  }

  public void shutdown() {

  }

  public void rebuildSelectors() {

  }

  private WorkerHolder findHolder(NioWorker worker) {
    WorkerHolder wh = new WorkerHolder(worker);
    for (WorkerHolder holder: workers) {
      if (holder.equals(wh)) {
        return holder;
      }
    }
    return null;
  }

  public synchronized void removeWorker(NioWorker worker) {
    //TODO can be optimised
    WorkerHolder holder = findHolder(worker);
    if (holder != null) {
      holder.count--;
      if (holder.count == 0) {
        workers.remove(holder);
      }
      checkPos();
    } else {
      throw new IllegalStateException("Can't find worker to remove");
    }
  }

  public synchronized int workerCount() {
    return workers.size();
  }

  private void checkPos() {
    if (pos == workers.size()) {
      pos = 0;
    }
  }

  private static class WorkerHolder {
    int count = 1;
    final NioWorker worker;

    WorkerHolder(NioWorker worker) {
      this.worker = worker;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      WorkerHolder that = (WorkerHolder) o;

      if (worker != null ? !worker.equals(that.worker) : that.worker != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return worker != null ? worker.hashCode() : 0;
    }
  }
}
