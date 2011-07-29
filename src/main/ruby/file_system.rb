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

require "buffer"

include Java

module FileSystem

  def FileSystem.read_file(path, proc = nil, &data_handler)
    data_handler = proc if proc
    java_handler = Handler.new(data_handler)
    org.nodex.core.file.FileSystem.instance.readFile(path, java_handler)
  end

  class Handler < org.nodex.core.buffer.DataHandler

    def initialize(handler)
      super()
      @handler = handler
    end

    def onData(java_buffer)
      @handler.call(Buffer.new(java_buffer))
    end

  end

end