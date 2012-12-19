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
package org.vertx.java.tests.core.impl;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.impl.ActionFuture;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * 
 * @author Juergen Donnerstag
 */
public class BlockingActionTest {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(BlockingActionTest.class);

	private static VertxInternal vertx;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		vertx = new DefaultVertx();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		vertx.stop();
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testNoHandler() throws InterruptedException {
		final AtomicInteger actionCount = new AtomicInteger(0);
		BlockingAction<Boolean> a = new BlockingAction<Boolean>(vertx) {
			@Override
			public Boolean action() throws Exception {
				actionCount.incrementAndGet();
				return true;
			}
		};
		ActionFuture<Boolean> f = a.run();
		assertNotNull(f);
		AsyncResult<Boolean> res = f.get();
		assertEquals(1, actionCount.get());
		assertNotNull(res);
		assertTrue(res.result);
		assertTrue(res.succeeded());
		assertFalse(res.failed());
		assertNull(res.exception);
	}

	@Test
	public void testSubclassedHandler() throws InterruptedException {
		final AtomicInteger actionCount = new AtomicInteger(0);
		final AtomicInteger handlerCount = new AtomicInteger(0);
		BlockingAction<Boolean> a = new BlockingAction<Boolean>(vertx) {
			@Override
			public Boolean action() throws Exception {
				actionCount.incrementAndGet();
				return true;
			}
			@Override
			protected void handle(AsyncResult<Boolean> result) {
				handlerCount.incrementAndGet();
			}
		};
		AsyncResult<Boolean> res = a.run().get();
		assertEquals(1, actionCount.get());
		assertEquals(1, handlerCount.get());
		assertNotNull(res);
		assertTrue(res.result);
		assertTrue(res.succeeded());
		assertFalse(res.failed());
		assertNull(res.exception);
	}

	@Test
	public void testHandler() throws InterruptedException {
		final AtomicInteger actionCount = new AtomicInteger(0);
		final AtomicInteger handlerCount = new AtomicInteger(0);
		final AtomicInteger derivedHandlerCount = new AtomicInteger(0);
		
		AsyncResultHandler<Boolean> handler = new AsyncResultHandler<Boolean>() {
			@Override
			public void handle(AsyncResult<Boolean> event) {
				handlerCount.incrementAndGet();
			}
		};
		
		BlockingAction<Boolean> a = new BlockingAction<Boolean>(vertx, handler) {
			@Override
			public Boolean action() throws Exception {
				actionCount.incrementAndGet();
				return true;
			}
			@Override
			protected void handle(AsyncResult<Boolean> result) {
				derivedHandlerCount.incrementAndGet();
			}
		};
		AsyncResult<Boolean> res = a.run().get();
		assertEquals(1, actionCount.get());
		assertEquals(0, derivedHandlerCount.get());
		assertEquals(1, handlerCount.get());
		assertNotNull(res);
		assertTrue(res.result);
		assertTrue(res.succeeded());
		assertFalse(res.failed());
		assertNull(res.exception);
	}

	@Test
	public void testException() throws InterruptedException {
		final AtomicInteger actionCount = new AtomicInteger(0);
		final AtomicInteger handlerCount = new AtomicInteger(0);
		BlockingAction<Boolean> a = new BlockingAction<Boolean>(vertx) {
			@Override
			public Boolean action() throws Exception {
				actionCount.incrementAndGet();
				throw new RuntimeException();
			}
			@Override
			protected void handle(AsyncResult<Boolean> result) {
				handlerCount.incrementAndGet();
			}
		};
		AsyncResult<Boolean> res = a.run().get();
		assertEquals(1, actionCount.get());
		assertEquals(1, handlerCount.get());
		assertNotNull(res);
		assertFalse(res.succeeded());
		assertTrue(res.failed());
		assertNotNull(res.exception);
	}

	@Test
	public void testTimeoutNotFired() throws InterruptedException {
		final AtomicInteger actionCount = new AtomicInteger(0);
		final AtomicInteger handlerCount = new AtomicInteger(0);
		final AtomicInteger timeoutCount = new AtomicInteger(0);
		BlockingAction<Boolean> a = new BlockingAction<Boolean>(vertx) {
			@Override
			public Boolean action() throws Exception {
				actionCount.incrementAndGet();
				Thread.sleep(500);
				return true;
			}
			@Override
			protected void handle(AsyncResult<Boolean> result) {
				handlerCount.incrementAndGet();
			}
			
			@Override
			protected void onTimeout(String timeoutMessage, ActionFuture<Boolean> future) {
				timeoutCount.incrementAndGet();
				super.onTimeout(timeoutMessage, future);
			}
		};
		ActionFuture<Boolean> f = a.run(1_000, TimeUnit.MILLISECONDS, null);
		assertEquals(1, actionCount.get());
		assertEquals(1, handlerCount.get());
		assertEquals(0, timeoutCount.get());
		assertNotNull(f.result());
		assertTrue(f.result().succeeded());
		assertFalse(f.result().failed());
		assertNull(f.result().exception);
	}

	@Test
	public void testTimeoutFired() throws InterruptedException {
		final AtomicInteger actionCount = new AtomicInteger(0);
		final AtomicInteger handlerCount = new AtomicInteger(0);
		final AtomicInteger timeoutCount = new AtomicInteger(0);
		BlockingAction<Boolean> a = new BlockingAction<Boolean>(vertx) {
			@Override
			public Boolean action() throws Exception {
				actionCount.incrementAndGet();
				Thread.sleep(1_000);
				return true;
			}

			@Override
			protected void handle(AsyncResult<Boolean> result) {
				handlerCount.incrementAndGet();
			}
		
			@Override
			protected void onTimeout(String timeoutMessage, ActionFuture<Boolean> future) {
				timeoutCount.incrementAndGet();
				super.onTimeout(timeoutMessage, future);
			}
		};
		ActionFuture<Boolean> f = a.run(200, TimeUnit.MILLISECONDS, null);
		assertEquals(1, actionCount.get());
		assertEquals(1, handlerCount.get());
		assertEquals(1, timeoutCount.get());
		assertNotNull(f.result());
		assertFalse(f.result().succeeded());
		assertTrue(f.result().failed());
		assertNotNull(f.result().exception);
	}
}
