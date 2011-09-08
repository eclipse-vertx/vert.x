# Copyright 2011 VMware, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'core/shared_data'

module Nodex

  class Buffer

    # We include this so we can put a buffer in a shared data structure.
    # This class isn't actually immutable but it doesn't matter since it will get copied automatically
    # as it is added to the map
    include SharedData::Immutable

    def initialize(j_buffer)
      @buffer = j_buffer
    end

    def Buffer.create(size)
      Buffer.new(org.nodex.java.core.buffer.Buffer.create(size))
    end

    def Buffer.create_from_str(str, enc = "UTF-8")
      Buffer.new(org.nodex.java.core.buffer.Buffer.create(str, enc))
    end

    def to_s(enc = "UTF-8")
      @buffer.toString(enc)
    end

    def get_byte(pos)
      @buffer.getByte(pos)
    end

    def get_fixnum(pos, bytes)
      case bytes
        when 1
          @buffer.getByte(pos)
        when 2
          @buffer.getShort(pos)
        when 4
          @buffer.getInt(pos)
        when 8
          @buffer.getLong(pos)
        else
          raise "bytes must be 1, 2, 4, or 8"
      end
    end

    def get_float(pos, bytes)
      case bytes
        when 4
          @buffer.getFloat(pos)
        when 8
          @buffer.getDouble(pos)
        else
          raise "bytes must be 4 or 8"
      end
    end

    def append_buffer(buff)
      @buffer.appendBuffer(buff._to_java_buffer)
    end

    def append_fixnum(num, bytes)
      case bytes
        when 1
          @buffer.appendByte(num)
        when 2
          @buffer.appendShort(num)
        when 4
          @buffer.appendInt(num)
        when 8
          @buffer.appendLong(num)
        else
          raise "bytes must be 1, 2, 4, or 8"
      end
    end

    def append_float(num, bytes)
      case bytes
        when 4
          @buffer.appendFloat(num)
        when 8
          @buffer.appendDouble(num)
        else
          raise "bytes must be 4 or 8"
      end
    end

    def append_str(str, enc = "UTF-8")
      @buffer.appendString(str, enc)
    end

    def set_fixnum(pos, num, bytes)
      case bytes
        when 1
          @buffer.setByte(pos, num)
        when 2
          @buffer.setShort(pos, num)
        when 4
          @buffer.setInt(pos, num)
        when 8
          @buffer.setLong(pos, num)
        else
          raise "bytes must be 1, 2, 4, or 8"
      end
    end

    def set_float(pos, num, bytes)
      case bytes
        when 4
          @buffer.setFloat(pos, num)
        when 8
          @buffer.setDouble(pos, num)
        else
          raise "bytes must be 4 or 8"
      end
    end

    def set_bytes(pos, buff)
      @buffer.setBytes(pos, buff._to_java_buffer)
    end

    def length
      @buffer.length
    end

    def copy_part(start_pos, end_pos)
      Buffer.new(@buffer.copy(start_pos, end_pos))
    end

    def copy()
      Buffer.new(@buffer.copy())
    end

    def _to_java_buffer
      @buffer
    end

    private :initialize

  end
end
