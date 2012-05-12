/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core;

import java.nio.file.Path;

import org.vertx.java.core.impl.DeploymentHandle;

/**
 * @author pidster
 *
 */
public interface Context {

	public abstract void setDeploymentHandle(DeploymentHandle deploymentHandle);

	public abstract DeploymentHandle getDeploymentHandle();

	public abstract Path getPathAdjustment();

	public abstract void setPathAdjustment(Path pathAdjustment);

	public abstract void reportException(Throwable t);

	public abstract void addCloseHook(Runnable hook);

	public abstract void runCloseHooks();

	public abstract void execute(Runnable handler);

	public abstract void executeOnWorker(final Runnable task);

}