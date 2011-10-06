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

module Nodex

  # Provides asynchronous stream wrappers around STDOUT, STDIN and STDERR
  #
  # These wrappers should be used if you want to use STDOUT, STDIN and STDERR in a non blocking way from inside
  # an event loop.
  #
  # @author {http://tfox.org Tim Fox}
  class StdIO

    # @return [ReadStream] a {ReadStream} wrapped around STDIN
    def StdIO.create_input_stream
      StdIOReadStream.new(org.nodex.java.core.stdio.Stdio.create_input_stream)
    end

    # @return [WriteStream] a {WriteStream} wrapped around STDOUT
    def StdIO.create_output_stream
      StdIOWriteStream.new(org.nodex.java.core.stdio.Stdio.create_output_stream)
    end

    # @return [WriteStream] a {WriteStream} wrapped around STDERR
    def StdIO.create_error_stream
      StdIOWriteStream.new(org.nodex.java.core.stdio.Stdio.create_error_stream)
    end

    # @private
    class StdIOWriteStream
      include WriteStream
      def initialize(j_ws)
        @j_del = j_ws
      end
    end

    # @private
    class StdIOReadStream
      include ReadStream
      def initialize(j_rs)
        @j_del = j_rs
      end
    end

  end

end
