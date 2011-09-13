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

require 'core/streams'
require 'core/ssl_support'

module Nodex

  module TCPSupport

    def send_buffer_size=(val)
      @j_del.setSendBufferSize(val)
    end

    def receive_buffer_size=(val)
      @j_del.setReceiveBufferSize(val)
    end

    def keep_alive=(val)
      @j_del.setKeepAlive(val)
    end

    def reuse_address=(val)
      @j_del.setReuseAddress(val)
    end

    def so_linger=(val)
      @j_del.setSoLinger(val)
    end

    def traffic_class=(val)
      @j_del.setTrafficClass(val)
    end

  end

  class NetServer

    include SSLSupport, TCPSupport

    def initialize
      @j_del = org.nodex.java.core.net.NetServer.new
    end

    def client_auth_required=(val)
      @j_del.setClientAuthRequired(val)
    end

    def connect_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del.connectHandler { |j_socket| hndlr.call(NetSocket.new(j_socket)) }
    end

    def listen(port, host = "0.0.0.0")
      @j_del.listen(port, host)
      self
    end

    def close(&hndlr)
      @j_del.close(hndlr)
    end

  end

  class NetClient

    include SSLSupport, TCPSupport

    def initialize
      @j_del = org.nodex.java.core.net.NetClient.new
    end

    def trust_all=(val)
      @j_del.setTrustAll(val)
    end

    def connect(port, host = "localhost", proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del.connect(port, host) { |j_socket| hndlr.call(NetSocket.new(j_socket)) }
    end

    def close
      @j_del.close
    end

  end

  class NetSocket

    include ReadStream, WriteStream

    def initialize(j_socket)
      @j_del = j_socket
      @write_handler_id = Nodex::register_handler { |buffer|
        write_buffer(buffer)
      }
      @j_del.closedHandler(Proc.new {
        Nodex::unregister_handler(@write_handler_id)
        @closed_handler.call if @closed_handler
      })
    end

    def write_buffer(buff, &compl)
      j_buff = buff._to_java_buffer
      if compl == nil
        @j_del.write(j_buff)
      else
        @j_del.write(j_buff, compl)
      end
    end

    def write_str(str, enc = nil, &compl)
      if (compl == nil)
        @j_del.writeString(str, enc)
      else
        @j_del.writeString(str, enc, compl)
      end
    end

    def closed_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @closed_handler = hndlr;
    end

    def send_file(file_path)
      @j_del.sendFile(file_path)
    end

    def close
      @j_del.close
    end

    def write_handler_id
      @write_handler_id
    end

  end
end

