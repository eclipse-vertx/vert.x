/*
 * Copyright (c) 2011-2017 The original author or authors
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

package io.vertx.core.streams;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.streams.impl.PipeImpl;

/**
 * 
 * @author arnaud le roy
 *
 */
@VertxGen
public interface Pipe {
	
	/**
	 * 
	 * @param rs
	 * @return
	 */
	static <T> Pipe create(ReadStream<T> rs) {
		return new PipeImpl<>(rs);
	}

	/**
	 * 
	 * @param ws
	 * @return
	 */
	@Fluent
	public <T> Pipe pipe(WriteStream<T> ws);

	/**
	 * 
	 * @return
	 */
	@Fluent
	public Pipe start();

	/**
	 * 
	 * @return
	 */
	@Fluent
	public Pipe stop();
}
