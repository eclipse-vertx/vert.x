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

def test_isolated_global_init1_1
  $test_global = "foo"
  @tu.test_complete
end

# Globals should not be visible between different verticle types
def test_isolated_global1
  @tu.azzert($test_global === "foo")
  @tu.test_complete
end

def test_isolated_global_init1_2
  $test_global = 1
  @tu.test_complete
end

@called = false

def test_isolated_global_init2_1
  $test_global = "foo"
  @called = true
  @tu.test_complete
end

def test_isolated_global_init2_2
  if !@called
    $test_global = "bar"
    @tu.test_complete
  end
end

# Globals should be visible between verticles of same type
def test_isolated_global2
  if !@called
    @tu.azzert($test_global == "bar")
    @tu.test_complete
  end
end



def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready

