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
require 'set'

@tu = TestUtils.new

def test_hash

  hash1 = SharedData::get_hash("map1")
  @tu.azzert(hash1 != nil)

  hash2 = SharedData::get_hash("map1")
  @tu.azzert(hash2 != nil)

  @tu.azzert(hash1 == hash2)

  hash3 = SharedData::get_hash("map3")
  @tu.azzert(hash3 != nil)
  @tu.azzert(hash1 != hash3)

  key = 'wibble'

  hash1[key] = 'hello'

  @tu.azzert(hash1[key] == 'hello')
  @tu.azzert(hash2[key] == 'hello')
  @tu.azzert(hash1[key].is_a? String) # Make sure it's not a Java String

  hash1[key] = 12
  @tu.azzert(hash1[key] == 12)
  @tu.azzert(hash2[key] == 12)

  hash1[key] = 1.2344
  @tu.azzert(hash1[key] == 1.2344)
  @tu.azzert(hash2[key] == 1.2344)

  hash1[key] = true
  @tu.azzert(hash1[key] == true)
  @tu.azzert(hash2[key] == true)

  hash1[key] = false
  @tu.azzert(hash1[key] == false)
  @tu.azzert(hash2[key] == false)

  succeeded = false
  begin
    hash1[key] = SomeOtherClass.new
    succeeded = true
  rescue Exception => e
    # OK
  end
  @tu.azzert(!succeeded, 'Should throw exception')

  # Make sure it deals with Ruby buffers ok, and copies them
  buff1 = TestUtils::gen_buffer(100)
  hash1[key] = buff1
  buff2 = hash1[key]
  @tu.azzert(buff2.is_a? Buffer)
  @tu.azzert(buff1 != buff2)
  @tu.azzert(TestUtils::buffers_equal(buff1, buff2))

  @tu.azzert(SharedData::remove_hash("map1"))
  @tu.azzert(!SharedData::remove_hash("map1"))
  @tu.azzert(SharedData::remove_hash("map3"))
  @tu.test_complete

end

def test_set
  set1 = SharedData::get_set("set1")
  @tu.azzert(set1 != nil)

  set2 = SharedData::get_set("set1")
  @tu.azzert(set2 != nil)

  @tu.azzert(set1 == set2)

  set3 = SharedData::get_set("set3")
  @tu.azzert(set3 != nil)

  @tu.azzert(set1 != set3)

  set1.add("foo")
  set1.add("bar")
  set1.add("quux")

  @tu.azzert(3 == set1.size)

  @tu.azzert(set1.include?("foo"))
  @tu.azzert(set1.include?("bar"))
  @tu.azzert(set1.include?("quux"))
  @tu.azzert(!set1.include?("wibble"))
  @tu.azzert(!set1.empty?)

  set1.delete("foo")
  @tu.azzert(2 == set1.size)
  @tu.azzert(!set1.include?("foo"))
  @tu.azzert(set1.include?("bar"))
  @tu.azzert(set1.include?("quux"))
  @tu.azzert(!set1.empty?)

  set1.clear
  @tu.azzert(0 == set1.size)
  @tu.azzert(set1.empty?)

  set1.add("foo")
  set1.add("bar")
  set1.add("quux")

  set2 = Set.new

  set1.each { |o|
    set2.add(o)
  }

  @tu.azzert(set2.include?("foo"))
  @tu.azzert(set2.include?("bar"))
  @tu.azzert(set2.include?("quux"))

  set1.clear
  set1.add(12)
  set1.each { |elem| @tu.azzert(elem == 12) }

  set1.clear
  set1.add(1.234)
  set1.each { |elem| @tu.azzert(elem == 1.234) }

  set1.clear
  set1.add("foo")
  set1.each { |elem|
    @tu.azzert(elem == "foo")
    @tu.azzert(elem.is_a? String)
  }

  set1.clear
  set1.add(true)
  set1.each { |elem| @tu.azzert(elem == true) }

  set1.clear
  set1.add(false)
  set1.each { |elem| @tu.azzert(elem == false) }

  buff = TestUtils::gen_buffer(100)
  set1.clear
  set1.add(buff)
  set1.each { |elem|
    @tu.azzert(TestUtils::buffers_equal(buff, elem))
    @tu.azzert(buff != elem)
    @tu.azzert(elem.is_a? Buffer)
  }

  set1.clear
  succeeded = false
  begin
    set1.add = SomeOtherClass.new
    succeeded = true
  rescue Exception => e
    # OK
  end
  @tu.azzert(!succeeded, 'Should throw exception')


  @tu.azzert(SharedData::remove_set("set1"))
  @tu.azzert(!SharedData::remove_set("set1"))
  @tu.azzert(SharedData::remove_set("set3"))

  @tu.test_complete
end

class SomeOtherClass
end

def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
