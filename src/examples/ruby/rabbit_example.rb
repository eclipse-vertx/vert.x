module Rabbit
  def Rabbit.subscribe(queue_name, &on_message_block)
    on_message_block.call("message")
  end

  def Rabbit.subscribe2(queue_name, on_message_method)
    on_message_method.call("message")
  end

  def Rabbit.send(exchange_name, routing_key, val)
    puts "Sending message #{val}"
  end
end

module GemFire
  def GemFire.lookup(key, &on_lookup_block)
    on_lookup_block.call(10)
  end
end

module Riak
  def Riak.lookup(key, &on_lookup_block)
    on_lookup_block.call(20)
  end
end

def combine(val1, val2)
  val1 + val2
end


puts "Starting"

#Using blocks

Rabbit.subscribe("myqueue") {
  |msg| GemFire.lookup("key") {
    |val1| Riak.lookup("key") {
      |val2| Rabbit.send("exchange", "routing", combine(val1, val2))
    }
  }
}

#Using method on classes

#class RabbitGemfireRiakConversation
#
#  def on_message(msg)
#    @msg = msg
#    GemFire.lookup("key", self.on_gemfire_lookup)
#  end
#
#  def onEvent
#    Rabbit.subscribe2("myqueue", on_message)
#  end
#
#  def on_gemfire_lookup(val)
#    @gf_val = val
#    Riak.lookup("key", self.on_riak_lookup)
#  end
#
#  def on_riak_lookup(val)
#    Rabbit.send("exchange", "routing", combine(@gf_val, val)
#  end
#
#
#end






