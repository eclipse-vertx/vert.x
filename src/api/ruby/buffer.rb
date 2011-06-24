require "java"

include_class "org.nodex.core.buffer.JavaBuffer"

class Buffer

  def initialize(*args)
    arg0 = args[0]
    case arg0
      when JavaBuffer
        @buffer = arg0
      when Array
        @buffer = JavaBuffer.new(arg0)
      when Integer
        @buffer = JavaBuffer.new(arg0)
      when String
        @buffer = JavaBuffer.new(arg0, args[1])
    end
  end

  def write(str, offset = 0, enc = "UTF-8")
    @buffer.write(str, offset, enc)
  end

  def byte_at(pos)
    @buffer.byteAt(pos)
  end

  def to_s(enc)
    @buffer.toString(enc)
  end

  def _to_java_buffer
    @buffer
  end

end
