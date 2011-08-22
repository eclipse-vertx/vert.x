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
    include org.nodex.core.Immutable

  end

  def SharedData.get_map(key)
    map = org.nodex.core.shared.SharedData.getMap(key)
    puts " map is #{map.inspect}"
    SharedMap.new(map)
  end

  def SharedData.get_set(key)
    org.nodex.core.shared.SharedData.getSet(key)
  end

  def SharedData.remove_map(key)
   org.nodex.core.shared.SharedData.removeMap(key)
  end

  def SharedData.remove_set(key)
    org.nodex.core.shared.SharedData.removeSet(key)
  end

  class SharedMap < DelegateClass(Hash)

    def initialize(hash)
      @hash = hash
      # Pass the object to be delegated to the superclass.
      super(@hash)
    end

    def []=(key, val)

      # Store as Java buffers - they will get copied in Java land

      key = key._to_java_buffer if key.is_a?(Buffer)
      val = val._to_java_buffer if val.is_a?(Buffer)

      @hash[key] = val
    end

    def [](key)
      val = @hash[key]
      val = Buffer.new(val) if val.is_a?(org.nodex.core.buffer.Buffer)
    end

  end

#  class SharedMap
#
#  end
#
#  class SharedSet
#
#  end
#
#  class SharedQueue
#
#  end
#
#  class SharedCounter
#
#  end
end