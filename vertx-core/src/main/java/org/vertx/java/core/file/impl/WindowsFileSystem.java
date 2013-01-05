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

package org.vertx.java.core.file.impl;

import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class WindowsFileSystem extends DefaultFileSystem {

	private static final Logger log = LoggerFactory.getLogger(WindowsFileSystem.class);

	public WindowsFileSystem(final VertxInternal vertx) {
		super(vertx);
	}

	private void logInternal(final String perms) {
		if ((perms != null) && log.isDebugEnabled()) {
			log.debug("You are running on Windows and POSIX style file permissions are not supported");
		}
	}

	@Override
	protected BlockingAction<Void> chmodInternal(String path, String perms, String dirPerms,
			AsyncResultHandler<Void> handler) {
		logInternal(perms);
		logInternal(dirPerms);
		return new BlockingAction<Void>(vertx, handler) {
			@Override
			public Void action() throws Exception {
				return null;
			}
		};
	}

	@Override
	protected BlockingAction<Void> mkdirInternal(String path, final String perms, final boolean createParents,
			AsyncResultHandler<Void> handler) {
		logInternal(perms);
		return super.mkdirInternal(path, null, createParents, handler);
	}

	@Override
	protected AsyncFile doOpen(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush,
			Context context) throws Exception {
		logInternal(perms);
		return new DefaultAsyncFile(vertx, path, null, read, write, createNew, flush, context);
	}

	@Override
	protected BlockingAction<Void> createFileInternal(String p, final String perms, AsyncResultHandler<Void> handler) {
		logInternal(perms);
		return super.createFileInternal(p, null, handler);
	}
}
