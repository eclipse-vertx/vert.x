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

module Nodex

  module WriteStream

    def write_buffer(buff)
      @j_del.writeBuffer(buff._to_java_buffer)
    end

    def write_queue_max_size=(size)
      @j_del.setWriteQueueMaxSize(size)
    end

    def write_queue_full?
      @j_del.writeQueueFull
    end

    def drain_handler(&hndlr)
      @j_del.drainHandler(hndlr)
    end

    def exception_handler(&hndlr)
      @j_del.exceptionHandler(hndlr)
    end

    def _to_write_stream
      @j_del
    end

  end

  module ReadStream

    def data_handler(&hndlr)
      @j_del.dataHandler(Proc.new { |j_buff|
        hndlr.call(Buffer.new(j_buff))
      })
    end

    def pause
      @j_del.pause
    end

    def resume
      @j_del.resume
    end

    def exception_handler(&hndlr)
      @j_del.exceptionHandler(hndlr)
    end

    def end_handler(&hndlr)
      @j_del.endHandler(hndlr)
    end

    def _to_read_stream
      @j_del
    end

  end

  class Pump
    def initialize(read_stream, write_stream)
      raise "read_stream is not a ReadStream" if !read_stream.is_a? ReadStream
      raise "write_stream is not a WriteStream" if !write_stream.is_a? WriteStream
      j_del = read_stream._to_read_stream
      j_del = write_stream._to_write_stream
      @j_pump = org.nodex.java.core.streams.Pump.new(j_del, j_del)
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
