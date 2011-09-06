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

module Composition
  class Composer
    def Composer.compose
      Composer.new
    end

    def initialize
      @java_composer = org.nodex.java.core.composition.Composer.compose
    end

    # parallel and then can be combined

    COMPLETION_CLASS_SYM = "org.nodex.java.core.composition.Composable".to_sym

    def when(*completions, &block)
      if block
        completion = Deferred.new(block)
        completions = [completion]
      end
      java_completions = []
      completions.each { |c| java_completions << c._to_java_completion }
      @java_composer.when(java_completions.to_java(COMPLETION_CLASS_SYM))
      self
    end

    def parallel(*completions)
      java_completions = []
      completions.each { |c| java_completions << c._to_java_completion }
      @java_composer.parallel(java_completions.to_java(COMPLETION_CLASS_SYM))
      self
    end

    def then(completion = nil, &block)
      completion = Deferred.new(block) if !completion
      @java_composer.then(completion._to_java_completion)
      self
    end

    def end
      @java_composer.end
    end

    private :initialize
  end

  class Completion

    def Completion.create
      Completion.new(org.nodex.java.core.composition.Composable.new)
    end

    def Completion.create_from_java_completion(java_completion)
      Completion.new(java_completion)
    end

    def initialize(java_completion)
      @java_completion = java_completion
    end

    def on_complete(proc = nil, &complete_block)
      complete_block = proc if proc
      @java_completion.onComplete(CompleteHandler.new(complete_block))
    end

    def complete
      @java_completion.complete
    end

    def _to_java_completion
      @java_completion
    end

    private :initialize

  end

  class Deferred
    def initialize(proc = nil, &block)
      block = proc if proc
      @java_deffered = org.nodex.java.core.composition.Deferred.new(CompleteHandler.new(block))
    end

    def _to_java_completion
      @java_deffered
    end

  end

  class CompleteHandler < org.nodex.java.core.Runnable
    def initialize(callback)
      super()
      @callback = callback
    end

    def run()
      @callback.call
    end

    private :initialize
  end

end