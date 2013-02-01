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

var vertx = vertx || {};

if (!vertx.logger) {
  vertx.logger = org.vertx.java.deploy.impl.VertxLocator.container.getLogger();

  // Add a console object which will be familiar to JavaScript devs
  var console = {
    // TODO this should take varargs and allow formatting a la sprintf
    log: function(msg) {
      stdout.println(msg);
    },
    warn: function(msg) {
      stderr.println(msg);
    },
    error: function(msg) {
      stderr.println(msg);
    }
  };
}
