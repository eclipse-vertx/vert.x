include Java
require "buffer"

module ParserTools

  class LineCallback < org.nodex.core.Callback

    def initialize(emitter)
      super()
      @emitter = emitter
    end

    def onEvent(data)
      @emitter.call(Buffer.new(data))
    end

    private :initialize
  end

  def ParserTools.split_on_delimiter(delim, &emitter)
    callback = org.nodex.core.parsetools.ParserTools.splitOnDelimiter(delim, LineCallback.new(emitter))

    Proc.new{ |data| callback.onEvent(data._to_java_buffer) }

  end

end