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

require 'core/shared_data'

module Vertx

  # A Buffer represents a sequence of zero or more bytes that can be written to or read from, and which expands
  # as necessary to accomodate any bytes written to it.
  #
  # Buffers are used in many places in vert.x, for example to read/write data to/from {NetSocket}, {AsyncFile},
  # {WebSocket}, {HttpClientRequest}, {HttpClientResponse}, {HttpServerRequest}, {HttpServerResponse} etc.
  #
  # There are two ways to write data to a Buffer: The first method involves methods that take the form set_XXX.
  # These methods write data into the buffer starting at the specified position. The position does not have to be inside data that
  # has already been written to the buffer; the buffer will automatically expand to encompass the position plus any data that needs
  # to be written. All positions are measured in bytes and start with zero.
  #
  # The second method involves methods that take the form append-XXX; these methods append data at the end of the buffer.
  # Methods exist to both set and append all primitive types, String and  other instances of Buffer.
  #
  # Data can be read from a buffer by invoking methods which take the form get_XXX. These methods take a parameter
  # representing the position in the Buffer from where to read data.
  #
  # @author {http://tfox.org Tim Fox}
  class Buffer

    # @private
    def initialize(j_buffer)
      @buffer = j_buffer
    end

    # Creates a new empty buffer. The {#length} of the buffer immediately after creation will be zero.
    # @param initial_size_hint [FixNum] is a hint to the system for how much memory
    # to initially allocate to the buffer to prevent excessive automatic re-allocations as data is written to it.
    def Buffer.create(initial_size_hint = 0)
      Buffer.new(org.vertx.java.core.buffer.Buffer.new(initial_size_hint))
    end

    # Create a new Buffer from a String
    # @param str [String] The String to encode into the Buffer
    # @param enc [String] Encoding to use. Defaults to "UTF-8"
    def Buffer.create_from_str(str, enc = "UTF-8")
      Buffer.new(org.vertx.java.core.buffer.Buffer.new(str, enc))
    end

    # Return a String representation of the buffer.
    # @param enc [String] The encoding to use. Defaults to "UTF-8"
    # @return [String] a String representation of the buffer.
    def to_s(enc = "UTF-8")
      @buffer.toString(enc)
    end

    # Get the byte at position pos in the buffer.
    # @param pos [FixNum] the position in the buffer from where to retrieve the byte
    # @return [FixNum] the byte
    def get_byte(pos)
      @buffer.getByte(pos)
    end

    # Get the fixnum represented by a sequence of bytes starting at position pos in the buffer.
    # @param pos [FixNum] the position in the buffer from where to retrieve the FixNum.
    # @param bytes [FixNum] the number of bytes to retrieve from position pos to create the FixNum. Valid values are 1, 2, 4 and 8.
    # @return [FixNum] the FixNum
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

    # Get the float represented by a sequence of bytes starting at position pos in the buffer.
    # @param pos [Float] the position in the buffer from where to retrieve the Float.
    # @param bytes [Float] the number of bytes to retrieve from position pos to create the Float. Valid values are 4 and 8.
    # @return [Float] the Float
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

    # Return bytes from the buffer interpreted as a String
    # @param pos [FixNum] the position in the buffer from where to start reading
    # @param end_pos [FixNum] the position in the buffer to end reading
    # @param enc [String] The encoding to use
    # @return [String] the String
    def get_string(pos, end_pos, enc = 'UTF-8')
      @buffer.getString(pos, end_pos, enc)
    end

    # Return bytes in the buffer as a Buffer
    # @param start_pos [FixNum] - the position in this buffer from where to start the copy.
    # @param end_pos [FixNum] - the copy will be made up to index end_pos - 1
    # @return [Buffer] the copy
    def get_buffer(pos, end_pos)
      j_buff = @buffer.getBuffer(pos, end_pos)
      Buffer.new(j_buff)
    end

    # Appends a buffer to the end of this buffer. The buffer will expand as necessary to accomodate any bytes written.
    # @param buff [Buffer] the buffer to append.
    # @return [Buffer] a reference to self so multiple operations can be appended together.
    def append_buffer(buff)
      @buffer.appendBuffer(buff._to_java_buffer)
      self
    end

    # Appends a fixnum to the end of this buffer. The buffer will expand as necessary to accomodate any bytes written.
    # @param num [FixNum] the fixnum to append.
    # @param bytes [FixNum] the number of bytes to write in the buffer to represent the fixnum. Valid values are 1, 2, 4 and 8.
    # @return [Buffer] a reference to self so multiple operations can be appended together.
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
      self
    end

    # Appends a float to the end of this buffer. The buffer will expand as necessary to accomodate any bytes written.
    # @param num [Float] the float to append.
    # @param bytes [FixNum] the number of bytes to write in the buffer to represent the float. Valid values are 4 and 8.
    # @return [Buffer] a reference to self so multiple operations can be appended together.
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

    # Appends a string to the end of this buffer. The buffer will expand as necessary to accomodate any bytes written.
    # @param str [String] the string to append.
    # @param enc [String] the encoding to use. Defaults to "UTF-8"
    # @return [Buffer] a reference to self so multiple operations can be appended together.
    def append_str(str, enc = "UTF-8")
      @buffer.appendString(str, enc)
      self
    end

    # Sets bytes in the buffer to a representation of a fixnum. The buffer will expand as necessary to accomodate any bytes written.
    # @param pos [FixNum] - the position in the buffer from where to start writing the fixnum
    # @param num [FixNum]  - the fixnum to write
    # @param bytes [FixNum] - the number of bytes to write to represent the fixnum. Valid values are 1, 2, 4, and 8
    # @return [Buffer] a reference to self so multiple operations can be appended together.
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
      self
    end

    # Sets bytes in the buffer to a representation of a float. The buffer will expand as necessary to accomodate any bytes written.
    # @param pos [FixNum] - the position in the buffer from where to start writing the float
    # @param num [Float]  - the float to write
    # @param bytes [FixNum] - the number of bytes to write to represent the float. Valid values are 4 and 8
    # @return [Buffer] a reference to self so multiple operations can be appended together.
    def set_float(pos, num, bytes)
      case bytes
        when 4
          @buffer.setFloat(pos, num)
        when 8
          @buffer.setDouble(pos, num)
        else
          raise "bytes must be 4 or 8"
      end
      self
    end

    # Sets bytes in this buffer to the bytes of the specified buffer. The buffer will expand as necessary to accomodate any bytes written.
    # @param pos [FixNum] - the position in this buffer from where to start writing the buffer
    # @param buff [Buffer] - the buffer to write into this buffer
    # @return [Buffer] a reference to self so multiple operations can be appended together.
    def set_buffer(pos, buff)
      @buffer.setBytes(pos, buff._to_java_buffer)
      self
    end

    # Set bytes in the buffer to the string encoding in the specified encoding
    # @param pos [FixNum] - the position in this buffer from where to start writing the string
    # @param str [String] the string
    # @param enc [String] the encoding
    # @return [Buffer] a reference to self so multiple operations can be appended together.
    def set_string(pos, str, enc = 'UTF-8')
      @buffer.setString(pos, str, enc)
      self
    end

    # @return [FixNum] the length of this buffer, in bytes.
    def length
      @buffer.length
    end

    # Get a copy of the entire buffer.
    # @return [Buffer] the copy
    def copy
      Buffer.new(@buffer.copy())
    end

    # @private
    def _to_java_buffer
      @buffer
    end

  end
end
