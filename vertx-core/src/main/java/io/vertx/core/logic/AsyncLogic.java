/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.logic;

/**
 * Main interface for asynchronous logic operations in Vert.x.
 * <p>
 * This interface combines three core capabilities:
 * <ul>
 *   <li>{@link AsyncLogicParallel} - Parallel execution of async operations</li>
 *   <li>{@link AsyncLogicLock} - Exclusive execution with lock mechanisms</li>
 *   <li>{@link AsyncLogicBlock} - Transformation of blocking operations to async</li>
 * </ul>
 * <p>
 * Instances of this interface can be obtained via {@link io.vertx.core.Vertx#asyncLogic()}.
 *
 * @author Sinri Edogawa
 */
public interface AsyncLogic extends AsyncLogicParallel, AsyncLogicLock, AsyncLogicBlock {
}
