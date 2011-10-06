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
require 'nodex'
require 'utils'
include Nodex

class CompositionTest < Test::Unit::TestCase

  def test_blocks
    comp = Composer.new

    comp.series{
      puts "1 called"
    }

    comp.series{
      puts "2 called"
    }

    comp.series{
      puts "3 called"

      latch.countdown
    }

    comp.execute

  end

  def test_deferreds
    comp = Composer.new

    d1_executed = d2_executed = d3_executed = false

    d1 = DeferredAction.new{
      puts "1 called"
      d1_executed = true
    }

    d2 = DeferredAction.new{
      puts "2 called"
      d2_executed = true
    }

    d3 = DeferredAction.new{
      puts "3 called"

      d3_executed = true
    }

    comp.series(d1)
    comp.series(d2)
    comp.series(d3)

    comp.execute

    assert(d1_executed)
    assert(!d2_executed)
    assert(!d3_executed)

    assert(!d1.complete?)
    assert(!d2.complete?)
    assert(!d3.complete?)

    d1.result = nil

    assert(d1_executed)
    assert(d2_executed)
    assert(!d3_executed)

    assert(d1.complete?)
    assert(!d2.complete?)
    assert(!d3.complete?)

    d2.result = nil

    assert(d1_executed)
    assert(d2_executed)
    assert(d3_executed)

    assert(d1.complete?)
    assert(d2.complete?)
    assert(!d3.complete?)

    d3.result = nil

    assert(d1_executed)
    assert(d2_executed)
    assert(d3_executed)

    assert(d1.complete?)
    assert(d2.complete?)
    assert(d3.complete?)

  end


end