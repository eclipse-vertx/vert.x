require "stomp"
require "buffer"
include Stomp

topic = "test-topic"

Client.connect(8080) do |conn|
  conn.subscribe(topic) { |msg| puts "Got message #{msg.body.to_s}" }
  (1..10).each {|i| conn.send(topic, Buffer.from_str("hello #{i}\n")) }
end

STDIN.gets

