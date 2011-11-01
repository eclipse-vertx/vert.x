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
include Utils

class CompositionTest < Test::Unit::TestCase

  def test_sync_blocks
    comp = Composer.new

    latch = Latch.new(3)

    comp.series{
      latch.countdown
    }

    comp.series{
      latch.countdown
    }

    comp.series{
      latch.countdown
    }

    comp.execute

  end

  def test_with_futures
    comp = Composer.new

    b1_executed = b2_executed = b3_executed = false

    f1 = SimpleFuture.new
    b1 = Proc.new{
      b1_executed = true
      f1
    }

    f2 = SimpleFuture.new
    b2 = Proc.new{
      b2_executed = true
      f2
    }

    f3 = SimpleFuture.new
    b3 = Proc.new{
      b3_executed = true
      f3
    }

    comp.series(b1)
    comp.series(b2)
    comp.series(b3)

    comp.execute

    assert(b1_executed)
    assert(!b2_executed)
    assert(!b3_executed)

    assert(!f1.complete?)
    assert(!f2.complete?)
    assert(!f3.complete?)

    f1.result = nil

    assert(b1_executed)
    assert(b2_executed)
    assert(!b3_executed)

    assert(f1.complete?)
    assert(!f2.complete?)
    assert(!f3.complete?)

    f2.result = nil

    assert(b1_executed)
    assert(b2_executed)
    assert(b3_executed)

    assert(f1.complete?)
    assert(f2.complete?)
    assert(!f3.complete?)

    f3.result = nil

    assert(b1_executed)
    assert(b2_executed)
    assert(b3_executed)

    assert(f1.complete?)
    assert(f2.complete?)
    assert(f3.complete?)

  end


end