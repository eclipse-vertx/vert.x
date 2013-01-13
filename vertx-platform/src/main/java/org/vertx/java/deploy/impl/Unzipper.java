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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * Utility class to unzip the content of a jar file.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class Unzipper {

	private static final Logger log = LoggerFactory.getLogger(Unzipper.class);

	private static final int BUFFER_SIZE = 4096 * 8;

	/**
	 * Constructor
	 * 
	 */
	public Unzipper() {
	}

	/**
	 * 
	 * @param modRoot
	 * @param modName
	 * @param data
	 * @return
	 */
	@SuppressWarnings("resource")
	public boolean unzipModule(final File modRoot, final String modName, final Buffer data) {
		if (!modRoot.exists()) {
			if (!modRoot.mkdir()) {
				throw new RuntimeException("Failed to create directory: " + modRoot.getAbsolutePath());
			}
		}

		log.debug("Unzip module " + modName + " into directory: " + modRoot.getAbsolutePath());
		File fdest = new File(modRoot, modName);
		if (fdest.exists()) {
			// We assume it has been installed already, or is being installed by
			// another thread.
			log.info("The module directory already exists."
					+ " We assume a race condition and that another thread is installing the same module. "
					+ " Though it might as well be an incomplete uninstall or deleting of the module directory? "
					+ " Module Dir: " + fdest.getAbsolutePath());

			return false;
		}

		try (InputStream is = new ByteArrayInputStream(data.getBytes());
				BufferedInputStream bis = new BufferedInputStream(is);
				ZipInputStream zis = new ZipInputStream(bis)) {

			byte[] buff = new byte[BUFFER_SIZE];
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				// Entry name might be 'mymod/mod.json' or
				// 'mymod/bin/groovy/myscript.groovy'
				// TODO Is the jar entry path separator always "/" or can it be "\\" as
				// well?
				// if (!entry.getName().startsWith(modName+"/")) {
				if (!entry.getName().startsWith(modName)) {
					log.warn("Ignoring zip file entry because the first path element does not match the module name: " + modName
							+ "; zip entry: " + entry.getName() + "; zip file: ???");

					continue;
				}

				File newFile = new File(modRoot, entry.getName());
				if (entry.isDirectory()) {
					if (newFile.mkdir() == false) {
						throw new RuntimeException("Failed to create directory: " + newFile.getAbsolutePath());
					}
				} else {
					try (OutputStream fos = new FileOutputStream(newFile);
							BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER_SIZE)) {
						int count;
						while ((count = zis.read(buff, 0, BUFFER_SIZE)) != -1) {
							dest.write(buff, 0, count);
						}
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to unzip module: " + modName, e);
		}

		return true;
	}
}
