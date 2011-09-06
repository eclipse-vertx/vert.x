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
require 'nodex'
require 'utils'
require 'set'
include Nodex

class SharedDataTest < Test::Unit::TestCase

  def test_hash

    hash1 = SharedData::get_hash("map1")
    assert(hash1 != nil)

    hash2 = SharedData::get_hash("map1")
    assert(hash2 != nil)

    assert(hash1 == hash2)

    hash3 = SharedData::get_hash("map3")
    assert(hash3 != nil)
    assert(hash1 != hash3)

    key = 'wibble'

    hash1[key] = 'hello'
    assert(hash1[key] = 'hello')
    assert(hash2[key] = 'hello')

    hash1[key] = 12
    assert(hash1[key] = 12)
    assert(hash2[key] = 12)

    hash1[key] = 1.2344
    assert(hash1[key] = 1.2344)
    assert(hash2[key] = 1.2344)

    hash1[key] = true
    assert(hash1[key] = true)
    assert(hash2[key] = 1.2344)

    hash1[key] = ImmutableClass.new

    succeeded = false
    begin
      hash1[key] = SomeOtherClass.new
      succeeded = true
    rescue Exception => e
      # OK
    end
    assert(!succeeded, 'Should throw exception')

    # Make sure it deals with Ruby buffers ok, and copies them
    buff1 = Buffer.create(0)
    hash1[key] = buff1
    buff2 = hash1[key]
    assert(buff1 != buff2)
    assert(Utils::buffers_equal(buff1, buff2))

    assert(SharedData::remove_hash("map1"))
    assert(!SharedData::remove_hash("map1"))
    assert(SharedData::remove_hash("map3"))

  end

  def test_set
    set1 = SharedData::get_set("set1")
    assert(set1 != nil)

    set2 = SharedData::get_set("set1")
    assert(set2 != nil)

    assert(set1 == set2)

    set3 = SharedData::get_set("set3")
    assert(set3 != nil)

    assert(set1 != set3)

    set1.add("foo")
    set1.add("bar")
    set1.add("quux")

    assert(3 == set1.size)

    assert(set1.include?("foo"))
    assert(set1.include?("bar"))
    assert(set1.include?("quux"))
    assert(!set1.include?("wibble"))
    assert(!set1.empty?)

    set1.delete("foo")
    assert(2 == set1.size)
    assert(!set1.include?("foo"))
    assert(set1.include?("bar"))
    assert(set1.include?("quux"))
    assert(!set1.empty?)

    set1.clear
    assert(0 == set1.size)
    assert(set1.empty?)

    set1.add("foo")
    set1.add("bar")
    set1.add("quux")

    set2 = Set.new

    set1.each { |o|
      set2.add(o)
    }

    assert(set2.include?("foo"))
    assert(set2.include?("bar"))
    assert(set2.include?("quux"))

    assert(SharedData::remove_set("set1"))
    assert(!SharedData::remove_set("set1"))
    assert(SharedData::remove_set("set3"))

  end

  def test_counter
    counter1 = SharedData::get_counter("c1")
    assert(counter1 != nil)

    counter2 = SharedData::get_counter("c1")
    assert(counter2 != nil)

    assert(counter1 == counter2)

    counter3 = SharedData::get_counter("c3")
    assert(counter3 != nil)

    assert(counter1 != counter3)

    assert(SharedData::remove_counter("c1"))
    assert(!SharedData::remove_counter("c1"))
    assert(SharedData::remove_counter("c3"))

    # TODO complete test

  end

  class ImmutableClass
    include SharedData::Immutable
  end

  class SomeOtherClass
  end

end