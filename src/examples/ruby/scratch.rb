
puts "starting scratch"




class Client

  def fire_receipt
    @action.fire_receipt
  end

  def subscribe(dest, &message_block)
    @action = Action.new
  end

  class Action

    def fire_receipt
      @receipt_block.call
    end

    def on_receipt(&receipt_block)
      @receipt_block = receipt_block
    end
  end


end

client = Client.new

client.subscribe("mydest") { |msg|
  puts "got message #{msg}"
}.on_receipt{
  puts "got receipt"
}

client.fire_receipt


