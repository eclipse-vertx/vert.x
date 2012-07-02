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

import org.vertx.java.core.parsetools.RecordParser
from core.buffer import Buffer

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"
__credits__ = "Based entirely on work by Tim Fox http://tfox.org"

class RecordParser(object):
    """A helper class which allows you to easily parse protocols which are delimited by a sequence of bytes, or fixed
    size records.

    Instances of this class take as input Buffer instances containing raw bytes, and output records.
    For example, if I had a simple ASCII text protocol delimited by '\\n' and the input was the following:

    buffer1:HELLO\\nHOW ARE Y
    buffer2:OU?\\nI AM
    buffer3: DOING OK
    buffer4:\\n

    Then the output would be:

    buffer1:HELLO
    buffer2:HOW ARE YOU?
    buffer3:I AM DOING OK

    Instances of this class can be changed between delimited mode and fixed size record mode on the fly as
    individual records are read, this allows you to parse protocols where, for example, the first 5 records might
    all be fixed size (of potentially different sizes), followed by some delimited records, followed by more fixed
    size records.

    Instances of this class can't currently be used for protocols where the text is encoded with something other than
    a 1-1 byte-char mapping. TODO extend this class to cope with arbitrary character encodings.
    """

    def __init__(self, java_parser):
        self.java_parser = java_parser
 
    def __call__(self, data):
        self.input(data)
  
    def input(self, data):
        """This method is called to provide the parser with data.
        @param data: Input buffer  to the parser.
        """
        self.java_parser.handle(data._to_java_buffer())

    @staticmethod
    def new_delimited(delim, handler):
        """Create a new RecordParser instance, initially in delimited mode, and where the delimiter can be represented
        by a delimiter string endcoded in latin-1 . Don't use this if your String contains other than latin-1 characters.

        Keyword arguments:
        @param delim: The delimiter string.
        @param handler: the output handler
        
        @return: a new RecordParser
        """        
        return RecordParser(org.vertx.java.core.parsetools.RecordParser.newDelimited(delim, RecordParserHandler(handler)))

    @staticmethod
    def new_fixed(size, handler):
        """Create a new RecordParser instance, initially in fixed size mode.

        Keyword arguments:
        @param size: the initial record size.
        @param handler: the output handler

        @return: a new RecordParser
        """        
        return RecordParser(org.vertx.java.core.parsetools.RecordParser.newFixed(size, RecordParserHandler(handler)))

    def delimited_mode(self, delim):
        """Flip the parser into delimited mode. This method can be called multiple times with different values
        of delim while data is being parsed.

        Keyword arguments:
        @param delim: the delimiter string.
        """
        self.java_parser.delimitedMode(delim)

    def fixed_size_mode(self, size):
        """Flip the parser into fixed size mode. This method can be called multiple times with different values
        of size while data is being parsed.

        Keyword arguments:
        @param size: the record size.
        """
        self.java_parser.fixedSizeMode(size)

class RecordParserHandler(org.vertx.java.core.Handler):
    """ Record Parser handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, buffer):
        """ Call the handler after buffer parsed"""
        self.handler(Buffer(buffer))
