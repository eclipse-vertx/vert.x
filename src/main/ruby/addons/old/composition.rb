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

  # Represents an operation that may or may not have completed yet.
  # It contains methods to determine it it has completed, failed or succeded, and allows a handler to
  # be set which will be called when the operation completes, or fails.
  # Instances of this class are normally created by library code
  # @author {http://tfox.org Tim Fox}
  class Future

    # @private
    def initialize(j_del, &result_converter)
      @j_del = j_del
      @result_converter = result_converter
    end

    # @return [Object] Returns the result of the operation. If the operation has not yet completed, or if it failed it will return nil.
    def result
      if !@result_converted
        j_result = @j_del.result
        if (j_result == nil)
          @result = nil
        else
          if (@result_converter == nil)
            @result = j_result
          else
            @result = @result_converter.call(j_result)
          end
          @result_converted = true
        end
      end
      @result
    end

    # @return [] Returns the exception. An exception is always set if the operation failed.
    # If the operation has not yet completed, or if it succeeded it will return nil.
    def exception
      @j_del.exception
    end

    # @return [Boolean] This will return true if the operation has completed, or failed.
    def complete?
      @j_del.complete
    end

    # @return [Boolean] This will return true if the operation succeeded.
    def succeeded?
      @j_del.succeeded
    end

    # @return [Boolean] This will return true if the operation failed.
    def failed?
      @j_del.failed
    end

    # Set a handler on the Future. If the operation has already completed it will be called immediately, otherwise
    # it will be called when the operation completes or fails, passing in a reference to self.
    def handler(proc = nil, &hndlr)
      hndlr = proc if proc
      this = self
      @j_del.handler{ |j_def| hndlr.call(this) }
      self
    end

    # @private
    def _to_j_del
      @j_del
    end

  end

  # A simple Future instance that you can use from inside blocks passed into {Composer#series} or {Composer#parallel}
  # @author {http://tfox.org Tim Fox}
  class SimpleFuture < Future
    def initialize
      @j_del = org.vertx.java.core.impl.SimpleFuture.new
      super(@j_del)
    end

    # Set the result of the Future
    # @param [] The result to set
    def result=(res)
      @j_del.setResult(res)
    end

    # Set the Future as failed
    # @param [] The exception
    def exception=(ex)
      @j_del.setException(ex)
    end
  end

  # Composer allows asynchronous control flows to be defined.
  #
  # This is useful when you have some asynchronous actions to be performed after other actions have completed.
  # In a asynchronous framework such as vert.x you cannot just block on the result of one action before executing
  # the next one, since an event loop thread must never block. By using Composer you can describe the execution order
  # of a sequence of actions in a quasi-direct way, even though when they execute it will all be asynchronous.
  #
  # Each action to execute is represented by a block or a Proc. You can think of these as "thunks".
  # Each block or Proc can return a Future instance. If it does return a Future then the action represented by that
  # block is not considered complete until that Future completes.
  #
  # An example of using this class is as follows:
  #
  # @example
  #
  #   comp = new Composer.new
  #   comp.parallel{ puts "copying" }
  #   comp.parallel{ FileSystem.copy("from", "to")}
  #   comp.series{ puts "copy complete" }
  #   comp.parallel{ redis_connection.set("key1", "val1") }
  #   comp.series{ puts "set value ok" }
  #   future = comp.series{ redis_connection.get("key1") }
  #   comp.series{ puts "value of key1 is #{future.result}" }
  #   comp.execute
  #
  # In the above example, when {#execute} is invoked, "copying" will be printed and immediately the filesystem copy
  # from "from" to "to" will start, since both these actions are declared to run in parallel.
  # Some time later the copy will complete, and since the "copying" action completed immediately the next set of actions
  # will be executed. This will result in "copy complet" being displayed and immediately the redis key "key" will be set
  # to "val1". When the operation to set the redis key is complete, the next action will be executed and
  # "set value ok" will be displayed. Once this is complete, the value of the key previously set is retrieved and
  # the future result of getting it is represented by future.
  # Some time later when the value is actually got, "value of key1 is val" is displayed.
  #
  # @author {http://tfox.org Tim Fox}
  class Composer
    def initialize
      @j_comp = org.vertx.java.core.composition.Composer.new
    end

    def parallel(proc = nil, &block)
      block = proc if proc
      deff = create_def(block)
      @j_comp.parallel(deff)
    end

    def series(proc = nil, &block)
      block = proc if proc
      deff = create_def(block)
      @j_comp.series(deff)
    end

    def execute
      @j_comp.execute
    end

    def exception_handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_comp.exceptionHandler(hndlr)
    end

    private

    # @private
    class Deff < org.vertx.java.core.impl.DeferredAction

      def initialize(&blk)
        @blk = blk
      end

      def run
        @blk.call(self)
      end

      def result=(res)
        setResult(res)
      end

      def exception=(ex)
        setException(ex)
      end
    end

    def create_def(block)
      Deff.new do |d|
        res = block.call
        if res.is_a? Future
          res.handler do |d2|
            if res.succeeded?
              d.setResult(res.result)
            else
              d.setException(res.exception)
            end
          end
        else
          # we treat it like a synchronous action
          d.setResult(res)
        end
      end
    end


  end

end

