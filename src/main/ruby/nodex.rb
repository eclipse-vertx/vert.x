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

include Java

require 'core/buffer'
require 'core/file_system'
require 'core/http'
require 'core/net'
require 'core/parsetools'
require 'core/pump'
require 'core/shared_data'

module Nodex

  class TheMain < org.nodex.java.core.NodexMain

    def initialize(block)
      super()
      @block = block
    end

    def go
      @block.call
    end

  end

  def Nodex.go(&block)
    TheMain.new(block).run
  end

  def Nodex.set_timeout(delay, proc = nil, &hndlr)
    hndlr = proc if proc
    org.nodex.java.core.Nodex.instance.setTimeout(delay, hndlr)
  end

  def Nodex.set_periodic(delay, proc = nil, &hndlr)
    hndlr = proc if proc
    org.nodex.java.core.Nodex.instance.setPeriodic(delay, hndlr)
  end

  def Nodex.cancel_timeout(id)
    org.nodex.java.core.Nodex.instance.cancelTimeout(id)
  end

  def Nodex.register_handler(proc = nil, &hndlr)
    hndlr = proc if proc
    org.nodex.java.core.Nodex.instance.registerHandler(hndlr)
  end

  def Nodex.unregister_handler(actor_id)
    org.nodex.java.core.Nodex.instance.unregisterHandler(actor_id)
  end

  def Nodex.send_to_handler(actor_id, msg)
    msg = msg.copy if msg.is_a?(Buffer)
    org.nodex.java.core.Nodex.instance.sendToHandler(actor_id, msg)
  end


end