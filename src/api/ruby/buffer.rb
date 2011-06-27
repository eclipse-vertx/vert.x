require "EchoServer"

include_class "org.nodex.core.buffer.Buffer"

class Buffer

  def initialize(*args)
    arg0 = args[0]
    case arg0
      when Buffer
        @buffer = arg0
      when Array
        @buffer = Buffer.new(arg0)
      when Integer
        @buffer = Buffer.new(arg0)
      when String
        @buffer = Buffer.new(arg0, args[1])
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
