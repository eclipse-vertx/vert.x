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

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"
__credits__ = "Based entirely on work by Tim Fox http://tfox.org"

class SSLSupport(object):
  """ A mixin module allowing SSL attributes to be set on classes """
  
  def set_ssl(self, val):
    """ Set whether the server or client will use SSL.

    Keyword arguments:
    @param val: If true then ssl will be used.
    
    return self. So multiple invocations can be chained.
    """
    self.java_obj.setSSL(val)
    return self

  ssl = property(fset=set_ssl)

  def set_key_store_path(self, path):
    """Set the path to the SSL key store. This method should only be used with the client/server in SSL mode, i.e. after {#ssl=}
    has been set to true.
    The SSL key store is a standard Java Key Store, and should contain the client/server certificate. For a client, it's only necessary to supply
    a client key store if the server requires client authentication via client certificates.
    
    Keyword arguments:
    @param path: The path to the key store
    
    return self. So multiple invocations can be chained.
    """
    self.java_obj.setKeyStorePath(path)
    return self

  key_store_path = property(fset=set_key_store_path)

  def set_key_store_password(self, password):
    """Set the password for the SSL key store. This method should only be used with the client in SSL mode, i.e. after ssl
    has been set to true.
    
    Keyword arguments:
    @param password: The password.
    
    return self. So multiple invocations can be chained.
    """
    self.java_obj.setKeyStorePassword(password)
    return self

  key_store_password = property(fset=set_key_store_password)

  def set_trust_store_path(self, path):
    """Set the path to the SSL trust store. This method should only be used with the client/server in SSL mode, i.e. after {#ssl=}
    has been set to true.
    The SSL trust store is a standard Java Key Store, and should contain the certificate(s) of the clients/servers that the server/client trusts. The SSL
    handshake will fail if the server provides a certificate that the client does not trust, or if client authentication is used,
    if the client provides a certificate the server does not trust.

    Keyword arguments:
    @param path: The path to the trust store
    
    return self. So multiple invocations can be chained.
    """
    self.java_obj.setTrustStorePath(path)
    return self

  trust_store_path = property(fset=set_trust_store_path)

  def set_trust_store_password(self, password):
    """Set the password for the SSL trust store. This method should only be used with the client in SSL mode, i.e. after {#ssl=}
    has been set to true.

    Keyword arguments:
    @param password: The password.
     
    return self. So multiple invocations can be chained.
    """
    self.java_obj.setTrustStorePassword(password)
    return self

  trust_store_password = property(fset=set_trust_store_password)
    