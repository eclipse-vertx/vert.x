package org.vertx.java.core.http.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.ConnectionPool;
import org.vertx.java.core.impl.Context;

import java.util.Queue;

/**
 * A connention pool with specialied behavior for HttpClientConnecitons
 *
 * @author Nathan Pahucki, <a href="mailto:npahucki@gmail.com"> nathan@gmail.com</a>
 */
final class DefaultHttpClientConnectionPool extends ConnectionPool<ClientConnection> {

  private final DefaultHttpClient client;
  private boolean disableUsingOccupiedConnections;

  DefaultHttpClientConnectionPool(DefaultHttpClient client) {
    this.client = client;
  }

  /**
   * Disallows assigning more than one request to a connection, but instead causes a request to go into
   * the wait list if all available connections are already servicing a request.
   *
   * NOTE: This is experimental, and seems to produce much worse performance in all cases tested. I left this
   * here so Tim could review this logic and see why this is the case, as it seems it should actually improve performance.
   *
   * @param disableUsingOccupiedConnections true to prevent allowing occupied conenctions.
   */
  void setDisableUsingOccupiedConnections(boolean disableUsingOccupiedConnections) {
    this.disableUsingOccupiedConnections = disableUsingOccupiedConnections;
  }

  @Override
  protected void connect(Handler<ClientConnection> connectHandler, Handler<Exception> connectErrorHandler, Context context) {
    // We delegate to the DefaultHttpClient class here, but really, DefaultHttpClient should be rewritten to take
    // the connection handling logic that exists in DefaultHttpClient and move it into this class.
    client.internalConnect(connectHandler, connectErrorHandler);
  }

  @Override
  protected ClientConnection selectConnection(final Queue<ClientConnection> available, final int connectionCount, final int maxPoolSize) {
    ClientConnection conn = null;

    if (!available.isEmpty()) {
      final boolean useOccupiedConnections = !disableUsingOccupiedConnections && connectionCount >= maxPoolSize;

      for (final ClientConnection c : available) {

        // Ideal situation for all cases, a cached but unoccupied connection.
        if (c.getOutstandingRequestCount() < 1 && !c.isClosed()) {
          conn = c;
          break;
        }

        if (useOccupiedConnections) {
          // Otherwise, lets try to pick the connection that has the least amount of outstanding requests on it,
          // even though we don't have any good way to know how long the requests in the front of this one might take
          // it's still better than the old behavior which seems to glob all the requests into the first connection
          // in the available list.
          if (conn == null || (conn.getOutstandingRequestCount() > c.getOutstandingRequestCount() && !c.isClosed())) {
            conn = c;
          }
        }
      }

      if(conn != null) available.remove(conn);
    }
    return conn; // might still be null, which would either create a connection, or put the request in a wait list
  }
}

