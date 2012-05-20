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
    load('core/buffer.js');
    load('core/event_bus.js');
    load('core/net.js');
    load('core/http.js');
    load('core/streams.js');
    load('core/timers.js');
    load('core/utils.js');
    load('core/sockjs.js');
    load('core/parse_tools.js');
    load('core/shared_data.js');
    load('core/filesystem.js');
    load('core/deploy.js');
    load('core/logger.js');
    (this.module && module.exports) ? module.exports = vertx : this.vertx = vertx;
})();
