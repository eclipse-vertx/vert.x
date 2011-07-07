module Composition
  class Composer
    def Composer.compose
      Composer.new
    end

    def initialize
      @java_composer = org.nodex.core.composition.Composer.compose
    end

    def parallel(*deferreds)
      @java_composer.parallel(deferreds)
    end

    def then(deferred)
      @java_composer.then(deferred)
    end

    def run
      @java_composer.run
    end

    private :initialize
  end

  class Deferred

    def initialize(java_deferred)
      @java_deferred = deferred
    end

    def execute
      @java_deferred.execute
    end

    def on_complete(proc = nil, &complete_block)
      complete_block = proc if proc
      @java_deferred.onComplete(CompleteCallback.new(complete_block))
    end

    class CompleteCallback < org.nodex.core.DoneHandler
      def initialize(callback)
        super()
        @callback = callback
      end

      def onEvent()
        @callback.call
      end

      private :initialize
    end

  end
end