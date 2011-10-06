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

require 'delegate'

module Nodex

  # A mixin module which marks a class as immutable and therefore allows it to be stored in any shared data structure.
  # Use this at your own risk. You need to make sure your class really is
  # immutable before you mark it.
  # @author {http://tfox.org Tim Fox}
  module Immutable
    include org.nodex.java.core.Immutable
  end

  # Sometimes it is desirable to share immutable data between different event loops, for example to implement a
  # cache of data.
  #
  # This class allows instances of shared data structures to be looked up and used from different event loops.
  # The data structures themselves will only allow certain data types to be stored into them. This shields the
  # user from worrying about any thread safety issues might occur if mutable objects were shared between event loops.
  #
  # The following types can be stored in a shared data structure:
  #
  #   String
  #   FixNum
  #   Float
  #   {Buffer} this will be automatically copied, and the copy will be stored in the structure.
  #   {Immutable}
  #
  # @author {http://tfox.org Tim Fox}
  class SharedData

    # Return a Hash with the specific name. All invocations of this method with the same value of name
    # are guaranteed to return the same Hash instance.
    # The Hash instance returned is a lock free Hash which supports a very high degree of concurrency.
    # @param [String] key. Get the hash with the key.
    # @return [Hash] the hash.
    def SharedData.get_hash(key)
      map = org.nodex.java.core.shared.SharedData.getMap(key)
      SharedHash.new(map)
    end

    # Return a Set with the specific name. All invocations of this method with the same value of name
    # are guaranteed to return the same Set instance.
    # The Set instance returned is a lock free Set which supports a very high degree of concurrency.
    # @param [String] key. Get the set with the key.
    # @return [SharedSet] the set.
    def SharedData.get_set(key)
      set = org.nodex.java.core.shared.SharedData.getSet(key)
      SharedSet.new(set)
    end

#    def SharedData.get_counter(key)
#      org.nodex.java.core.shared.SharedData.getCounter(key)
#    end

    # Remove the hash
    # @param [String] key. The key of the hash.
    def SharedData.remove_hash(key)
      org.nodex.java.core.shared.SharedData.removeMap(key)
    end

    # Remove the set
    # @param [String] key. The key of the set.
    def SharedData.remove_set(key)
      org.nodex.java.core.shared.SharedData.removeSet(key)
    end

#    def SharedData.remove_counter(key)
#      org.nodex.java.core.shared.SharedData.removeCounter(key)
#    end

    # We need to copy certain objects because they're not immutable
    def SharedData.check_copy(obj)
      if obj.is_a?(Buffer)
        obj = obj.copy
      elsif obj.is_a?(String)
        obj = String.new(obj)
      end
      obj
    end

    # @private
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
        # This will be fixed in JRuby 1.6.5
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


    end

    # A highly concurrent set
    #
    # @author {http://tfox.org Tim Fox}
    class SharedSet

      # @private
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

      # @private
      def _to_java_set
        @j_set
      end

      # Add an object to the set
      # @param [Object] obj. The object to add
      # @return [SharedSet} self
      def add(obj)
        obj = SharedData.check_copy(obj)
        @j_set.add(obj)
        self
      end

      # Add an object to the set
      # @param [Object] obj. The object to add
      # @return [SharedSet] self if the object is not already in the set, otherwise nil
      def add?(obj)
        obj = SharedData.check_copy(obj)
        if !@j_set.contains(obj)
          @j_set.add(obj)
          self
        else
          nil
        end
      end

      # Clear the set
      def clear
        @j_set.clear
      end

      # Delete an object from the set
      # @param [Object] obj. The object to delete
      def delete(obj)
        @j_set.remove(obj)
      end

      # Delete an object from the set
      # @param [Object] obj. The object to delete
      # @return [SharedSet] self if the object was in the set before the remove, nil otherwise.
      def delete?(obj)
        if @j_set.contains(obj)
          @j_set.remove(obj)
          self
        else
          nil
        end
      end

      # Call the block for every element of the set
      # @param [Blovk] block. The block to call.
      def each(&block)
        iter = @j_set.iterator
        while iter.hasNext do
          block.call(iter.next)
        end
      end

      # @return [Boolean] true if the set is empty
      def empty?
        @j_set.isEmpty
      end

      # Does the set contain an element?
      # @param [Object] obj, the object to check if the set contains
      # @return [Boolean] true if the object is contained in the set
      def include?(obj)
        @j_set.contains(obj)
      end

      # @return [FixNum] The number of elements in the set
      def size
        @j_set.size
      end

    end
  end
end