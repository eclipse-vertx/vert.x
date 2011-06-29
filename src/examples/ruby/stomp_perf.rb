require "stomp"
require "buffer"
include Stomp

warmup = 500000;
num_messages = 1000000;
count = 0
start = nil

Client.connect(8080, "localhost") { |conn|
  conn.subscribe("test-topic") { |msg|
    count += 1
    if count == warmup + num_messages
      puts "elapsed time is #{Time.now - start}"
      rate = (num_messages) / (Time.now - start)
      puts "Rate is #{rate}"
    end
  }
  buf = Buffer.from_str("msg")
  (1..warmup).each {|i|
    conn.send("test-topic", buf)
  }
  start = Time.now
  (1..num_messages).each {|i|
    conn.send("test-topic", buf)
  }
}

STDIN.gets

