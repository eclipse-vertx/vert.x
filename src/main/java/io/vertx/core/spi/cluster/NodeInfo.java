/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.cluster;

import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * Details about a clustered Vert.x node.
 *
 * @author Thomas Segismont
 */
public final class NodeInfo {

  private final NodeAddress address;
  private final JsonObject metadata;

  public NodeInfo(NodeAddress address, JsonObject metadata) {
    this.address = Objects.requireNonNull(address, "address is null");
    this.metadata = metadata;
  }

  public NodeAddress getAddress() {
    return address;
  }

  public JsonObject getMetadata() {
    return metadata;
  }
}
