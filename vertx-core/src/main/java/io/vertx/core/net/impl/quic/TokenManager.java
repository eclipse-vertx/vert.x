/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.quic.QuicTokenHandler;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.digest.SigningAlgorithm;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.KeyStoreOptionsBase;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.impl.KeyStoreHelper;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Performs Quic token creation and validation.
 *
 * <p>The manager issues signed tokens containing client information using a cryptographic key.</p>
 *
 * <p>Depending on the nature of the key, either symmetric (MAC) or asymmetric (Digital Signature) is achieved.</p>
 *
 * <p>Description of the token format:</p>
 * <ul>
 *   <li>Cryptographic signature of the computed client <i>payload</i> - length depends on the signature algorithm</li>
 *   <li>Destination connection ID - length is at most 20 bytes</li>
 * </ul>
 *
 * The payload is computed using:
 *
 * <ul>
 *   <li>The remote socket address</li>
 *   <li>The destination connection ID</li>
 *   <li>A timestamp</li>
 * </ul>
 *
 * <p>The <i>timestamp</i> is equals to the current time in nano divided by the manager time window in nanos. This procedure
 * divides the time in slots in which the signature remains valid. In order to account for the corner case of validating
 * a token at the end of a slot that would have been issued at the beginning of this slot, the previous slot is also
 * checked during token verification. Therefore, we only guarantee that a token remains valid for at most twice the duration
 * of the configured time window.</p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TokenManager implements QuicTokenHandler {

  private final VertxInternal vertx;
  private SigningAlgorithm signingAlgorithm;
  private Duration timeWindow;
  private int length;

  public TokenManager(VertxInternal vertx, Duration timeWindow) {
    this.vertx = Objects.requireNonNull(vertx);
    this.timeWindow = Objects.requireNonNull(timeWindow);
  }

  public void init(KeyCertOptions conf) throws Exception {
    KeyStore.Entry entry = null;
    if (conf instanceof KeyStoreOptionsBase) {
      KeyStoreOptionsBase keyStoreOptions = (KeyStoreOptionsBase) conf;
      KeyStoreHelper helper = keyStoreOptions.getHelper(vertx);
      KeyStore keystore = helper.store();
      Enumeration<String> aliases = keystore.aliases();
      if (aliases.hasMoreElements()) {
        entry = keystore.getEntry(aliases.nextElement(), new KeyStore.PasswordProtection(keyStoreOptions.getPassword().toCharArray()));
      }
    } else if (conf instanceof PemKeyCertOptions) {
      PemKeyCertOptions pemKeyCertOptions = (PemKeyCertOptions) conf;
      KeyStoreHelper helper = pemKeyCertOptions.getHelper(vertx);
      KeyStore keystore = helper.store();
      Enumeration<String> aliases = keystore.aliases();
      if (aliases.hasMoreElements()) {
        entry = keystore.getEntry(aliases.nextElement(), new KeyStore.PasswordProtection(KeyStoreHelper.DUMMY_PASSWORD.toCharArray()));
      }
    } else {
      throw new IllegalArgumentException("Invalid configuration");
    }
    if (entry != null) {
      signingAlgorithm = SigningAlgorithm.create(entry);
      length = signingAlgorithm.signer().sign(new byte[0]).length;
    } else {
      throw new IllegalArgumentException("KeyStore does not contains a valid entry");
    }
  }

  public SigningAlgorithm signingAlgorithm() {
    return signingAlgorithm;
  }

  public byte[] generateToken(byte[] payload) {
    ByteBuf out = Unpooled.buffer();
    writeToken(out, Unpooled.copiedBuffer(payload), new InetSocketAddress(NetUtil.LOCALHOST4, 8080));
    return ByteBufUtil.getBytes(out);
  }

  public boolean verify(byte[] token) throws Exception {
    return validateToken(Unpooled.copiedBuffer(token), new InetSocketAddress(NetUtil.LOCALHOST4, 8080)) >= 0;
  }

  private static byte[] basePayload(InetSocketAddress address, ByteBuf dcid) {
    byte[] addressBytes = address.getAddress().getAddress();
    int len = addressBytes.length + dcid.readableBytes() + 8;
    byte[] buffer = Arrays.copyOf(addressBytes, len);
    int offset = addressBytes.length;
    dcid.getBytes(dcid.readerIndex(), buffer, offset, dcid.readableBytes());
    return buffer;
  }

  private static void addTimestamp(byte[] buffer, long timestamp) {
    int offset = buffer.length - 8;
    buffer[offset] = (byte)(timestamp & 0xFF);
    buffer[offset + 1] = (byte)((timestamp >> 8) & 0xFF);
    buffer[offset + 2] = (byte)((timestamp >> 16) & 0xFF);
    buffer[offset + 3] = (byte)((timestamp >> 24) & 0xFF);
    buffer[offset + 4] = (byte)((timestamp >> 32) & 0xFF);
    buffer[offset + 5] = (byte)((timestamp >> 40) & 0xFF);
    buffer[offset + 6] = (byte)((timestamp >> 48) & 0xFF);
    buffer[offset + 7] = (byte)((timestamp >> 56) & 0xFF);
  }

  @Override
  public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
    long timestamp = System.nanoTime() / timeWindow.toNanos();
    byte[] payload = basePayload(address, dcid);
    addTimestamp(payload, timestamp);
    byte[] signature;
    try {
      signature = signingAlgorithm.signer().sign(payload);
    } catch (GeneralSecurityException e) {
      PlatformDependent.throwException(e);
      return false;
    }
    out.writeBytes(signature);
    out.writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes());
    return true;
  }

  @Override
  public int validateToken(ByteBuf token, InetSocketAddress address) {
    long timestamp = System.nanoTime() / timeWindow.toNanos();
    byte[] signature = new byte[length];
    token.getBytes(token.readerIndex(), signature);
    ByteBuf dcid = token.slice(token.readerIndex() + length, token.readableBytes() - length);
    byte[] payload = basePayload(address, dcid);
    try {
      for (int i = 0;i < 2;i++) {
        addTimestamp(payload, timestamp);
        if (signingAlgorithm.verifier().verify(signature, payload)) {
          return token.readerIndex() + length;
        } else {
          timestamp--;
        }
      }
    } catch (GeneralSecurityException ignore) {
    }
    return -1;
  }

  @Override
  public int maxTokenLength() {
    return 20 + length;
  }
}
