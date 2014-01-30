/*
 * Copyright (c) 2011-2013 Red Hat Inc.
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl.resolver;
import org.vertx.java.core.Vertx;
import org.vertx.java.platform.impl.ModuleIdentifier;

import java.net.URI;

public abstract class HttpRepoResolver implements RepoResolver {

  protected final Vertx vertx;
  protected final String repoScheme;
  protected final String repoHost;
  protected final String repoUsername;
  protected final String repoPassword;
  protected final int repoPort;
  protected final String contentRoot;

  public HttpRepoResolver(Vertx vertx, String repoID) {
    this.vertx = vertx;
    try {
      URI uri = new URI(repoID);
      if (uri.getUserInfo() != null) {
        int i = uri.getUserInfo().indexOf(":");
        if (i > 0) {
          repoUsername = uri.getUserInfo().substring(0, i);
          repoPassword = uri.getUserInfo().substring(i + 1);
        } else {
          repoUsername = null;
          repoPassword = null;
        }
      } else {
        repoUsername = null;
        repoPassword = null;
      }

      repoScheme = uri.getScheme();
      repoHost = uri.getHost();
      int port = uri.getPort();
      if (port == -1 ) {
        port = (repoScheme.equals("https")) ? 443 : 80;
      }
      repoPort = port;
      contentRoot = uri.getPath();
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException(repoID + " is not a valid repository identifier");
    }
  }

  public abstract boolean getModule(String filename, ModuleIdentifier moduleIdentifier);

}
