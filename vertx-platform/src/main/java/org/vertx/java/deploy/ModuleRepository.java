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

package org.vertx.java.deploy;

import java.io.File;

import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.impl.ActionFuture;

/**
 * Interface for module repository implementations
 * 
 * @author Juergen Donnerstag
 */
public interface ModuleRepository {

	/**
	 * Install a module from a remote (http) repository.
	 * 
	 * @param moduleName
	 *          Module name
	 * @param modRoot
	 *          Where to install the module
	 * @param doneHandler
	 */
	ActionFuture<Void> installMod(String moduleName, File modRoot, AsyncResultHandler<String> doneHandler);
}
