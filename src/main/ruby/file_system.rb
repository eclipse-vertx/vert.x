require "buffer"

include Java

module FileSystem

  def FileSystem.read_file(path, proc = nil, &data_handler)
    data_handler = proc if proc
    java_handler = Handler.new(data_handler)
    org.nodex.core.file.FileSystem.instance.readFile(path, java_handler)
  end

  class Handler < org.nodex.core.buffer.DataHandler

    def initialize(handler)
      super()
      @handler = handler
    end

    def onData(java_buffer)
      @handler.call(Buffer.new(java_buffer))
    end

  end

end