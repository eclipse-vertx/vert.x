# Copyright 2002-2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use
# this file except in compliance with the License. You may obtain a copy of the
# License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

require 'test/unit'
require 'core/nodex'
require 'core/shared_data'
require 'utils'

class ActorTest < Test::Unit::TestCase

  def test_actor

    latch1 = Utils::Latch.new(2)

    latch2 = Utils::Latch.new(1)

    key1 = "actor1"
    key2 = "actor2"

    shared_hash = SharedData::get_hash("foo")

    msg1 = "hello from outer"
    msg2 = "hello from actor1"

    Nodex::go{
      id1 = Nodex::register_actor{ |msg|
        assert(msg1 == msg)
        id2 = shared_hash[key2]
        Nodex::send_message(id2, msg2)
      }
      shared_hash[key1] = id1
      latch1.countdown
    }

    Nodex::go{
      id2 = Nodex::register_actor{ |msg|
        assert(msg2 == msg)
        latch2.countdown
      }
      shared_hash[key2] = id2
      latch1.countdown
    }

    assert(latch1.await(5))

    Nodex::go{
      id1 = shared_hash[key1]
      Nodex::send_message(id1, msg1)
    }

    assert(latch2.await(5))

  end
end