/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.cluster;

/**
 * Registration listener used by the cluster manager to keep the clustered event bus updated with the registration changes.
 */
public interface RegistrationListener {

  /**
   * Invoked by the {@link ClusterManager} when messaging handler registrations are added or removed.
   */
  default void registrationsUpdated(RegistrationUpdateEvent event) {
  }

  /**
   * Invoked by the {@link ClusterManager} when some handler registrations have been lost.
   */
  default void registrationsLost() {
  }

  /**
   * Invoked by the {@link ClusterManager} to determine if the node selector wants updates for the given {@code address}.
   *
   * @param address the event bus address
   * @return {@code true} if the node selector wants updates for the given {@code address}, {@code false} otherwise
   */
  default boolean wantsUpdatesFor(String address) {
    return true;
  }
}
