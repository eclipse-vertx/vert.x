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

include Java

require 'core/global_handlers'
require 'core/timers'
require 'core/buffer'
require 'core/file_system'
require 'core/http'
require 'core/net'
require 'core/parsetools'
require 'core/streams'
require 'core/shared_data'
require 'core/std_io'
require 'core/composition'
require 'core/logger'
require 'core/event_bus'
require 'core/sock_js'

require 'addons/redis'

# The Vertx modules defines the top level namespace within which all Vertx classes are found.
#
# This module also contains some class methods used for such things as setting and cancelling timers and
# global event handlers, amongst other things.
#
# @author {http://tfox.org Tim Fox}
module Vertx

  # @private
  # !! This method is for internal use only - do not call from user code.
  def Vertx.internal_go(proc = nil, &block)
    block = proc if proc
    org.vertx.java.core.VertxInternal.instance.start_on_event_loop(block)
  end

  # @private
  class InternalAction < org.vertx.java.core.BlockingAction

    # @private
    def initialize(hndlr)
      super()
      @action = hndlr
    end

    def action
      @action.call
    end
  end

  # Sometimes it is necessary to perform operations in vert.x which are inherently blocking, e.g. talking to legacy
  # blocking APIs or libraries. This method allows blocking operations to be executed cleanly in an asychronous
  # environment.
  # This method will execute the proc or block on a thread from a
  # background thread pool specially reserved for blocking operations. This means the event loop threads are not
  # blocked and can continue to service other requests.
  def Vertx.run_blocking(proc = nil, &block)
    block = proc if proc
    ia = InternalAction.new(block)
    ia.execute
    Future.new(ia)
  end

  # Cause vert.x to exit. Any stop methods of running vert.x applications will be called first
  # Cannot be called if vert.x is running in server mode.
  def Vertx.exit
    org.vertx.java.core.Vertx.instance.exit
  end

end