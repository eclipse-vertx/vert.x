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
(function(){
    var vertx = {};

    require('core/buffer.js')(vertx);
    require('core/event_bus.js')(vertx);
    require('core/net.js')(vertx);
    require('core/http.js')(vertx);
    require('core/streams.js')(vertx);
    require('core/timers.js')(vertx);
    require('core/utils.js')(vertx);
    require('core/sockjs.js')(vertx);
    require('core/parse_tools.js')(vertx);
    require('core/shared_data.js')(vertx);
    require('core/filesystem.js')(vertx);
    require('core/deploy.js')(vertx);
    require('core/logger.js')(vertx);
    require('core/env.js')(vertx);

    (this.module && module.exports) ? module.exports = vertx : this.vertx = vertx;
})();
