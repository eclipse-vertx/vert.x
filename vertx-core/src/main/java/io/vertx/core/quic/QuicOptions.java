/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.quic;

import io.vertx.core.net.TransportOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicOptions extends TransportOptions {

  private Long initialMaxData;
  private Long initialMaxStreamDataBidirectionalLocal;
  private Long initialMaxStreamDataBidirectionalRemote;
  private Long initialMaxStreamsBidirectional;
  private Long initialMaxStreamsUnidirectional;
  private Long initialMaxStreamDataUnidirectional;
  private Boolean activeMigration;

  public QuicOptions() {
  }

  public QuicOptions(QuicOptions other) {
    this.initialMaxData = other.initialMaxData;
    this.initialMaxStreamDataBidirectionalLocal = other.initialMaxStreamDataBidirectionalLocal;
    this.initialMaxStreamDataBidirectionalRemote = other.initialMaxStreamDataBidirectionalRemote;
    this.initialMaxStreamsBidirectional = other.initialMaxStreamsBidirectional;
    this.initialMaxStreamsUnidirectional = other.initialMaxStreamsUnidirectional;
    this.initialMaxStreamDataUnidirectional = other.initialMaxStreamDataUnidirectional;
    this.activeMigration = other.activeMigration;
  }

  @Override
  protected QuicOptions copy() {
    return new QuicOptions(this);
  }

  public Long getInitialMaxData() {
    return initialMaxData;
  }

  public QuicOptions setInitialMaxData(Long initialMaxData) {
    this.initialMaxData = initialMaxData;
    return this;
  }

  public Long getInitialMaxStreamDataBidirectionalLocal() {
    return initialMaxStreamDataBidirectionalLocal;
  }

  public QuicOptions setInitialMaxStreamDataBidirectionalLocal(Long initialMaxStreamDataBidirectionalLocal) {
    this.initialMaxStreamDataBidirectionalLocal = initialMaxStreamDataBidirectionalLocal;
    return this;
  }

  public Long getInitialMaxStreamDataBidirectionalRemote() {
    return initialMaxStreamDataBidirectionalRemote;
  }

  public QuicOptions setInitialMaxStreamDataBidirectionalRemote(Long initialMaxStreamDataBidirectionalRemote) {
    this.initialMaxStreamDataBidirectionalRemote = initialMaxStreamDataBidirectionalRemote;
    return this;
  }

  public Long getInitialMaxStreamsBidirectional() {
    return initialMaxStreamsBidirectional;
  }

  public QuicOptions setInitialMaxStreamsBidirectional(Long initialMaxStreamsBidirectional) {
    this.initialMaxStreamsBidirectional = initialMaxStreamsBidirectional;
    return this;
  }

  public Long getInitialMaxStreamsUnidirectional() {
    return initialMaxStreamsUnidirectional;
  }

  public QuicOptions setInitialMaxStreamsUnidirectional(Long initialMaxStreamsUnidirectional) {
    this.initialMaxStreamsUnidirectional = initialMaxStreamsUnidirectional;
    return this;
  }

  public Long getInitialMaxStreamDataUnidirectional() {
    return initialMaxStreamDataUnidirectional;
  }

  public QuicOptions setInitialMaxStreamDataUnidirectional(Long initialMaxStreamDataUnidirectional) {
    this.initialMaxStreamDataUnidirectional = initialMaxStreamDataUnidirectional;
    return this;
  }

  public Boolean getActiveMigration() {
    return activeMigration;
  }

  public QuicOptions setActiveMigration(Boolean activeMigration) {
    this.activeMigration = activeMigration;
    return this;
  }
}
