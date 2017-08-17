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

package io.vertx.core.streams.impl;

import java.util.Objects;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.VertxException;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;


@VertxGen
public class PipeImpl<T> implements Pipe {

	private ReadStream<T> _pipedStream;
	private Pump pump;
	private Boolean ended;

	public PipeImpl(ReadStream<T> rs) {
		Objects.requireNonNull(rs);
		this._pipedStream = rs;
		this.ended = false;
		this.pump = null;
	}

	@Override
	public Pipe pipe(WriteStream ws) {
		Objects.requireNonNull(ws);
		if (ended) {
			throw new VertxException("Piped Flow is ended");		
		}
		if (pump != null) {
			// start previous pipe
			pump.start();
		}
		pump = Pump.pump(_pipedStream, ws);
		// https://github.com/eclipse/vert.x/issues/1465
		_pipedStream.endHandler(v -> ws.end());
		if ((ws instanceof ReadStream) == false) {			
			ended = true;
		} else {
			_pipedStream = (ReadStream)ws;
		}
		return this;
	}

	@Override
	public Pipe start() {
		if (pump != null) {
			pump.start();
		}

		return this;
	}

	@Override
	public Pipe stop() {
		if (pump != null) {
			pump.stop();
		}

		return this;
	}
}
