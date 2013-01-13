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
package org.vertx.java.deploy.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.Stack;

import org.vertx.java.core.utils.Args;

/**
 * A little utility to walk a module's dependencies
 * 
 * @author Juergen Donnerstag
 * 
 * @param <T>
 *          The result type
 */
public class ModuleWalker<T> {

	private final ModuleManager moduleManager;

	// Parent are pushed onto the stack which is made available
	private final Stack<VertxModule> stack = new Stack<>();

	private ModuleVisitor<T> visitor;

	// Temporary store for the result value
	private T result;

	/**
	 * Constructor
	 * 
	 * @param moduleManager
	 */
	public ModuleWalker(final ModuleManager moduleManager) {
		this.moduleManager = moduleManager;
	}

	/**
	 * Get the module by name
	 * 
	 * @param modName
	 * @return
	 */
	private VertxModule module(String modName) {
		return moduleManager.module(modName);
	}

	/**
	 * @return The 'result' after visiting the modules
	 */
	public final T result() {
		return result;
	}

	/**
	 * Set the result value
	 * 
	 * @param result
	 */
	public final void result(final T result) {
		this.result = result;
	}

	/**
	 * The (unmodifiable) module stack: parent of the current module
	 * 
	 * @return
	 */
	public final Collection<VertxModule> stack() {
		return Collections.unmodifiableCollection(this.stack);
	}

	/**
	 * Same as {@link #visit(String, ModuleVisitor)}, except that all exceptions
	 * are converted into RuntimeException.
	 * 
	 * @param modName
	 * @param visitor
	 * @return
	 */
	public final T visit2(String modName, ModuleVisitor<T> visitor) {
		try {
			return visit(modName, visitor);
		} catch (Exception ex) {
			throw new RuntimeException(ex.getMessage(), ex);
		}
	}

	/**
	 * Walk the module tree
	 * 
	 * @param modName
	 *          The module to start with
	 * @param visitor
	 *          User code invoked upon a visit
	 * @return See {@link #result()} and {@link #result(Object)}
	 * @throws Exception
	 */
	public final T visit(String modName, ModuleVisitor<T> visitor) throws Exception {
		Args.notNull(modName, "modName");
		this.visitor = Args.notNull(visitor, "visitor");

		VertxModule module = module(modName);
		if (!module.exists()) {
			if (visitor.onMissingModule(module, this)) {
				module.loadConfig(true);
			}
		}

		this.stack.push(module);
		visitModule(module);
		this.stack.pop();

		return result;
	}

	/**
	 * Visit all includes of a module
	 */
	private ModuleVisitResult visitIncludes(final VertxModule mod) throws Exception {
		this.stack.push(mod);
		ModuleVisitResult res = ModuleVisitResult.CONTINUE;
		try {
			for (String modName : mod.config().includes()) {
				VertxModule module = module(modName);
				if (!module.exists()) {
					if (visitor.onMissingModule(module, this)) {
						module.loadConfig(true);
					}
				}

				res = visitModule(module);
				if (res == null) {
					res = ModuleVisitResult.CONTINUE;
				}
				if (res == ModuleVisitResult.TERMINATE) {
					return res;
				} else if (res == ModuleVisitResult.SKIP_SIBLINGS) {
					return ModuleVisitResult.CONTINUE;
				}
			}
		} finally {
			this.stack.pop();
		}

		return res;
	}

	/**
	 * Invoke the client visitor on a specific module and continue depending on
	 * the return value
	 */
	private ModuleVisitResult visitModule(final VertxModule module) throws Exception {
		ModuleVisitResult res = ModuleVisitResult.TERMINATE;
		try {
			res = visitor.visit(module, this);
		} catch (Exception ex) {
			res = visitor.onException(module, this, ex);
		}
		if (res == ModuleVisitResult.TERMINATE) {
			return res;
		} else if (res == ModuleVisitResult.SKIP_SIBLINGS) {
			return res;
		} else if (res == ModuleVisitResult.SKIP_SUBTREE) {
			return res;
		}

		res = visitIncludes(module);
		if (res != null && res == ModuleVisitResult.TERMINATE) {
			return res;
		}
		return ModuleVisitResult.CONTINUE;
	}

	/**
	 * Must be provided (and extended) by the user
	 * 
	 * @param <T>
	 */
	public abstract static class ModuleVisitor<T> {

		/**
		 * Invoked for each module found.
		 */
		protected abstract ModuleVisitResult visit(VertxModule module, ModuleWalker<T> walker);

		/**
		 * Upon an exception. By default re-throws the exception.
		 */
		protected ModuleVisitResult onException(VertxModule module, ModuleWalker<T> walker, Exception ex) throws Exception {
			throw ex;
		}

		/**
		 * Upon an exception. By default re-throws the exception.
		 */
		protected boolean onMissingModule(VertxModule module, ModuleWalker<T> walker) throws Exception {
			return false;
		}
	}

	/**
   * 
   */
	public enum ModuleVisitResult {
		CONTINUE, TERMINATE, SKIP_SUBTREE, SKIP_SIBLINGS;
	}
}
