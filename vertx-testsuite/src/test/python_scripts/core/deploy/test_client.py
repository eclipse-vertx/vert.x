# Copyright 2011-2012 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import vertx
from test_utils import TestUtils
from core.event_bus import EventBus

tu = TestUtils()



class DeployTest(object):

    handler_id = None

    def test_deploy(self):
        global handler_id
        def handler(message):
            if message.body == "started":
                tu.test_complete()
        handler_id = EventBus.register_handler("test-handler", False, handler)
        conf = {'foo' : 'bar'}
        vertx.deploy_verticle("core/deploy/child.py", conf)

    def test_undeploy(self):
        global handler_id
        def handler(message):
            if message.body == "stopped":
                tu.test_complete()
        handler_id = EventBus.register_handler("test-handler", False, handler)
        conf = {'foo' : 'bar'}
        def deploy_handler(id):
            vertx.undeploy_verticle(id)
        vertx.deploy_verticle("core/deploy/child.py", conf, handler=deploy_handler)

def vertx_stop():
    EventBus.unregister_handler(handler_id)
    tu.unregister_all()
    tu.app_stopped()

tu.register_all(DeployTest())
tu.app_ready()
