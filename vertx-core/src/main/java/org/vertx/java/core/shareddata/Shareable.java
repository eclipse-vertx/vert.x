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

package org.vertx.java.core.shareddata;

/**
 * Marker interface.<p>
 * If a class implements this it means it can be put into shared data maps
 * and sets.<p>
 * All classes that implement this MUST be threadsafe.<p>
 * Use this interface with caution.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Shareable {
}
