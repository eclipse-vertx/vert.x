require "amqp"
include Amqp

queue = "test-queue"

# Create a channel, declare a queue, send some messages and consume them

AmqpClient.create_client.connect { |conn|
  conn.create_channel { |chan|
    chan.declare_queue(queue, false, true, true) {
      chan.subscribe(queue, true) { |props, body|
        puts "Received message #{body}"
      }
      (1..10).each {|i| chan.publish("", queue, "message #{i}") }
    }
  }
}

STDIN.gets
