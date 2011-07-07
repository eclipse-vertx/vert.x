require "stomp"
require "buffer"

topic = "test-topic"

Stomp::Client.connect(8080) do |conn|
  conn.subscribe(topic) { |headers, body| puts "Got message #{body}" }
  (1..10).each { |i| conn.send(topic, Buffer.from_str("hello #{i}\n")) }
end

STDIN.gets

