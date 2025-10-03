/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.digest;

import io.vertx.core.internal.digest.Signer;
import io.vertx.core.internal.digest.SigningAlgorithm;
import io.vertx.core.internal.digest.ThreadLocalSigningAlgorithm;
import io.vertx.core.internal.digest.ThreadSafeSigningAlgorithm;
import io.vertx.core.internal.digest.Verifier;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SigningAlgorithmTest extends VertxTestBase {

  @Test
  public void testHS256() throws Exception {
    testAlgo("HmacSHA256", "HmacSHA256");
    testAlgo("HmacSHA384", "HmacSHA384");
    testAlgo("HmacSHA512", "HmacSHA512");
  }

  @Test
  public void testRSA() throws Exception {
    testAlgo("SHA256withRSA", "SHA256withRSA");
    testAlgo("SHA384withRSA", "SHA384withRSA");
    testAlgo("SHA512withRSA", "SHA512withRSA");
  }

  @Test
  public void testECDSA() throws Exception {
    testAlgo("SHA256withECDSA", "SHA256withECDSA");
    testAlgo("SHA384withECDSA", "SHA384withECDSA");
    testAlgo("SHA512withECDSA", "SHA512withECDSA");
  }

  @Test
  public void threadSafetyViaExclusion() throws Exception {
    SigningAlgorithm algo = algo("HmacSHA256");
    threadSafety(new ThreadSafeSigningAlgorithm(algo));
  }

  @Test
  public void threadSafetyViaConfinement() throws Exception {
    SigningAlgorithm algo = algo("HmacSHA256");
    threadSafety(new ThreadLocalSigningAlgorithm(algo));
  }

  private void threadSafety(SigningAlgorithm algo) throws Exception {
    int numThreads = 16;
    int numOps = 1000;
    byte[] payload = TestUtils.randomByteArray(512);
    ExecutorService exec = Executors.newFixedThreadPool(numThreads);
    Deque<Future<Boolean>> results = new ArrayDeque<>();
    Signer signer = algo.signer();
    Verifier verifier = algo.verifier();
    for (int i = 0;i < numOps;i++) {
      Future<Boolean> fut = exec.submit(() -> {
        byte[] signature = signer.sign(payload);
        return verifier.verify(signature, payload);
      });
      results.add(fut);
    }
    for (Future<Boolean> future = results.poll();future != null;future = results.poll()) {
      assertTrue(future.get(10, TimeUnit.SECONDS));
    }
  }

  private void testAlgo(String alias, String expectedName) throws Exception {
    SigningAlgorithm algo = algo(alias);
    assertEquals(expectedName, algo.name());
    byte[] signature = algo.signer().sign("foo".getBytes(StandardCharsets.UTF_8));
    assertTrue(algo.verifier().verify(signature, "foo".getBytes()));
  }

  private SigningAlgorithm algo(String alias) throws Exception {
    InputStream keys = new ByteArrayInputStream(vertx.fileSystem().readFileBlocking("digest/keystore.p12").getBytes());
    assertNotNull(keys);
    KeyStore store = KeyStore.getInstance("pkcs12");
    store.load(keys, "secret".toCharArray());
    KeyStore.Entry entry = store.getEntry(alias, new KeyStore.PasswordProtection("secret".toCharArray()));
    return SigningAlgorithm.create(entry);
  }
}
