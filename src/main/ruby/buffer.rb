class Buffer

  def initialize(java_buffer)
    @buffer = java_buffer
  end

  def Buffer.new_fixed(size)
    Buffer.new(org.nodex.core.buffer.Buffer.newFixed(size))
  end

  def Buffer.new_dynamic(size)
    Buffer.new(org.nodex.core.buffer.Buffer.newDynamic(size))
  end

  def Buffer.from_str(str, enc = "UTF-8")
    Buffer.new(org.nodex.core.buffer.Buffer.fromString(str, enc))
  end

  def write(str, offset = 0, enc = "UTF-8")
    @buffer.write(str, offset, enc)
  end

  def byte_at(pos)
    @buffer.byteAt(pos)
  end

  def to_s(enc = "UTF-8")
    @buffer.toString(enc)
  end

  def _to_java_buffer
    @buffer
  end

end
