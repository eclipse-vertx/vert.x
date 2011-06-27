require "net"
require "parser_tools"

Server.create_server{ |socket|
  socket.data(ParserTools.split_on_delimiter("\n") { |line|
    line = line.to_s.rstrip
    if line.start_with?("subscribe,")
      topic_name = line.split(",", 2)[1]
      puts "subscribing to #{topic_name}"
      @topics ||= {}
      topic = @topics[topic_name]
      if (topic.nil?)
        topic = []
        @topics[topic_name] = topic
      end
      topic << socket
    elsif line.start_with?("publish,")
      sp = line.split(',', 3)
      puts "publishing to #{sp[1]} with #{sp[2]}"
      topic = @topics[sp[1]]
      if (topic)
        topic.each{|socket| socket.write(Buffer.from_str(sp[2]))}
      end
    end
  })
}.listen(8080)

puts "hit enter to stop server"
STDIN.gets
server.stop
