#There must be a better way of doing this!!
$LOAD_PATH << ENV['LOAD_PATH']

require "net"

Server.create_server{ |socket|
  LineReader.readLine(socket) { |line|
    line = line.rstrip
    if line.start_with?("subscribe,")
      topic_name = line.split(",", 2)[1]
      @topics ||= {}
      topic = @topics[topic_name]
      if (topic.nil?)
        topic = []
        @topics[topic_name] = topic
      end
      topic << socket
    elsif line.start_with?("publish,")
      sp = line.split(',', 3)
      topic = @topics[sp[1]]
      if (topic)
        topic.each{|socket| socket.write(sp[2])}
      end
    end
  }
}.listen(8080, "127.0.0.1")


# This is really boilerplate and would be provided in API - no need for dev to write this
module LineReader
  def LineReader.readLine(socket, &on_read_line_block)
    line = ""
    socket.data{ |data|
      line += data
      if (line.include?("\n"))
        sp = line.split("\n", 2)
        line = sp[1]
        on_read_line_block.call(sp[0])
      end
    }
  end
end

