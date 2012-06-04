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

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"

class TCPSupport(object):
  """ Mixin module that provides all the common TCP params that can be set. """
  
  def set_send_buffer_size(self, bytes):
    """Set the TCP send buffer size.

    Keyword arguments
    bytes -- The size in bytes.

    return a reference to self so invocations can be chained
    """
    self.java_obj.setSendBufferSize(bytes)
    return self

  send_buffer_size = property(fset=set_send_buffer_size)

  def set_receive_buffer_size(self, bytes):
    """Set the TCP receive buffer size.
    
    Keyword arguments
    bytes -- The size in bytes.
  
    return a reference to self so invocations can be chained
    """
    self.java_obj.setReceiveBufferSize(bytes)
    return self

  receive_buffer_size = property(fset=set_receive_buffer_size)

  def set_tcp_keep_alive(self, val):
    """Set the TCP keep alive setting.

    Keyword arguments
    val -- If true, then TCP keep alive will be enabled.
    
    return a reference to self so invocations can be chained
    """
    self.java_obj.setTCPKeepAlive(val)
    return self

  tcp_keep_alive = property(fset=set_tcp_keep_alive)

  def set_reuse_address(self, val):
    """Set the TCP reuse address setting.

    Keyword arguments
    val -- If true, then TCP reuse address will be enabled.
    
    returns a reference to self so invocations can be chained
    """
    self.java_obj.setReuseAddress(val)
    return self

  reuse_address = property(fset=set_reuse_address)

  def set_so_linger(self, val):
    """Set the TCP so linger setting.

    Keyword arguments
    val -- If true, then TCP so linger will be enabled.
    
    return a reference to self so invocations can be chained
    """
    self.java_obj.setSoLinger(val)
    return self

  so_linger = property(fset=set_so_linger)

  def set_traffic_class(self, val):
    """Set the TCP traffic class setting.

    Keyword arguments
    val -- The TCP traffic class setting.
    
    return a reference to self so invocations can be chained
    """
    self.java_obj.setTrafficClass(val)
    return self

  traffic_class = property(fset=set_traffic_class)