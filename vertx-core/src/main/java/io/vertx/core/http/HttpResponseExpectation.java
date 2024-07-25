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
package io.vertx.core.http;

import io.vertx.core.Expectation;
import io.vertx.core.VertxException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Common expectations for HTTP responses.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpResponseExpectation extends Expectation<HttpResponseHead> {

  /**
   * Any 1XX informational response
   */
  HttpResponseExpectation SC_INFORMATIONAL_RESPONSE = status(100, 200);

  /**
   * 100 Continue
   */
  HttpResponseExpectation SC_CONTINUE = status(100);

  /**
   * 101 Switching Protocols
   */
  HttpResponseExpectation SC_SWITCHING_PROTOCOLS = status(101);

  /**
   * 102 Processing (WebDAV, RFC2518)
   */
  HttpResponseExpectation SC_PROCESSING = status(102);

  /**
   * 103 Early Hints
   */
  HttpResponseExpectation SC_EARLY_HINTS = status(103);

  /**
   * Any 2XX success
   */
  HttpResponseExpectation SC_SUCCESS = status(200, 300);

  /**
   * 200 OK
   */
  HttpResponseExpectation SC_OK = status(200);

  /**
   * 201 Created
   */
  HttpResponseExpectation SC_CREATED = status(201);

  /**
   * 202 Accepted
   */
  HttpResponseExpectation SC_ACCEPTED = status(202);

  /**
   * 203 Non-Authoritative Information (since HTTP/1.1)
   */
  HttpResponseExpectation SC_NON_AUTHORITATIVE_INFORMATION = status(203);

  /**
   * 204 No Content
   */
  HttpResponseExpectation SC_NO_CONTENT = status(204);

  /**
   * 205 Reset Content
   */
  HttpResponseExpectation SC_RESET_CONTENT = status(205);

  /**
   * 206 Partial Content
   */
  HttpResponseExpectation SC_PARTIAL_CONTENT = status(206);

  /**
   * 207 Multi-Status (WebDAV, RFC2518)
   */
  HttpResponseExpectation SC_MULTI_STATUS = status(207);

  /**
   * Any 3XX redirection
   */
  HttpResponseExpectation SC_REDIRECTION = status(300, 400);

  /**
   * 300 Multiple Choices
   */
  HttpResponseExpectation SC_MULTIPLE_CHOICES = status(300);

  /**
   * 301 Moved Permanently
   */
  HttpResponseExpectation SC_MOVED_PERMANENTLY = status(301);

  /**
   * 302 Found
   */
  HttpResponseExpectation SC_FOUND = status(302);

  /**
   * 303 See Other (since HTTP/1.1)
   */
  HttpResponseExpectation SC_SEE_OTHER = status(303);

  /**
   * 304 Not Modified
   */
  HttpResponseExpectation SC_NOT_MODIFIED = status(304);

  /**
   * 305 Use Proxy (since HTTP/1.1)
   */
  HttpResponseExpectation SC_USE_PROXY = status(305);

  /**
   * 307 Temporary Redirect (since HTTP/1.1)
   */
  HttpResponseExpectation SC_TEMPORARY_REDIRECT = status(307);

  /**
   * 308 Permanent Redirect (RFC7538)
   */
  HttpResponseExpectation SC_PERMANENT_REDIRECT = status(308);

  /**
   * Any 4XX client error
   */
  HttpResponseExpectation SC_CLIENT_ERRORS = status(400, 500);

  /**
   * 400 Bad Request
   */
  HttpResponseExpectation SC_BAD_REQUEST = status(400);

  /**
   * 401 Unauthorized
   */
  HttpResponseExpectation SC_UNAUTHORIZED = status(401);

  /**
   * 402 Payment Required
   */
  HttpResponseExpectation SC_PAYMENT_REQUIRED = status(402);

  /**
   * 403 Forbidden
   */
  HttpResponseExpectation SC_FORBIDDEN = status(403);

  /**
   * 404 Not Found
   */
  HttpResponseExpectation SC_NOT_FOUND = status(404);

  /**
   * 405 Method Not Allowed
   */
  HttpResponseExpectation SC_METHOD_NOT_ALLOWED = status(405);

  /**
   * 406 Not Acceptable
   */
  HttpResponseExpectation SC_NOT_ACCEPTABLE = status(406);

  /**
   * 407 Proxy Authentication Required
   */
  HttpResponseExpectation SC_PROXY_AUTHENTICATION_REQUIRED = status(407);

  /**
   * 408 Request Timeout
   */
  HttpResponseExpectation SC_REQUEST_TIMEOUT = status(408);

  /**
   * 409 Conflict
   */
  HttpResponseExpectation SC_CONFLICT = status(409);

  /**
   * 410 Gone
   */
  HttpResponseExpectation SC_GONE = status(410);

  /**
   * 411 Length Required
   */
  HttpResponseExpectation SC_LENGTH_REQUIRED = status(411);

  /**
   * 412 Precondition Failed
   */
  HttpResponseExpectation SC_PRECONDITION_FAILED = status(412);

  /**
   * 413 Request Entity Too Large
   */
  HttpResponseExpectation SC_REQUEST_ENTITY_TOO_LARGE = status(413);

  /**
   * 414 Request-URI Too Long
   */
  HttpResponseExpectation SC_REQUEST_URI_TOO_LONG = status(414);

  /**
   * 415 Unsupported Media Type
   */
  HttpResponseExpectation SC_UNSUPPORTED_MEDIA_TYPE = status(415);

  /**
   * 416 Requested Range Not Satisfiable
   */
  HttpResponseExpectation SC_REQUESTED_RANGE_NOT_SATISFIABLE = status(416);

  /**
   * 417 Expectation Failed
   */
  HttpResponseExpectation SC_EXPECTATION_FAILED = status(417);

  /**
   * 421 Misdirected Request
   */
  HttpResponseExpectation SC_MISDIRECTED_REQUEST = status(421);

  /**
   * 422 Unprocessable Entity (WebDAV, RFC4918)
   */
  HttpResponseExpectation SC_UNPROCESSABLE_ENTITY = status(422);

  /**
   * 423 Locked (WebDAV, RFC4918)
   */
  HttpResponseExpectation SC_LOCKED = status(423);

  /**
   * 424 Failed Dependency (WebDAV, RFC4918)
   */
  HttpResponseExpectation SC_FAILED_DEPENDENCY = status(424);

  /**
   * 425 Unordered Collection (WebDAV, RFC3648)
   */
  HttpResponseExpectation SC_UNORDERED_COLLECTION = status(425);

  /**
   * 426 Upgrade Required (RFC2817)
   */
  HttpResponseExpectation SC_UPGRADE_REQUIRED = status(426);

  /**
   * 428 Precondition Required (RFC6585)
   */
  HttpResponseExpectation SC_PRECONDITION_REQUIRED = status(428);

  /**
   * 429 Too Many Requests (RFC6585)
   */
  HttpResponseExpectation SC_TOO_MANY_REQUESTS = status(429);

  /**
   * 431 Request Header Fields Too Large (RFC6585)
   */
  HttpResponseExpectation SC_REQUEST_HEADER_FIELDS_TOO_LARGE = status(431);

  /**
   * Any 5XX server error
   */
  HttpResponseExpectation SC_SERVER_ERRORS = status(500, 600);

  /**
   * 500 Internal Server Error
   */
  HttpResponseExpectation SC_INTERNAL_SERVER_ERROR = status(500);

  /**
   * 501 Not Implemented
   */
  HttpResponseExpectation SC_NOT_IMPLEMENTED = status(501);

  /**
   * 502 Bad Gateway
   */
  HttpResponseExpectation SC_BAD_GATEWAY = status(502);

  /**
   * 503 Service Unavailable
   */
  HttpResponseExpectation SC_SERVICE_UNAVAILABLE = status(503);

  /**
   * 504 Gateway Timeout
   */
  HttpResponseExpectation SC_GATEWAY_TIMEOUT = status(504);

  /**
   * 505 HTTP Version Not Supported
   */
  HttpResponseExpectation SC_HTTP_VERSION_NOT_SUPPORTED = status(505);

  /**
   * 506 Variant Also Negotiates (RFC2295)
   */
  HttpResponseExpectation SC_VARIANT_ALSO_NEGOTIATES = status(506);

  /**
   * 507 Insufficient Storage (WebDAV, RFC4918)
   */
  HttpResponseExpectation SC_INSUFFICIENT_STORAGE = status(507);

  /**
   * 510 Not Extended (RFC2774)
   */
  HttpResponseExpectation SC_NOT_EXTENDED = status(510);

  /**
   * 511 Network Authentication Required (RFC6585)
   */
  HttpResponseExpectation SC_NETWORK_AUTHENTICATION_REQUIRED = status(511);

  /**
   * Creates an expectation asserting that the status response code is equal to {@code statusCode}.
   *
   * @param statusCode the expected status code
   */
  static HttpResponseExpectation status(int statusCode) {
    return status(statusCode, statusCode + 1);
  }

  static HttpResponseExpectation status(int min, int max) {
    return new HttpResponseExpectation() {
      @Override
      public boolean test(HttpResponseHead value) {
        int sc = value.statusCode();
        return sc >= min && sc < max;
      }

      @Override
      public Exception describe(HttpResponseHead value) {
        int sc = value.statusCode();
        if (max - min == 1) {
          return new VertxException("Response status code " + sc + " is not equal to " + min, true);
        }
        return new VertxException("Response status code " + sc + " is not between " + min + " and " + max, true);
      }
    };
  }

  /**
   * Creates an expectation validating the response {@code content-type} is {@code application/json}.
   */
  HttpResponseExpectation JSON = contentType("application/json");

  /**
   * Creates an expectation validating the response has a {@code content-type} header matching the {@code mimeType}.
   *
   * @param mimeType the mime type
   */
  static HttpResponseExpectation contentType(String mimeType) {
    return contentType(Collections.singletonList(mimeType));
  }

  /**
   * Creates an expectation validating the response has a {@code content-type} header matching one of the {@code mimeTypes}.
   *
   * @param mimeTypes the list of mime types
   */
  static HttpResponseExpectation contentType(String... mimeTypes) {
    return contentType(Arrays.asList(mimeTypes));
  }

  /**
   * Creates an expectation validating the response has a {@code content-type} header matching one of the {@code mimeTypes}.
   *
   * @param mimeTypes the list of mime types
   */
  static HttpResponseExpectation contentType(List<String> mimeTypes) {
    return new HttpResponseExpectation() {
      @Override
      public boolean test(HttpResponseHead value) {
        String contentType = value.headers().get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
          return false;
        }
        int paramIdx = contentType.indexOf(';');
        String mediaType = paramIdx != -1 ? contentType.substring(0, paramIdx) : contentType;

        for (String mimeType : mimeTypes) {
          if (mediaType.equalsIgnoreCase(mimeType)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public Exception describe(HttpResponseHead value) {
        String contentType = value.headers().get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
          return new VertxException("Missing response content type", true);
        }
        StringBuilder sb = new StringBuilder("Expect content type ").append(contentType).append(" to be one of ");
        boolean first = true;
        for (String mimeType : mimeTypes) {
          if (!first) {
            sb.append(", ");
          }
          first = false;
          sb.append(mimeType);
        }
        return new VertxException(sb.toString(), true);
      }
    };
  }
}
