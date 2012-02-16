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
@tu.check_context

KEY1 = Buffer.create("key1")
KEY2 = Buffer.create("key2")
KEY3 = Buffer.create("key3")

VAL1 = Buffer.create("val1")
VAL2 = Buffer.create("val2")
VAL3 = Buffer.create("val3")

@pool = RedisPool.new
@conn = @pool.connection

def flush
  @conn.flush_db.handler{
    yield
  }
end

def test_method_with_buffer_arg

  comp = Composer.new
  comp.series{@conn.set(KEY1, VAL1)}
  future1 = comp.series{@conn.get(KEY1)}
  comp.series{@tu.azzert(TestUtils::buffers_equal(VAL1, future1.result)) }
  comp.series{@tu.test_complete}
  comp.execute

end

def test_method_with_buffer_array_arg

  comp = Composer.new
  comp.series{@conn.r_push(KEY2, VAL1, VAL2, VAL3)}
  future1 = comp.series{@conn.r_pop(KEY2)}
  future2 = comp.parallel{@conn.r_pop(KEY2)}
  future3 = comp.parallel{@conn.r_pop(KEY2)}
  comp.series{
    @tu.azzert(TestUtils::buffers_equal(VAL3, future1.result))
    @tu.azzert(TestUtils::buffers_equal(VAL2, future2.result))
    @tu.azzert(TestUtils::buffers_equal(VAL1, future3.result))
  }
  comp.series{@tu.test_complete}
  comp.execute

end

def test_method_with_buffer_array_ret

  comp = Composer.new
  comp.series{@conn.r_push(KEY3, VAL1, VAL2, VAL3)}
  future = comp.series{@conn.l_range(KEY3, 0, 2)}
  comp.series{
    azzert_buff_arrays_equals([VAL1, VAL2, VAL3], future.result)
  }
  comp.series{@tu.test_complete}
  comp.execute

end

def azzert_buff_arrays_equals(expected, actual)
  @tu.azzert expected.size == actual.size
  for i in 0...expected.size - 1
    @tu.azzert(TestUtils::buffers_equal(expected[i], actual[i]))
  end
end

def vertx_stop
  @tu.check_context
  flush do
    @conn.close.handler {
      @pool.close
      @tu.unregister_all
      @tu.app_stopped
    }
  end
end

@tu.register_all(self)
flush do
  @tu.app_ready  
end

