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

module Nodex

  # Represents an operation that may or may not have completed yet.
  # It contains methods to determine it it has completed, failed or succeded, and allows a handler to
  # be set which will be called when the operation completes, or fails.
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
      @j_del.handler{ |j_def| hndlr.call(self) }
      self
    end

    # @private
    def _to_j_del
      @j_del
    end

  end

  # Represents something that hasn't happened yet, but will occur when the {#execute} method is called.
  # Once it has been executed it behaves identically to a {Future}.
  #
  # Instances of Deferred can represent all kinds of deferred operations, e.g. copying a file or getting a value from
  # a Redis server. Since the actual execution of the action is deferred until the execute method is called, it allows
  # multiple instances of Deferred to be composed together into more complex control flows, e.g. using the {org.nodex.java.core.composition.Composer} class.
  #
  # @author {http://tfox.org Tim Fox}
  class Deferred < Future

    # @private
    # For internal use only
    def initialize(j_del, &result_converter)
      super(j_del, &result_converter)
      @j_del = j_del
    end

    # Execute the deferred operation. If the Deferred has already been executed the call will be ignored.
    # Once the deferred has been executed it behaves like a {Future}.
    def execute
      raise "Cannot execute a Deferred which has been returned by a Composer" if @block_execution
      @j_del.execute
    end

    # @private
    def _to_j_del
      @j_del
    end

    # @private
    def block_execution
      @block_execution = true
      self
    end

  end

  # DeferredAction is useful when you want to create your own Deferred actions.
  #
  # Normally, instances of Deferred are returned from node.x modules to represent operations such as getting a key from
  # a Redis server, or copying a file. However if you wish to create your own instances you can do this by creating an
  # instance of this class specifying a block/Proc when creating it.
  #
  # The block/Proc will get called when the action is executed. Be sure to call {#result=} or {#exception=} when
  # the action is complete.
  #
  # @author {http://tfox.org Tim Fox}
  class DeferredAction < Deferred

    # @private
    class InternalDeferred < org.nodex.java.core.DeferredAction

      def initialize(hndlr)
        super()
        @hndlr = hndlr
      end

      def run
        @hndlr.call
      end
    end

    # Create a new DeferredAction
    # @param [Proc] A proc representing the action to run when this is executed
    # @param [block] A block representing the action to run when this is executed
    def initialize(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del = InternalDeferred.new(hndlr)
      super(@j_del)
    end

    # Set the result. If the operation succeeded you must call this when the operation is complete
    def result=(result)
      @j_del.setResult(result)
    end

    # Set the exception. If the operation failed you must call this when the operation is complete
    def exception=(exception)
      @j_del.setException(exception)
    end

  end

  # Composer allows asynchronous control flows to be defined.
  #
  # This is useful when you have some asynchronous actions to be performed after other actions have completed.
  # In a asynchronous framework such as node.x you cannot just block on the result of one action before executing
  # the next one, since an event loop thread must never block. By using Composer you can describe the execution order
  # of a sequence of actions in a quasi-direct way, even though when they execute it will all be asynchronous.
  #
  # Each action to execute is represented by an instance of {Deferred}. A Deferred represents an action
  # which is yet to be executed. Instances of Deferred can represent any asynchronous action, e.g. copying a file from A to B,
  # or getting a key from a Redis server.
  #
  # An example of using this class is as follows:
  #
  # @example
  #   # d1..dn are instances of Deferred, returned from other node.x modules, e.g. {@link org.nodex.java.core.file.FileSystem}
  #
  #   comp = new Composer.new
  #   comp.parallel(d1)
  #   comp.parallel(d2)
  #   comp.parallel(d3)
  #   comp.series(d4)
  #   comp.parallel(d5)
  #   comp.series(d6)
  #   comp.series(d7)
  #   comp.parallel(d8)
  #   comp.parallel(d9)
  #   comp.execute
  #
  # In the above example, when {#execute} is invoked, d1, d2, and d3 will be executed. When d1, d2, and d3
  # have all completed, then d4 and d5 will be executed. When d4 and d5 have completed d6 will be executed.
  # When d6 has completed d7, d8 and d9 will be executed. All this will occur asynchronous with no thread blocking for
  # anything to complete.
  #
  # Here is another example which uses the return values from actions in subsequent actions:
  #
  # @example
  #   comp = Composer.new
  #   future = comp.series(d1)
  #   comp.series{ puts "Result of d1 is #{future.result}" }
  #   comp.execute
  #
  # In the above example, when {#execute} is invoked d1 will be executed. When d1 completes the result of the action
  # will be available in the {Future} future, and the second action will be executed which simply displays the result.
  #
  # @author {http://tfox.org Tim Fox}
  class Composer
    def initialize
      @j_comp = org.nodex.java.core.composition.Composer.new
    end

    # Add a {Deferred} or a block to be executed in parallel with any other Deferred instances added using this method since the
    # last time (if any) the {#series} method was invoked. All such Deferred instances will be executed only
    # when there are no more Deferred instances that have not yet completed and were added before the last call to {#series}.
    # @param [Deferred] deferred - The [Deferred] to add. This can be nil if a block is specified instead.
    # @param [Block] block - An arbitrary block to execute with the same semantics of the [Deferred].
    # @return [Future] A Future representing the future result of the Deferred.
    def parallel(deferred = nil, &block)
      if deferred != nil
        check_deferred(deferred)
        @j_comp.parallel(deferred._to_j_del)
        deferred.block_execution
      elsif block != nil
        j_def = TheAction.new(block)
        @j_comp.parallel(j_def)
      end
    end

    # Adds a Deferred that will be executed after any other Deferred instances that were added to this Composer have
    # completed.
    # @param [Deferred] deferred - The [Deferred] to add. This can be nil if a block is specified instead.
    # @param [Block] block - An arbitrary block to execute with the same semantics of the [Deferred].
    # @return [Future] A Future representing the future result of the Deferred.
    def series(deferred = nil, &block)
      if deferred != nil
        check_deferred(deferred)
        @j_comp.series(deferred._to_j_del)
        deferred.block_execution
      elsif block != nil
        j_def = TheAction.new(block)
        @j_comp.series(j_def)
      end
    end

    # Start executing any Deferred instances added to this composer. Any instances added with {#parallel} will
    # be executed immediately until the first instance added with {#series} is encountered. Once all of those
    # instances have completed then, the next "batch" of Deferred instances starting from instance added with {#series}
    # up until the next instance added with {#series} will be executed. Once all of those have completed the next batch
    # up until the next {#series} will be executed. This process continues until all Deferred instances have been
    # executed.
    def execute
      @j_comp.execute
    end

    # @private
    def check_deferred(deferred)
      raise "Please specify an instance of Nodex::Deferred" if !deferred.is_a? Deferred
    end

    private :check_deferred

    # @private
    class TheAction < org.nodex.java.core.SynchronousAction

      def initialize(block)
        super()
        @block = block
      end

      def action
        @block.call
      end

    end
  end

end

