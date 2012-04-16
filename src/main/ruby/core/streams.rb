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

  # A mixin module which represents a stream of data that can be written to.
  #
  # Any class that mixes in this module can be used by a {Pump} to pump data from a {ReadStream} to it.
  #
  # @author {http://tfox.org Tim Fox}
  module WriteStream

    # Write some data to the stream. The data is put on an internal write queue, and the write actually happens
    # asynchronously. To avoid running out of memory by putting too much on the write queue,
    # check the {#write_queue_full?} method before writing. This is done automatically if using a {Pump}.
    # @param [Buffer]. The buffer to write.
    def write_buffer(buff)
      @j_del.writeBuffer(buff._to_java_buffer)
    end

    # Set the maximum size of the write queue. You will still be able to write to the stream even
    # if there is more data than this in the write queue. This is used as an indicator by classes such as
    # {Pump} to provide flow control.
    # @param [FixNum] size. The maximum size, in bytes.
    def write_queue_max_size=(size)
      @j_del.setWriteQueueMaxSize(size)
    end

    # Is the write queue full?
    # @return [Boolean] true if there are more bytes in the write queue than the max write queue size.
    def write_queue_full?
      @j_del.writeQueueFull
    end

    # Set a drain handler on the stream. If the write queue is full, then the handler will be called when the write
    # queue has been reduced to maxSize / 2. See {Pump} for an example of this being used.
    # @param [Block] hndlr. The drain handler
    def drain_handler(&hndlr)
      @j_del.drainHandler(hndlr)
    end

    # Set an execption handler on the stream.
    # @param [Block] hndlr. The exception handler
    def exception_handler(&hndlr)
      @j_del.exceptionHandler(hndlr)
    end

    # @private
    def _to_write_stream
      @j_del
    end

  end

  # A mixin module which represents a stream of data that can be read from.
  #
  # Any class that mixes in this module can be used by a {Pump} to pump data from a {ReadStream} to it.
  #
  # @author {http://tfox.org Tim Fox}
  module ReadStream

    # Set a data handler. As data is read, the handler will be called with the data.
    # @param [Block] hndlr. The data handler
    def data_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del.dataHandler(Proc.new { |j_buff|
        hndlr.call(Buffer.new(j_buff))
      })
    end

    # Pause the ReadStream. After calling this, the ReadStream will aim to send no more data to the {#data_handler}
    def pause
      @j_del.pause
    end

    # Resume reading. If the ReadStream has been paused, reading will recommence on it.
    def resume
      @j_del.resume
    end

    # Set an execption handler on the stream.
    # @param [Block] hndlr. The exception handler
    def exception_handler(&hndlr)
      @j_del.exceptionHandler(hndlr)
    end

    # Set an end handler on the stream. Once the stream has ended, and there is no more data to be read, this handler will be called.
    # @param [Block] hndlr. The exception handler
    def end_handler(&hndlr)
      @j_del.endHandler(hndlr)
    end

    # @private
    def _to_read_stream
      @j_del
    end

  end

  # Pumps data from a {ReadStream} to a {WriteStream} and performs flow control where necessary to
  # prevent the write stream from getting overloaded.
  #
  # Instances of this class read bytes from a ReadStream and write them to a WriteStream. If data
  # can be read faster than it can be written this could result in the write queue of the WriteStream growing
  # without bound, eventually causing it to exhaust all available RAM.
  # To prevent this, after each write, instances of this class check whether the write queue of the WriteStream
  # is full, and if so, the ReadStream is paused, and a {WriteStream#drain_handler} is set on the WriteStream.
  # When the WriteStream has processed half of its backlog, the drain_handler will be called,
  # which results in the pump resuming the ReadStream.
  #
  # This class can be used to pump from any {ReadStream} to any { WriteStream},
  # e.g. from an {HttpServerRequest} to an {AsyncFile}, or from {NetSocket} to a {WebSocket}.
  #
  # @author {http://tfox.org Tim Fox}
  class Pump
    def initialize(read_stream, write_stream)
      raise "read_stream is not a ReadStream" if !read_stream.is_a? ReadStream
      raise "write_stream is not a WriteStream" if !write_stream.is_a? WriteStream
      j_rs = read_stream._to_read_stream
      j_ws = write_stream._to_write_stream
      @j_pump = org.vertx.java.core.streams.Pump.createPump(j_rs, j_ws)
    end

    # Set the write queue max size
    # @param [FixNum] The write queue max size
    def write_queue_max_size=(val)
      @j_pump.setWriteQueueMaxSize(val)
    end

    # Start the Pump. The Pump can be started and stopped multiple times.
    def start
      @j_pump.start
    end

    # Stop the Pump. The Pump can be started and stopped multiple times.
    def stop
      @j_pump.stop
    end

    # @return [FixNum] Return the total number of bytes pumped by this pump.
    def bytes_pumped
      @j_pump.getBytesPumped
    end
  end
end
