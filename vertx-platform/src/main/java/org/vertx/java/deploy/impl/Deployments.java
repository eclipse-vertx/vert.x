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

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;

/**
 * Maintain the Deployment hierarchy
 * 
 * @author Juergen Donnerstag
 */
public class Deployments implements Iterable<String> {

	private static final Logger log = LoggerFactory.getLogger(Deployments.class);

	// deployment name --> deployment
	private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();

	public Deployments() {
	}

	public final Deployment get(final String name) {
		Args.notNull(name, "name");
		return deployments.get(name);
	}

	public final boolean isEmpty() {
		return deployments.isEmpty();
	}

	public final void add(final Deployment deployment) {
		Args.notNull(deployment, "deployment");
		String name = deployment.name;
		deployments.put(name, deployment);
		if (deployment.parentDeploymentName != null) {
			Deployment p = deployments.get(deployment.parentDeploymentName);
			if (p != null) {
				if (p.childDeployments.contains(name)) {
					throw new RuntimeException("The parent Deployment already contains a child with the very same name." 
							+ "child: " + name + "; parent: " + deployment.parentDeploymentName);
				}
				p.childDeployments.add(name);
			} else {
				throw new RuntimeException("Parent Deployment not found. Name: " + name);
			}
		}
	}

	public final Deployment remove(final String name) {
		Args.notNull(name, "name");
		
		final Deployment depl = deployments.get(name);
		if (depl == null) {
			return null;
		} 
		
//		if (!depl.childDeployments.isEmpty()) {
//			throw new RuntimeException("You can not remove Deployments which still have children: " + name);
//		}

		deployments.remove(name);
		
		if (depl.parentDeploymentName != null) {
			Deployment parent = deployments.get(depl.parentDeploymentName);
			if (parent == null) {
				log.warn("Parent Deployment not found. Name: " + depl.parentDeploymentName);
			} else if (!parent.childDeployments.remove(name)) {
				log.warn("Child Deployment '" + depl.name + "' not registered with parent '" + parent.name + "'");
			}
		}
		
		return depl;
	}

	@Override
	public Iterator<String> iterator() {
		return deployments.keySet().iterator();
	}

	public final void print(final PrintStream out) {
		Args.notNull(out, "out");
		int depth = 0;
		for (String name : this) {
			Deployment depl = get(name);
			if (depl.parentDeploymentName == null) {
				print(depl, depth, out);
			}
		}
	}

	public final int size() {
		return deployments.size();
	}
	
	/**
	 * Little utility to print out the hierarchy
	 */
	private void print(final Deployment root, final int depth, final PrintStream out) {
		for (String name : root.childDeployments) {
			Deployment depl = get(name);

			StringBuilder buf = new StringBuilder();
			for (int i = 0; i < depth; i++) {
				buf.append("--");
			}
			buf.append("- ").append(name);
			buf.append(" (module: ").append(depl.module.name());
			buf.append("; verticles: ").append(depl.verticles.size()).append(")");
			out.println(buf.toString());

			for (String child : depl.childDeployments) {
				print(get(child), depth + 1, out);
			}
		}
	}

	@Override
	public String toString() {
		int size = deployments.size();
		int rootDeployments = 0;
		int deepestHierarchy = 0;
		int verticles = 0;

		for (String name : this) {
			Deployment depl = get(name);
			if (depl.parentDeploymentName == null) {
				rootDeployments += 1;
			} else {
				int count = 1;
				Deployment p = deployments.get(depl.parentDeploymentName);
				while (p != null) {
					if (p.parentDeploymentName != null) {
						count += 1;
						p = deployments.get(p.parentDeploymentName);
					} else {
						p = null;
					}
				}
				deepestHierarchy = Math.max(deepestHierarchy, count);
			}
			verticles += depl.verticles.size();
		}

		return "Size: " + size + "; Roots: " + rootDeployments + "; deepest: " + deepestHierarchy + "; # verticles: "
				+ verticles;
	}
}
