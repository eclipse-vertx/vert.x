require "stomp"
require "buffer"

topic = "test-topic"

# A very simple example using STOMP to send and consume some messages
# Use this with the java STOMP server in src/examples/java/stomp

Stomp::Client.connect(8080) do |conn|
  conn.subscribe(topic) { |headers, body| puts "Got message #{body}" }
  (1..10).each { |i| conn.send(topic, Buffer.from_str("hello #{i}\n")) }
end

STDIN.gets

