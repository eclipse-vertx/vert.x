/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core;

import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * Abstracts base64 encoding within vert.x, delegates to {@link Base64} or {@link org.apache.commons.codec.binary.Base64}
 * depending on the configuration, see {@link io.vertx.core.json.impl.JsonUtil}
 */
public class VertxBase64 {

  public static class Encoder {

    private final Base64.Encoder encoder;

    public Encoder(boolean legacyMode) {
      if (legacyMode) {
        encoder = Base64.getEncoder();
      } else {
        encoder = Base64.getUrlEncoder().withoutPadding();
      }
    }

    public String encodeToString(byte[] src) {
      return encoder.encodeToString(src);
    }

    public byte[] encode(byte[] src) {
      return encoder.encode(src);
    }

    public int encode(byte[] src, byte[] dst) {
      return encoder.encode(src, dst);
    }

    public ByteBuffer encode(ByteBuffer buffer) {
      return encoder.encode(buffer);
    }

  }

  public static class Decoder {

    private final VertxBase64Decoder decoder;

    public Decoder(boolean legacyMode, boolean useApacheImplementation) {
      if (useApacheImplementation) {
        // supports both modes
        decoder = new VertxBase64DecoderApacheImpl();
      } else {
        decoder = new VertxBase64DecoderJdkImpl(legacyMode);
      }
    }

    public byte[] decode(String src) {
      return decoder.decode(src);
    }
  }

  private interface VertxBase64Decoder {
    byte[] decode(String src);
  }

  private static class VertxBase64DecoderJdkImpl implements VertxBase64Decoder {

    private final Base64.Decoder decoder;

    public VertxBase64DecoderJdkImpl(boolean legacyMode) {
      if (legacyMode) {
        decoder = Base64.getDecoder();
      } else {
        decoder = Base64.getUrlDecoder();
      }
    }

    @Override
    public byte[] decode(String src) {
      return decoder.decode(src);
    }
  }

  private static class VertxBase64DecoderApacheImpl implements VertxBase64Decoder {

    org.apache.commons.codec.binary.Base64 decoder = new org.apache.commons.codec.binary.Base64();

    @Override
    public byte[] decode(String src) {
      return decoder.decode(src);
    }
  }


}
