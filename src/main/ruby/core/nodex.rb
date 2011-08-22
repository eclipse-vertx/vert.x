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
java_import org.nodex.core.NodexMain
java_import org.nodex.core.Nodex

module Nodex

  class TheMain < NodexMain

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
    Nodex.instance.setTimeout(delay, hndlr)
  end

  def Nodex.set_periodic(delay, proc = nil, &hndlr)
    hndlr = proc if proc
    Nodex.instance.setPeriodic(delay, hndlr)
  end

  def Nodex.cancel_timeout(id)
    Nodex.instance.cancelTimeout(id)
  end

  def Nodex.register_actor(proc = nil, &hndlr)
    hndlr = proc if proc
    Nodex.instance.registerActor(handlr)
  end

  def Nodex.unregister_actor(actor_id)
    Nodex.instance.unregisterActor(actor_id)
  end

  def Nodex.send_message(actor_id, msg)
    Nodex.instance.sendMessage(actor_id, msg)
  end


end