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

class Buffer

  def initialize(java_buffer)
    @buffer = java_buffer
  end

  def Buffer.create(size)
    Buffer.new(org.nodex.core.buffer.Buffer.create(size))
  end

  def Buffer.create_from_str(str, enc = "UTF-8")
    Buffer.new(org.nodex.core.buffer.Buffer.create(str, enc))
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
    @buffer.append(buff._to_java_buffer)
  end

  def append_str(str, enc = "UTF-8")
    @buffer.append(str, enc)
  end

  def append_byte(byte)
    ??
  end


  def to_s(enc = "UTF-8")
    @buffer.toString(enc)
  end

  def _to_java_buffer
    @buffer
  end

  private :initialize

end
