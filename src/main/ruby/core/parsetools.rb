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

include Java

module Nodex

  class RecordParser

    def initialize(java_parser)
      @java_parser = java_parser
    end

    def call(data)
      input(data)
    end

    def input(data)
      @java_parser.onEvent(data._to_java_buffer)
    end

    def RecordParser.new_delimited(delim, proc = nil, &output_block)
      output_block = proc if proc
      RecordParser.new(org.nodex.java.core.parsetools.RecordParser.newDelimited(delim, output_block))
    end

    def RecordParser.new_fixed(size, proc = nil, &output_block)
      output_block = proc if proc
      RecordParser.new(org.nodex.java.core.parsetools.RecordParser.newFixed(size, output_block))
    end

    def delimited_mode(delim)
      @java_parser.delimitedMode(delim)
    end

    def fixed_size_mode(size)
      @java_parser.fixedSizeMode(size)
    end

    private :initialize
  end

end