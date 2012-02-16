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

def test_one_off
  Vertx::set_timer(10) { |timer_id|
    @tu.check_context
    @tu.test_complete
  }
end

def test_periodic

  fires = 10

  count = 0
  Vertx::set_periodic(10) { |timer_id|
    @tu.check_context
    count += 1
    if count == fires
      Vertx::cancel_timer(timer_id)
      # End test in another timer in case the first timer fires again - we want to catch that
      Vertx::set_timer(100) { |timer_id|
        @tu.test_complete
      }
    elsif count > fires
      @tu.azzert(false, 'Fired too many times')
    end
  }

end

def vertx_stop
  @tu.check_context
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
