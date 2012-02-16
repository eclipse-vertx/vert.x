/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.http.ws.hybi08;

import org.vertx.java.core.http.ws.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

public class WebSocketChallenge08 {

  private static Logger log = LoggerFactory.getLogger(WebSocketChallenge08.class);

  public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
  public static final String SHA1 = "SHA1";

  private byte[] rawNonce;

  public WebSocketChallenge08() throws NoSuchAlgorithmException {
    generateNonce();
  }

  protected void generateNonce() {
    this.rawNonce = new byte[16];
    Random random = new SecureRandom();
    random.nextBytes(this.rawNonce);
  }

  public String getNonceBase64() {
    return Base64.encodeBytes(this.rawNonce).trim();
  }

  public static String solve(String nonceBase64) throws NoSuchAlgorithmException {
    String concat = nonceBase64 + GUID;
    MessageDigest digest = MessageDigest.getInstance(SHA1);
    digest.update(concat.getBytes());
    byte[] hashed = digest.digest();
    return Base64.encodeBytes(hashed).trim();
  }

  public boolean verify(String solution) throws NoSuchAlgorithmException {
    if (solution == null) {
      return false;
    }
    String localSolution = solve(getNonceBase64());
    return localSolution.equals(solution);
  }
}
