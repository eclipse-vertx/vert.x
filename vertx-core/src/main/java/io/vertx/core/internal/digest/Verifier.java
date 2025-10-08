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
package io.vertx.core.internal.digest;

import java.security.GeneralSecurityException;

/**
 * Token signature verification contract.
 */
@FunctionalInterface
public interface Verifier {

  /**
   * Verify {@code payload} matches the {@code signature}.
   *
   * @param signature the expected result
   * @param payload the tested data
   * @return whether verification succeeded
   * @throws GeneralSecurityException anything that could prevent verification to happen
   */
  boolean verify(byte[] signature, byte[] payload) throws GeneralSecurityException;
}
