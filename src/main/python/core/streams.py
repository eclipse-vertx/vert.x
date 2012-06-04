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

import org.vertx.java.core.streams.Pump

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"

class WriteStream(object):
    """
     A mixin module which represents a stream of data that can be written to.

     Any class that mixes in this module can be used by a  to pump data from a  to it.

    """

    def write_buffer(self, buff):
        """Write some data to the stream. The data is put on an internal write queue, and the write actually happens
        asynchronously. To avoid running out of memory by putting too much on the write queue,
        check the  method before writing. This is done automatically if using a .
        param [Buffer]. The buffer to write.
        """
        self.java_obj.writeBuffer(buff._to_java_buffer())
    
    def set_write_queue_max_size(self, size):
        """Set the maximum size of the write queue. You will still be able to write to the stream even
        if there is more data than this in the write queue. This is used as an indicator by classes such as
        to provide flow control.

        Keyword arguments
        size -- The maximum size, in bytes.
        """
        self.java_obj.setWriteQueueMaxSize(size)

    write_queue_max_size = property(fset=set_write_queue_max_size)
    
    @property
    def write_queue_full(self):
        """Is the write queue full?

        return True if there are more bytes in the write queue than the max write queue size.
        """
        return self.java_obj.writeQueueFull()
    
    def drain_handler(self, handler):
        """Set a drain handler on the stream. If the write queue is full, then the handler will be called when the write
        queue has been reduced to maxSize / 2. See  for an example of this being used.

        Keyword arguments
        handler -- The drain handler
        """
        self.java_obj.drainHandler(DrainHandler(handler))
    
    def exception_handler(self, handler):
        """Set an execption handler on the stream.

        Keyword arguments
        handler -- The exception handler
        """  
        self.java_obj.exceptionHandler(ExceptionHandler(handler))
    
    def _to_write_stream(self):
        return self.java_obj
    
   
class ReadStream(object):
    """A mixin module which represents a stream of data that can be read from.

    Any class that mixes in this module can be used by a  to pump data from a  to it.
    """

    def data_handler(self, handler):
        """Set a data handler. As data is read, the handler will be called with the data.

        Keyword arguments
        handler -- The data handler
        """
        self.java_obj.dataHandler(DataHandler(handler))
    
    def pause(self):
        """Pause the ReadStream. After calling this, the ReadStream will aim to send no more data to the """
        self.java_obj.pause()
        
    def resume(self):
        """Resume reading. If the ReadStream has been paused, reading will recommence on it."""
        self.java_obj.resume()
        
    def exception_handler(self, handler):
        """Set an execption handler on the stream.
        param [Block] hndlr. The exception handler
        """
        self.java_obj.exceptionHandler(ExceptionHandler(handler))
        
    def end_handler(self, handler):
        """Set an end handler on the stream. Once the stream has ended, and there is no more data to be read, this handler will be called.

        Keyword arguments
        handler -- The exception handler"""
        self.java_obj.endHandler(EndHandler(handler))
        
    def _to_read_stream(self):
        return self.java_obj
        
class Pump(object):
    """Pumps data from a ReadStream to a WriteStream and performs flow control where necessary to
    prevent the write stream from getting overloaded.

    Instances of this class read bytes from a ReadStream and write them to a WriteStream. If data
    can be read faster than it can be written this could result in the write queue of the WriteStream growing
    without bound, eventually causing it to exhaust all available RAM.
    To prevent this, after each write, instances of this class check whether the write queue of the WriteStream
    is full, and if so, the ReadStream is paused, and a WriteStreamdrain_handler is set on the WriteStream.
    When the WriteStream has processed half of its backlog, the drain_handler will be called,
    which results in the pump resuming the ReadStream.

    This class can be used to pump from any ReadStream to any  WriteStream,
    e.g. from an HttpServerRequest to an AsyncFile, or from NetSocket to a WebSocket.
    """

    def __init__(self, read_stream, write_stream):
        #raise "read_stream is not a ReadStream" if !read_stream.is_a? ReadStream
        #raise "write_stream is not a WriteStream" if !write_stream.is_a? WriteStream
        self.j_rs = read_stream._to_read_stream()
        self.j_ws = write_stream._to_write_stream()
        self.j_pump = org.vertx.java.core.streams.Pump.createPump(self.j_rs, self.j_ws)

    def set_write_queue_max_size(self, val):
        """Set the write queue max size

        Keyword arguments
        val -- The write queue max size
        """  
        self.j_pump.setWriteQueueMaxSize(val)

    write_queue_max_size = property(fset=set_write_queue_max_size)

    def start(self):
        """Start the Pump. The Pump can be started and stopped multiple times."""
        self.j_pump.start()

    def stop(self):
        """Stop the Pump. The Pump can be started and stopped multiple times."""  
        self.j_pump.stop()

    @property
    def bytes_pumped(self):
        """return the total number of bytes pumped by this pump."""
        self.j_pump.getBytesPumped()