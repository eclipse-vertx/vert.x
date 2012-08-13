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
#

""" 
This module defines several functions to ease interfacing with Java code.  

Initially based on resources in the following article http://www.ibm.com/developerworks/java/tutorials/j-jython2/index.html
"""

import sys
from types import *
from java import util

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"
__credits__ = "Based entirely on work by Tim Fox http://tfox.org"

def map_map_from_java (map):
    """ Convert a Map to a Dictionary. """
    result = {}
    iter = map.keySet().iterator()
    while iter.hasNext():
        key = iter.next()
        result[map_from_java(key)] = map_from_java(map.get(key))
    return result

def map_set_from_java (set_):
    """ Convert a Set to a set. """
    result = set()
    iter = set_.iterator()
    while iter.hasNext():
        result.add(map_from_java(iter.next()))
    return result

def map_collection_from_java (coll):
    """ Convert a Collection to a List. """
    result = []
    iter = coll.iterator()
    while iter.hasNext():
        result.append(map_from_java(iter.next()))
    return result

def map_from_java (object):
    """ Convert a Java type to a Jython type. """
    if object is None: return object
    if   isinstance(object, util.Map):        result = map_map_from_java(object)
    elif isinstance(object, util.Set):        result = map_set_from_java(object)
    elif isinstance(object, util.Collection): result = map_collection_from_java(object)
    else:                                     result = object
    return result

def map_seq_to_java (seq):
    """ Convert a seqence to a Java ArrayList. """
    result = util.ArrayList(len(seq))
    for e in seq:
        result.add(map_to_java(e));
    return result

def map_list_to_java (list):
    """ Convert a List to a Java ArrayList. """
    result = util.ArrayList(len(list))
    for e in list:
        result.add(map_to_java(e));
    return result

def map_list_to_java_vector (list):
    """ Convert a List to a Java Vector. """
    result = util.Vector(len(list))
    for e in list:
        result.add(map_to_java(e));
    return result

def map_dict_to_java (dict):
    """ Convert a Dictionary to a Java HashMap. """
    result = util.HashMap()
    for key, value in dict.items():
        result.put(map_to_java(key), map_to_java(value))
    return result

def map_to_java (object):
    """ Convert a Jython type to a Java type. """
    if object is None: return object
    t = type(object)
    if   t == TupleType: result = map_seq_to_java(object)
    elif t == ListType:  result = map_seq_to_java(object)
    elif t == DictType:  result = map_dict_to_java(object)
    else:                result = object
    return result
