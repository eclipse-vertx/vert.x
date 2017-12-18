/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.dns.impl;

import io.vertx.core.dns.MxRecord;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class MxRecordImpl implements MxRecord, Comparable<MxRecord> {

  private final int priority;
  private final String name;

  public MxRecordImpl(int priority, String name) {
    this.priority = priority;
    this.name = name;
  }

  @Override
  public int priority() {
    return priority;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return priority() + " " + name();
  }

  @Override
  public int compareTo(MxRecord o) {
    return Integer.valueOf(priority()).compareTo(o.priority());
  }
}
