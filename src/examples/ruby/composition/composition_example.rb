require "net"
require "buffer"
require "stomp"
require "http"
require "file_system"
require "amqp"
require "redis"
require "composition"
include Net
include Http
include FileSystem
include Amqp
include Redis
include Stomp
include Composition

AMQP_QUEUE = "availability"
STOMP_DESTINATION = "availability-request"

def http_server
  channel_pool = ChannelPool.create_pool
  HttpServer.create_server{ |conn|
    conn.request{ |req, resp|
      puts "Request uri is #{req.uri}"
      if req.uri == "/"
        puts "Serving index page"
        # Serve the main page
        FileSystem.read_file("index.html") { |data|
          resp.write_buffer(data)
          resp.end
        }
      elsif req.uri.start_with? "/submit"
        item = req.get_param("item")
        channel_pool.get_channel{ |chan|
          props = Props.new
          props.headers["item"] = item
          chan.request("", AMQP_QUEUE, props, nil) { |resp_props, body|
            # We get a response back with the price and number of items in stock
            price = resp_props.headers["price"]
            stock = resp_props.headers["stock"]
            content = "<html><body>Price is: #{price}<br>Stock is: #{stock}</body></html>"
            resp.write_str(content, "UTF-8").end
          }
        }
      end
    }
  }.listen(8080, "localhost")
end

# The AMQP worker consumes from the queue and then calls redis to get the price for the item, and does a request/response
# from the STOMP queue to get the stock availability of the item. This is done in parallel.
# When both results are in, it sends back a message with both results
def amqp_worker

  # First we need to create a connection to redis
  redis_conn = nil
  redis_connected = Completion.create
  RedisClient.create_client.connect(6379, "localhost") { |conn|
    # We add a little reference data that we're going to need later
    conn.set("bicycle", "125") {
      conn.set("aardvark", "333") {
        redis_conn = conn
        redis_connected.complete
      }
    }
  }

  # And we create a connection to the STOMP broker
  stomp_conn = nil
  stomp_connected = Completion.create
  StompClient.connect(8181) { |conn|
    stomp_conn = conn
    stomp_connected.complete
  }

  # Create and start the AMQP worker

  AmqpClient.create_client.connect{ |conn|
    conn.create_channel{ |chan|
      chan.declare_queue(AMQP_QUEUE, false, true, true) {
        chan.subscribe(AMQP_QUEUE, true) { |props, body|
          item = props.headers["item"].to_s
          comp = Composer.compose

          price = nil
          redis_get = redis_conn.get(item) { |val|
            price = val
          }

          stock = nil
          response_returned = stomp_conn.request(STOMP_DESTINATION, {"item" => item}, nil) { |headers, body|
            stock = headers["stock"]
          }

          comp.parallel(redis_connected, stomp_connected).
               parallel(redis_get, response_returned).
               then(Deferred.new{
                      props.headers["price"] = price
                      props.headers["stock"] = stock
                      chan.publish_with_props("", props.reply_to, props, nil)}).
               run
        }
      }
    }
  }
end

def stomp_worker

  # The STOMP worker consumes from the price queue and sends back the number of items in stock for the item

  StompClient.connect(8181) { |conn|
    conn.subscribe(STOMP_DESTINATION) { |headers, body|
      puts "Sending back number of items in stock for item #{headers["item"]}"
      headers["stock"] = rand(10).to_s
      conn.send_with_headers(headers["reply-to"], headers, nil)
    }
  }
end

puts "Starting composition example"

http_server()
amqp_worker()
stomp_worker()

STDIN.gets
