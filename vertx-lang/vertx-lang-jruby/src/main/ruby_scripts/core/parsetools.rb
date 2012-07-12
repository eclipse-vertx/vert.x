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

module Vertx

  # A helper class which allows you to easily parse protocols which are delimited by a sequence of bytes, or fixed
  # size records.
  #
  # Instances of this class take as input {Buffer} instances containing raw bytes, and output records.
  # For example, if I had a simple ASCII text protocol delimited by '\n' and the input was the following:
  #
  #   buffer1:HELLO\nHOW ARE Y
  #   buffer2:OU?\nI AM
  #   buffer3: DOING OK
  #   buffer4:\n
  #
  # Then the output would be:
  #
  #   buffer1:HELLO
  #   buffer2:HOW ARE YOU?
  #   buffer3:I AM DOING OK
  #
  # Instances of this class can be changed between delimited mode and fixed size record mode on the fly as
  # individual records are read, this allows you to parse protocols where, for example, the first 5 records might
  # all be fixed size (of potentially different sizes), followed by some delimited records, followed by more fixed
  # size records.
  #
  # Instances of this class can't currently be used for protocols where the text is encoded with something other than
  # a 1-1 byte-char mapping. TODO extend this class to cope with arbitrary character encodings.
  #
  # @author {http://tfox.org Tim Fox}
  class RecordParser

    # @private
    def initialize(java_parser)
      @java_parser = java_parser
    end

    # @private
    def call(data)
      input(data)
    end

    # This method is called to provide the parser with data.
    # @param [Buffer] data. Input data to the parser.
    def input(data)
      @java_parser.handle(data._to_java_buffer)
    end

    # Create a new RecordParser instance, initially in delimited mode, and where the delimiter can be represented
    # by a delimiter string endcoded in latin-1 . Don't use this if your String contains other than latin-1 characters.
    # @param [String] delim. The delimiter string.
    # @param [Proc] proc A proc to be used as the output handler
    # @param [Block] output_block A block to be used as the output handler
    # @return [RecordParser] a new RecordParser
    def RecordParser.new_delimited(delim, proc = nil, &output_block)
      output_block = proc if proc
      RecordParser.new(org.vertx.java.core.parsetools.RecordParser.newDelimited(delim, output_block))
    end

    # Create a new RecordParser instance, initially in fixed size mode.
    # @param [FixNum] size. The initial record size.
    # @param [Proc] proc A proc to be used as the output handler
    # @param [Block] output_block A block to be used as the output handler
    # @return [RecordParser] a new RecordParser
    def RecordParser.new_fixed(size, proc = nil, &output_block)
      output_block = proc if proc
      RecordParser.new(org.vertx.java.core.parsetools.RecordParser.newFixed(size, output_block))
    end

    # Flip the parser into delimited mode. This method can be called multiple times with different values
    # of delim while data is being parsed.
    # @param [String] delim. The delimiter string.
    def delimited_mode(delim)
      @java_parser.delimitedMode(delim)
    end

    # Flip the parser into fixed size mode. This method can be called multiple times with different values
    # of size while data is being parsed.
    # @param [FixNum] size. The record size.
    def fixed_size_mode(size)
      @java_parser.fixedSizeMode(size)
    end

  end

end