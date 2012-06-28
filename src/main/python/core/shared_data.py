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

import org.vertx.java.deploy.impl.VertxLocator
import org.vertx.java.core.buffer

from core.javautils import map_from_java
from core.buffer import Buffer

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"
__credits__ = "Based entirely on work by Tim Fox http://tfox.org"

class SharedData(object):
    """Sometimes it is desirable to share immutable data between different event loops, for example to implement a
    cache of data.

    This class allows instances of shareddata data structures to be looked up and used from different event loops.
    The data structures themselves will only allow certain data types to be stored into them. This shields the
    user from worrying about any thread safety issues might occur if mutable objects were shareddata between event loops.

    The following types can be stored in a shareddata data structure:

    String
    FixNum
    Float
    Buffer - this will be automatically copied, and the copy will be stored in the structure.
    """
    @staticmethod
    def shared_data():
        return org.vertx.java.deploy.impl.VertxLocator.vertx.sharedData()

    @staticmethod
    def get_hash(key):
        """Return a Hash with the specific name. All invocations of this method with the same value of name
        are guaranteed to return the same Hash instance.

        Keyword arguments:
        @param key: Get the hash with the key.
        
        @return: the hash.
        """
        map = SharedData.shared_data().getMap(key)
        return SharedHash(map)

    @staticmethod
    def get_set(key):
        """Return a Set with the specific name. All invocations of this method with the same value of name
        are guaranteed to return the same Set instance.

        Keyword arguments:
        @param key: Get the set with the key.
        
        @return: the shared set.
        """
        set_ = SharedData.shared_data().getSet(key)
        return SharedSet(set_)

    @staticmethod
    def remove_hash(key):
        """Remove the hash

        Keyword arguments:
        @param key: The key of the hash.
        """
        return SharedData.shared_data().removeMap(key)

    @staticmethod
    def remove_set(key):
        """Remove the set

        Keyword arguments:
        @param key: The key of the set.
        """
        return SharedData.shared_data().removeSet(key)

    @staticmethod
    def check_obj(obj):
        """Convert to corresponding Java objects
        and make copies where appropriate (the underlying java map will also make copies for some data types too)
        """
        if isinstance(obj, Buffer):
            obj = obj._to_java_buffer()
        return obj


class SharedHash(object):

    def __init__(self, hash):
      self.java_obj = hash

    def __setitem__(self, key, val):
        key = SharedData.check_obj(key)
        val = SharedData.check_obj(val)
        self.java_obj.put(key, val)
    
    def __getitem__(self, key):
        obj = self.java_obj.get(key)
        if isinstance(obj, org.vertx.java.core.buffer.Buffer):
            obj = Buffer(obj)
        return obj
    
    def __eq__(self, other):        
        if isinstance(other, SharedHash):
            return self.java_obj.equals(other._to_java_map())
        else:
            return False
    
    def __str__(self):
        return map_from_java(self.java_obj).__str__()

    def keys(self):
        return map_from_java(self.java_obj).keys()

    def iteritems(self):
        return map_from_java(self.java_obj).iteritems()

    def items(self):
        return map_from_java(self.java_obj).items()

    def _to_java_map(self):
        return self.java_obj

class SharedSet(object):

    def __init__(self, java_set):
        self.java_obj = java_set

    def __eq__(self, other):
        if isinstance(other, SharedSet):
            return self.java_obj.hashCode() == other._to_java_set().hashCode()
        else:
            return False

    def __len__(self):
        return self.size()

    def __str__(self):
        return map_from_java(self.java_obj).__str__()

    def __iter__(self):
        return map_from_java(self.java_obj).__iter__()

    def add(self, obj):
        """ Add an object to the set
        
        Keyword arguments:
        @param obj: The object to add
        @return: self
        """
        obj = SharedData.check_obj(obj)
        self.java_obj.add(obj)
        return self

    def clear(self):
        """Clear the set"""
        self.java_obj.clear()

    def delete(self, obj):
        """Delete an object from the set

        Keyword arguments:
        @param obj: the object to delete
        """
        self.java_obj.remove(obj)


    def each(self, func):
        """Call the func for every element of the set

        Keyword arguments:
        @param func: The function to call.
        """
        iter = self.java_obj.iterator()
        while iter.hasNext():
            obj = iter.next()
            if isinstance(obj, org.vertx.java.core.buffer.Buffer):
                obj = Buffer(obj)
            func(obj)

    def empty(self):
        """returns True if the set is empty"""
        return self.java_obj.isEmpty()

    def include(self, obj):
        """Does the set contain an element?
    
        Keyword arguments:
        @param obj: the object to check if the set contains
        
        @return: True if the object is contained in the set
        """
        if isinstance(obj, Buffer):
            obj = obj._to_java_buffer()
        return self.java_obj.contains(obj)

    def size(self):
        """returns the number of elements in the set"""
        return self.java_obj.size()

    def _to_java_set(self):
        return self.java_obj
