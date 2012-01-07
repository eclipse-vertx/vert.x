require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

@tu.register('test1') do

  @client = NetClient.new
  @client.connect(8080, "localhost") do |socket|
    socket.data_handler do |data|
      puts "Echo client received #{data.to_s}"
      @tu.test_complete('test1')
      puts "Sending test complete!!!!"
    end
    str = "hello"
    puts "Echo client sending #{str}"
    socket.write_buffer(Buffer.create_from_str(str))
  end

end

@tu.app_ready

def vertx_stop
  @client.close
  @tu.app_stopped
end