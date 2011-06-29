include Java
require "buffer"

module ParserTools

  class RecordParser

    class OutputCallback < org.nodex.core.Callback
      def initialize(output_block)
        super()
        @output_block = output_block
      end

      def onEvent(java_frame)
        #FIXME - convert to Ruby frame??
        @output_block.call(java_frame);
      end
    end

    def RecordParser.new_delimited(delim, enc = "UTF-8", proc = nil, &output_block)
      output_block = proc if proc
      org.nodex.core.parsetools.RecordParser.newDelimited(delim, enc, OutputCallback.new(output_block))
    end

    def RecordParser.new_bytes_delimited(bytes, proc = nil, &output_block)
      output_block = proc if proc
      org.nodex.core.parsetools.RecordParser.newDelimited(bytes, OutputCallback.new(output_block))
    end

    def RecordParser.new_fixed(size, proc = nil, &output_block)
      output_block = proc if proc
      org.nodex.core.parsetools.RecordParser.newFixed(size, OutputCallback.new(output_block))
    end

    def delimited_mode(delim, enc)
      org.nodex.core.parsetools.RecordParser.delimitedMode(delim, enc)
    end

    def bytes_delimited_mode(bytes)
      org.nodex.core.parsetools.RecordParser.delimitedMode(bytes)
    end

    def fixed_mode(size)
      org.nodex.core.parsetools.RecordParser.fixedSizeMode(size)
    end
  end

end