require "amqp"
include Amqp

queue = "test-queue"

Client.create_client.connect { |conn|
  conn.create_channel { |chan|
    chan.declare(queue, false, true, true) {
      chan.subscribe(queue, true) { |msg|
        puts "Received message #{msg}"
      }
      (1..10).each {|i| chan.publish("", queue, "message #{i}") }
    }
  }
}


STDIN.gets
