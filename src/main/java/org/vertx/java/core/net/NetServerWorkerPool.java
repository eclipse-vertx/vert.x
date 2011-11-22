package org.vertx.java.core.net;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetServerWorkerPool extends NioWorkerPool {

  public NetServerWorkerPool() {
    super(0, null);
  }

  private int pos;

  private List<WorkerHolder> workers = new ArrayList<>();

  public synchronized NioWorker nextWorker() {
    NioWorker worker = workers.get(pos).worker;
    pos++;
    checkPos();
    return worker;
  }

  public synchronized void addWorker(NioWorker worker) {
    WorkerHolder holder = findHolder(worker);
    if (holder == null) {
      workers.add(new WorkerHolder(worker));
    } else {
      holder.count++;
    }
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
        workers.remove(worker);
      }
      checkPos();
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
