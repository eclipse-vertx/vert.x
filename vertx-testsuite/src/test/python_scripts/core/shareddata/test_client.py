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

from test_utils import TestUtils
from core.shared_data import SharedData
from core.buffer import Buffer

tu = TestUtils()

class SharedDataTest(object):

    def test_hash(self):
        hash1 = SharedData.get_hash("map1")
        tu.azzert(hash1 != None)
        hash2 = SharedData.get_hash("map1")
        tu.azzert(hash2 != None)

        tu.azzert(hash1 == hash2)

        hash3 = SharedData.get_hash("map3")
        tu.azzert(hash3 != None)
        tu.azzert(hash1 != hash3)

        key = 'wibble'

        hash1[key] = 'hello'

        tu.azzert(hash1[key] == 'hello')
        tu.azzert(hash2[key] == 'hello')
        tu.azzert(isinstance(hash1[key], unicode)) # Make sure it's not a Java String

        hash1[key] = 12
        tu.azzert(hash1[key] == 12)
        tu.azzert(hash2[key] == 12)

        hash1[key] = 1.2344
        tu.azzert(hash1[key] == 1.2344)
        tu.azzert(hash2[key] == 1.2344)

        hash1[key] = True
        tu.azzert(hash1[key] == True)
        tu.azzert(hash2[key] == True)

        hash1[key] = False
        tu.azzert(hash1[key] == False)
        tu.azzert(hash2[key] == False)

        succeeded = False
        try:
            hash1[key] = SomeOtherClass()
            succeeded = True
        except:
            pass # OK
          
        tu.azzert(not succeeded, 'Should throw exception')

        # Make sure it deals with Ruby buffers ok, and copies them
        buff1 = TestUtils.gen_buffer(100)
        hash1[key] = buff1
        buff2 = hash1[key]
        tu.azzert(isinstance(buff2, Buffer))
        tu.azzert(buff1 != buff2)
        tu.azzert(TestUtils.buffers_equal(buff1, buff2))

        tu.azzert(SharedData.remove_hash("map1"))
        tu.azzert(not SharedData.remove_hash("map1"))
        tu.azzert(SharedData.remove_hash("map3"))
        tu.test_complete()

    def test_set(self):
        set1 = SharedData.get_set("set1")
        tu.azzert(set1 != None)

        set2 = SharedData.get_set("set1")
        tu.azzert(set2 != None)

        tu.azzert(set1 == set2)

        set3 = SharedData.get_set("set3")
        tu.azzert(set3 != None)

        tu.azzert(set1 != set3)

        set1.add("foo")
        set1.add("bar")
        set1.add("quux")

        tu.azzert(3 == len(set1))

        tu.azzert("foo" in set1)
        tu.azzert("bar" in set1)
        tu.azzert("quux" in set1)
        tu.azzert(not ("wibble" in set1))
        tu.azzert(not set1.empty())

        set1.delete("foo")
        tu.azzert(2 == len(set1))
        tu.azzert(2 == set1.size())
        tu.azzert(not ("foo" in set1))
        tu.azzert("bar" in set1)
        tu.azzert("quux" in set1)
        tu.azzert(not set1.empty())

        set1.clear()
        tu.azzert(set1.empty())

        set1.add("foo")
        set1.add("bar")
        set1.add("quux")

        set2 = set()

        @set1.each
        def each(o):
            set2.add(o)
    
        tu.azzert("foo" in set2)
        tu.azzert("bar" in set2)
        tu.azzert("quux" in set2)

        set1.clear()
        set1.add(12)
        @set1.each
        def each2(elem):
            tu.azzert(elem == 12)

        set1.clear()
        set1.add(1.234)
        @set1.each
        def each3(elem):
            tu.azzert(elem == 1.234)

        set1.clear()
        set1.add("foo")
        @set1.each
        def each4(elem):
            tu.azzert(elem == "foo")
            tu.azzert(isinstance(elem, unicode))
        
        set1.clear()
        set1.add(True)
        @set1.each
        def each5(elem):
            tu.azzert(elem == True)

        set1.clear()
        set1.add(False)
        @set1.each
        def each6(elem):
            tu.azzert(elem == False)

        buff = TestUtils.gen_buffer(100)
        set1.clear()
        set1.add(buff)
        @set1.each
        def each7(elem):
            tu.azzert(TestUtils.buffers_equal(buff, elem))
            tu.azzert(buff != elem)
            tu.azzert(isinstance(elem, Buffer))

        set1.clear()
        succeeded = False
        try:
            set1.add(SomeOtherClass())
            succeeded = True
        except: 
            pass # OK
    
        tu.azzert(not succeeded, 'Should throw exception')

        tu.azzert(SharedData.remove_set("set1"))
        tu.azzert(not SharedData.remove_set("set1"))
        tu.azzert(SharedData.remove_set("set3"))

        tu.test_complete()

class SomeOtherClass: 
    pass

def vertx_stop():
  tu.unregister_all()
  tu.app_stopped()

tu.register_all(SharedDataTest())
tu.app_ready()
