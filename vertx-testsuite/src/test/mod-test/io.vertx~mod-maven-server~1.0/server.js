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

var vertx = require('vertx')

var sent302 = false;
vertx.createHttpServer().requestHandler(function(req) {
  if (req.uri().indexOf("..") !== -1) {
    req.response.statusCode(403).end();
  } else {
    // Maven can send redirects so we test this by sending one first
    if (!sent302) {
      req.response.statusCode(302);
      req.response.putHeader('location', 'http://localhost:9193' + req.uri());
      req.response.end();
      sent302 = true;
    } else {
      var file = '.' + req.uri();
      req.response.sendFile(file)
    }
  }
}).listen(9193)