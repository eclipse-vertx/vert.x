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

require 'delegate'
require 'core/buffer'
include Java

module SharedData

  module Immutable
    include org.nodex.java.core.Immutable
  end

  def SharedData.get_hash(key)
    map = org.nodex.java.core.shared.SharedData.getMap(key)
    SharedHash.new(map)
  end

  def SharedData.get_set(key)
    set = org.nodex.java.core.shared.SharedData.getSet(key)
    SharedSet.new(set)
  end

  def SharedData.get_counter(key)
    org.nodex.java.core.shared.SharedData.getCounter(key)
  end

  def SharedData.remove_hash(key)
    org.nodex.java.core.shared.SharedData.removeMap(key)
  end

  def SharedData.remove_set(key)
    org.nodex.java.core.shared.SharedData.removeSet(key)
  end

  def SharedData.remove_counter(key)
    org.nodex.java.core.shared.SharedData.removeCounter(key)
  end

  # We need to copy certain objects because they're not immutable
  def SharedData.check_copy(obj)
    if obj.is_a?(Buffer)
      obj = obj.copy
    elsif obj.is_a?(String)
      obj = String.new(obj)
    end
    obj
  end

  class SharedHash < DelegateClass(Hash)

    def initialize(hash)
      @hash = hash
      # Pass the object to be delegated to the superclass.
      super(@hash)
    end

    def []=(key, val)

      key = SharedData.check_copy(key)
      val = SharedData.check_copy(val)

      # We call the java class directly - otherwise RubyHash does a scan of the whole map!! :(
      @hash.put(key, val)
    end

    alias store []=

    def [](key)
      # We call the java class directly
      @hash.get(key)
    end

    def ==(other)
      if other.is_a?(SharedHash)
        @hash.equal?(other._to_java_map)
      else
        false
      end
    end

    def _to_java_map
      @hash
    end

    private :initialize
  end

  class SharedSet
    def initialize(j_set)
      @j_set = j_set
    end

    def ==(other)
      if other.is_a?(SharedSet)
        @j_set.equal?(other._to_java_set)
      else
        false
      end
    end

    def _to_java_set
      @j_set
    end

    def add(obj)
      obj = SharedData.check_copy(obj)
      @j_set.add(obj)
      self
    end

    def add?(obj)
      obj = SharedData.check_copy(obj)
      if !@j_set.contains(obj)
        @j_set.add(obj)
        self
      else
        nil
      end
    end

    def clear
      @j_set.clear
    end

    def delete(obj)
      @j_set.remove(obj)
    end

    def delete?(obj)
      if @j_set.contains(obj)
        @j_set.remove(obj)
        self
      else
        nil
      end
    end

    def each(&block)
      iter = @j_set.iterator
      while iter.hasNext do
        block.call(iter.next)
      end
    end

    def empty?
      @j_set.isEmpty
    end

    def include?(obj)
      @j_set.contains(obj)
    end

    def size
      @j_set.size
    end

    private :initialize

  end

end