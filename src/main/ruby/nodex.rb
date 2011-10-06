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

require 'addons/redis'

# The Nodex modules defines the top level namespace within which all Nodex classes are found.
#
# This module also contains some class methods used for such things as setting and cancelling timers and
# global event handlers, amongst other things.
#
# @author {http://tfox.org Tim Fox}

module Nodex

  # @private
  class TheMain < org.nodex.java.core.NodexMain

    def initialize(block)
      super()
      @block = block
    end

    def go
      @block.call
    end

  end

  # Runs a block in an event loop. The event loop will be chosen from all available loops by the system.
  # Most node.x operations have to be run in an event loop, so this method is usually used at the beginning of your
  # script to start things running.
  # This method will accept either a Proc or a block.
  # @param [Proc] proc a Proc to run
  # @param [Block] block a block to run.
  def Nodex.go(proc = nil, &block)
    block = proc if proc
    TheMain.new(block).run
  end

end