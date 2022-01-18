/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import io.vertx.core.http.HttpTestBase;
import io.vertx.core.http.RequestOptions;

public class HttpClientRequestTimeoutTest extends HttpTestBase {

	@Test
	public void testTimeoutFlowWithRequestExceptionHandler() {
		server.requestHandler(req -> {
			vertx.setTimer(50L, delay -> {
				req.response().end();
			});
		});
		server.listen(testAddress, onSuccess(server -> {
			client.request(new RequestOptions(requestOptions).setURI("/").setTimeout(20), onSuccess(req -> {
				req.exceptionHandler(t -> {
					Assertions.fail("shouldn't be executed on a response timeout case");
				}).send(ar -> {
					Assertions.assertThat(ar.failed()).isTrue();
					Assertions.assertThat(ar.cause()).isInstanceOf(NoStackTraceTimeoutException.class);
					Assertions.assertThat(ar.cause().getMessage())
							.startsWith("The timeout period of 20ms has been exceeded while executing GET / for server localhost:8080");

					testComplete();
				});

			}));
		}));

		await(5L, TimeUnit.SECONDS);
	}
}
