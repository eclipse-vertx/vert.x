/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform;

import java.util.ServiceLoader;

/**
 * Use this class to get an instance of a PlatformManagerFactory so you can create PlatformManager instances when
 * embedding the Vert.x platform.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PlatformLocator {

  public static PlatformManagerFactory factory = ServiceLoader.load(PlatformManagerFactory.class).iterator().next();

}
