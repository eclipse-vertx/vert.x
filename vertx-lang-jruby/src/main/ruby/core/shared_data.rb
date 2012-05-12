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

module Vertx

  # Sometimes it is desirable to share immutable data between different event loops, for example to implement a
  # cache of data.
  #
  # This class allows instances of shareddata data structures to be looked up and used from different event loops.
  # The data structures themselves will only allow certain data types to be stored into them. This shields the
  # user from worrying about any thread safety issues might occur if mutable objects were shareddata between event loops.
  #
  # The following types can be stored in a shareddata data structure:
  #
  #   String
  #   FixNum
  #   Float
  #   {Buffer} this will be automatically copied, and the copy will be stored in the structure.
  #
  # @author {http://tfox.org Tim Fox}
  class SharedData

    @@j_sd = org.vertx.java.deploy.impl.VertxLocator.vertx.sharedData()

    # Return a Hash with the specific name. All invocations of this method with the same value of name
    # are guaranteed to return the same Hash instance.
    # @param [String] key. Get the hash with the key.
    # @return [Hash] the hash.
    def SharedData.get_hash(key)
      map = @@j_sd.getMap(key)
      SharedHash.new(map)
    end

    # Return a Set with the specific name. All invocations of this method with the same value of name
    # are guaranteed to return the same Set instance.
    # @param [String] key. Get the set with the key.
    # @return [SharedSet] the set.
    def SharedData.get_set(key)
      set = @@j_sd.getSet(key)
      SharedSet.new(set)
    end

    # Remove the hash
    # @param [String] key. The key of the hash.
    def SharedData.remove_hash(key)
      @@j_sd.removeMap(key)
    end

    # Remove the set
    # @param [String] key. The key of the set.
    def SharedData.remove_set(key)
      @@j_sd.removeSet(key)
    end

    # Convert to corresponding Java objects
    # And make copies where appropriate (the underlying java map will also make copies for some data types too)
    # @private
    def SharedData.check_obj(obj)
      if obj.is_a?(Buffer)
        obj = obj._to_java_buffer
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
        key = SharedData.check_obj(key)
        val = SharedData.check_obj(val)
        super(key, val)
      end

      alias store []=

      def [](key)
        # We call the java class directly
        obj = @hash.get(key)
        obj = Buffer.new(obj) if obj.is_a? org.vertx.java.core.buffer.Buffer
        obj
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

    #
    # @private
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

      # Add an object to the set
      # @param [Object] obj. The object to add
      # @return [SharedSet} self
      def add(obj)
        obj = SharedData.check_obj(obj)
        @j_set.add(obj)
        self
      end

      # Add an object to the set
      # @param [Object] obj. The object to add
      # @return [SharedSet] self if the object is not already in the set, otherwise nil
      def add?(obj)
        obj = SharedData.check_obj(obj)
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
          obj = iter.next
          obj = Buffer.new(obj) if obj.is_a? org.vertx.java.core.buffer.Buffer
          block.call(obj)
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
        obj = obj._to_java_buffer if obj.is_a? Buffer
        @j_set.contains(obj)
      end

      # @return [FixNum] The number of elements in the set
      def size
        @j_set.size
      end

      # @private
      def _to_java_set
        @j_set
      end

    end
  end
end