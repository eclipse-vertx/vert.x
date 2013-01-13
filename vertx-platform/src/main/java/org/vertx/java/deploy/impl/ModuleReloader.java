package org.vertx.java.deploy.impl;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ModuleReloader {
	void reloadModules(Set<Deployment> parents);
}
