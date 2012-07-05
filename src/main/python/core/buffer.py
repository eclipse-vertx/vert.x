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

""" 
This module adds the buffer support to the python vert.x platform 
"""

import org.vertx.java.core.buffer.Buffer

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"
__credits__ = "Based entirely on work by Tim Fox http://tfox.org"

class Buffer(object):
    """A Buffer represents a sequence of zero or more bytes that can be written to or read from, and which expands
    as necessary to accomodate any bytes written to it.

    Buffers are used in many places in vert.x, for example to read/write data to/from NetSocket, AsyncFile,
    WebSocket, HttpClientRequest, HttpClientResponse, HttpServerRequest, HttpServerResponse etc.

    There are two ways to write data to a Buffer: The first method involves methods that take the form set_XXX.
    These methods write data into the buffer starting at the specified position. The position does not have to be inside data that
    has already been written to the buffer; the buffer will automatically expand to encompass the position plus any data that needs
    to be written. All positions are measured in bytes and start with zero.

    The second method involves methods that take the form append-XXX; these methods append data at the end of the buffer.
    Methods exist to both set and append all primitive types, String and  other instances of Buffer.

    Data can be read from a buffer by invoking methods which take the form get_XXX. These methods take a parameter
    representing the position in the Buffer from where to read data.
    """
    def __init__(self, buffer):
        self.buffer = buffer

    def __repr__( self):
        """ String representation of buffer """
        return self.buffer.toString("UTF-8")

    def to_string(self, enc="UTF-8"):
        """ Buffer in enc encoding """
        return self.buffer.toString(enc)

    @staticmethod
    def create_from_str(str, enc="UTF-8"):
        """ Create a buffer from a string in the enc encoding """
        return Buffer(org.vertx.java.core.buffer.Buffer(str, enc))

    @staticmethod
    def create(initial_size_hint = 0):
        """ Creates a new empty buffer. initial_size_hint is a hint to the system for how much memory to initially allocate."""
        return Buffer(org.vertx.java.core.buffer.Buffer(initial_size_hint))

    def get_byte(self, pos):
        """ Get the byte at position pos in the buffer. """
        return self.buffer.getByte(pos)

    def get_fixnum(self, pos, bytes):
        """ Get the fixnum represented by a sequence of bytes starting at position pos in the buffer. """
        if bytes == 1:
          return self.buffer.getByte(pos)
        elif bytes ==  2:
          return self.buffer.getShort(pos)
        elif bytes ==  4:
          return self.buffer.getInt(pos)
        elif bytes ==  8:
          return self.buffer.getLong(pos)
        else:
          raise Exception("bytes must be 1, 2, 4, or 8")

    def get_float(self, pos, bytes):
        """ Get the float represented by a sequence of bytes starting at position pos in the buffer. """
        if bytes == 4:
          return self.buffer.getFloat(pos)
        elif bytes == 8:
          return self.buffer.getDouble(pos)
        else:
          raise Exception("bytes must be 4 or 8")

    def get_string(self, pos, end_pos, enc='UTF-8'):
        """ Return bytes from the buffer interpreted as a String """
        return self.buffer.getString(pos, end_pos, enc)

    def get_buffer(self, pos, end_pos):
        """ Return bytes in the buffer as a Buffer """
        return Buffer(self.buffer.getBuffer(pos, end_pos))

    def append_buffer(self, buff):
        """ Appends a buffer to the end of this buffer. The buffer will expand as necessary to accomodate any bytes written. """
        self.buffer.appendBuffer(buff._to_java_buffer())        
        return self

    def append_fixnum(self, num, bytes):
        """ Appends a fixnum to the end of this buffer. The buffer will expand as necessary to accomodate any bytes written. """
        if bytes == 1:
            self.buffer.appendByte(num)
        elif bytes == 2:
            self.buffer.appendShort(num)
        elif bytes ==  4:
            self.buffer.appendInt(num)
        elif bytes ==  8:
            self.buffer.appendLong(num)
        else:
          raise Exception("bytes must be 1, 2, 4, or 8")
        return self

    def append_float(self, num, bytes):
        """ Appends a float to the end of this buffer. The buffer will expand as necessary to accomodate any bytes written. """
        if bytes == 4:
          self.buffer.appendFloat(num)
        elif bytes == 8:
          self.buffer.appendDouble(num)
        else:
          raise Exception("bytes must be 4 or 8")
      
    def append_str(self, str, enc="UTF-8"):
        """ Appends a string to the end of this buffer. The buffer will expand as necessary to accomodate any bytes written. """
        self.buffer.appendString(str, enc)
        return self

    def set_fixnum(self, pos, num, bytes):
        """ Sets bytes in the buffer to a representation of a fixnum. The buffer will expand as necessary to accomodate any bytes written. """
        if bytes == 1:
            self.buffer.setByte(pos, num)
        elif bytes == 2:
            self.buffer.setShort(pos, num)
        elif bytes == 4:
            self.buffer.setInt(pos, num)
        elif bytes == 8:
            self.buffer.setLong(pos, num)
        else:
          raise Exception("bytes must be 1, 2, 4, or 8")
        return self

    def set_float(self, pos, num, bytes):
        """ Sets bytes in the buffer to a representation of a float. The buffer will expand as necessary to accomodate any bytes written. """
        if bytes == 4:
            self.buffer.setFloat(pos, num)
        elif bytes == 8:
            self.buffer.setDouble(pos, num)
        else:
            raise Exception("bytes must be 4 or 8")
        return self

    def set_buffer(self, pos, buff):
        """ Sets bytes in this buffer to the bytes of the specified buffer. The buffer will expand as necessary to accomodate any bytes written. """
        self.buffer.setBytes(pos, buff._to_java_buffer)
        return self

    def set_string(self, pos, str, enc="UTF-8"):
        """ Set bytes in the buffer to the string encoding in the specified encoding """
        self.buffer.setString(pos, str, enc)
        return self

    @property
    def length(self):
        """ The length of this buffer, in bytes. """
        return self.buffer.length()

    def copy(self):
        """ Get a copy of the entire buffer. """
        return Buffer(self.buffer.copy())

    def _to_java_buffer(self):
        """ private """
        return self.buffer
