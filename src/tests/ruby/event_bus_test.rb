# Copyright 2011 the original author or authors.
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

require 'test/unit'
require 'vertx'
require 'utils'
include Vertx

# Just some basic tests - most testing is done at the Java layer
class EventBusTest < Test::Unit::TestCase

  class TestEventBus < org.vertx.java.core.cluster.EventBus
    def initialize(server_id, cluster_manager)
      super(server_id, cluster_manager)
    end

    def close(&done)
      super{ done.call}
    end
  end

  def setup
    serverID = org.vertx.java.core.net.ServerID.new(8181, "localhost")
    latch = Utils::Latch.new(1)
    Vertx::internal_go {
      @c_id = org.vertx.java.core.Vertx.instance.getContextID
      @cm = org.vertx.java.core.cluster.spi.hazelcast.HazelcastClusterManager.new
      @eb = TestEventBus.new(serverID, @cm)
      org.vertx.java.core.cluster.EventBus.initialize(@eb)
      latch.countdown
    }
    latch.await(5)
  end

  def teardown
    latch = Utils::Latch.new(1)
    org.vertx.java.core.internal.VertxInternal.instance.executeOnContext(@c_id) {
      org.vertx.java.core.internal.VertxInternal.instance.setContextID(@c_id)
      @eb.close { latch.countdown }
      @cm.close
    }
    latch.await(5)
  end

  def test_event_bus

    latch = Utils::Latch.new(1)

    Vertx::internal_go {

      body = "hello world!"
      address = "some-address"

      id = EventBus.register_handler(address) do |msg|
        assert body == msg.body.to_s
        puts "message id is #{msg.message_id}"
        msg.acknowledge
        latch.countdown
      end

      buff = Buffer.create_from_str(body)
      msg = Message.new(address, buff)

      EventBus.send(msg) do
        # receipt handler
        EventBus.unregister_handler(id)
        latch.countdown
      end
    }

    assert(latch.await(5))
  end

end
