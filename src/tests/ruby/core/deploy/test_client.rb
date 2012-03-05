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

require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

@handler_id = nil

def test_deploy

  @handler_id = EventBus.register_handler("test-handler") do |message|
    @tu.test_complete if "started" == message.body
  end

  conf = {'foo' => 'bar'}

  Vertx.deploy_verticle("core/deploy/child.rb", conf)
end

def test_undeploy

  @handler_id = EventBus.register_handler("test-handler") do |message|
    @tu.test_complete if "stopped" == message.body
  end

  conf = {'foo' => 'bar'}
  id = Vertx.deploy_verticle("core/deploy/child.rb", conf) {
    Vertx.undeploy_verticle(id)
  }

end

def vertx_stop
  EventBus.unregister_handler(@handler_id)
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
