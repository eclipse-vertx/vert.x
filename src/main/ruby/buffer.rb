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

  def Buffer.new_fixed(size)
    Buffer.new(org.nodex.core.buffer.Buffer.newFixed(size))
  end

  def Buffer.new_dynamic(size)
    Buffer.new(org.nodex.core.buffer.Buffer.newDynamic(size))
  end

  def Buffer.from_str(str, enc = "UTF-8")
    Buffer.new(org.nodex.core.buffer.Buffer.fromString(str, enc))
  end

  def write(str, offset = 0, enc = "UTF-8")
    @buffer.write(str, offset, enc)
  end

  def byte_at(pos)
    @buffer.byteAt(pos)
  end

  def to_s(enc = "UTF-8")
    @buffer.toString(enc)
  end

  def _to_java_buffer
    @buffer
  end

end
