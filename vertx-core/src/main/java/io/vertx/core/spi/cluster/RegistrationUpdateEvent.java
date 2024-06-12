/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.cluster;

import java.util.Collections;
import java.util.List;

/**
 * Event fired by the {@link ClusterManager} when messaging handler registrations are added or removed.
 *
 * @author Thomas Segismont
 */
public class RegistrationUpdateEvent {

  private final String address;
  private final List<RegistrationInfo> registrations;

  public RegistrationUpdateEvent(String address, List<RegistrationInfo> registrations) {
    this.address = address;
    this.registrations = registrations == null ? Collections.emptyList() : registrations;
  }

  /**
   * @return the address related to this event
   */
  public String address() {
    return address;
  }

  /**
   * @return the new state of messaging handler registrations
   */
  public List<RegistrationInfo> registrations() {
    return registrations;
  }

  @Override
  public String toString() {
    return "RegistrationUpdateEvent{" +
      "address='" + address + '\'' +
      ", registrations=" + registrations +
      '}';
  }
}
