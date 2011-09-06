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

  class Pump
    def initialize(read_stream, write_stream)
      j_rs = read_stream._to_read_stream
      j_ws = write_stream._to_write_stream
      @j_pump = org.nodex.java.core.streams.Pump.new(j_rs, j_ws)
    end

    def write_queue_max_size=(val)
      @j_pump.setWriteQueueMaxSize(val)
    end

    def start
      @j_pump.start
    end

    def stop
      @j_pump.stop
    end

    def bytes_pumped
      @j_pump.getBytesPumped
    end
  end
end
