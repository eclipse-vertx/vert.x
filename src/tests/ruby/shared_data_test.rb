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
require 'core/shared_data'
require 'utils'

class SharedDataTest < Test::Unit::TestCase

  def test_map

    map1 = SharedData::get_map("map1")
    assert(map1 != nil)

    map2 = SharedData::get_map("map2")
    assert(map2 != nil)

    key = 'wibble'

    map1[key] = 'hello'
    assert(map1[key] = 'hello')
    assert(map2[key] = 'hello')

    map1[key] = 12
    assert(map1[key] = 12)
    assert(map2[key] = 12)

    map1[key] = 1.2344
    assert(map1[key] = 1.2344)
    assert(map2[key] = 1.2344)

    map1[key] = true
    assert(map1[key] = true)
    assert(map2[key] = 1.2344)

    map1[key] = ImmutableClass.new

    succeeded = false
    begin
      map1[key] = SomeOtherClass.new
      succeeded = true
    rescue Exception => e
      # OK
    end
    assert(!succeeded, 'Should throw exception')

    # Make sure it deals with Ruby buffers ok, and copies them
    buff1 = Buffer.create(0)
    map1[key] = buff1
    buff2 = map1[key]
    assert(buff1 != buff2)
    assert(Utils::buffers_equal(buff1, buff2))


  end

  class ImmutableClass
    include SharedData::Immutable
  end

  class SomeOtherClass
  end

end