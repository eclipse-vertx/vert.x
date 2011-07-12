require "stomp"
require "buffer"
include Stomp

topic = "test-topic"

# A very simple example using STOMP to send and consume some messages
# Use this with the java STOMP server in src/examples/java/stomp

StompClient.connect(8181) do |conn|
  conn.subscribe(topic) { |headers, body| puts "Got message #{body}" }
  (1..10).each { |i| conn.send(topic, Buffer.from_str("hello #{i}\n")) }
end

STDIN.gets

