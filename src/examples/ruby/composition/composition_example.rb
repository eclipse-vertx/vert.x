# Copyright 2002-2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use
# this file except in compliance with the License. You may obtain a copy of the
# License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

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

# This somewhat elaborate example shows an interaction including HTTP, STOMP, AMQP and Redis
#
# Summary: We have a website which allows the user to check the price and stock count for inventory items.
#
# It contains the following components (In real life these would probably be running on different nodes).
#
# 1) HTTP server. We create an HTTP server which serves the index.html page from disk, and responds to HTTP requests for an item.
# when it receives a request it uses the request-response pattern to send that request in an AMQP message to a queue
# and sets a handler for the response.
# When the response returns it formats the price and stock count in the html page returned to the browser.
#
# 2) AMQP consumer. We create an AMQP consumer that consumes from the AMQP queue, and then does two things in parallel
# a) Call redis to get the price for the item
# b) Send a STOMP message, using the request-response pattern to a STOMP destination to request the stock count
# When both a) and b) asynchronously complete, we return an AMQP message as the response to the HTTP server that made
# the request
#
# 3) A redis server
#
# 4) A STOMP server
#
# 5) STOMP consumer. We create a STOMP consumer that subscribes to the STOMP destination, calculates a stock count and
# sends that back in a response message

AMQP_QUEUE = "availability"
STOMP_DESTINATION = "availability-request"

def http_server
  channel_pool = ChannelPool.create_pool
  HttpServer.create_server { |conn|
    conn.request { |req, resp|
      puts "Request uri is #{req.uri}"
      if req.uri == "/"
        puts "Serving index page"
        # Serve the main page
        FileSystem.read_file("index.html") { |data|
          resp.write_buffer(data)
          resp.end
        }
      elsif req.uri.start_with? "/submit"
        # Handle the request for the item
        item = req.get_param("item")
        channel_pool.get_channel { |chan|
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

def amqp_worker

  #First we need to setup the connections and add some reference data

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

  # We need to make sure we are connected before we can do anything
  Composer.compose.when(redis_connected, stomp_connected).when { do_amqp_worker(redis_conn, stomp_conn) }.end
end

def do_amqp_worker(redis_conn, stomp_conn)

  # Create and start the AMQP worker

  AmqpClient.create_client.connect { |conn|
    conn.create_channel { |chan|
      chan.declare_queue(AMQP_QUEUE, false, true, true) {
        chan.subscribe(AMQP_QUEUE, true) { |props, body|
          item = props.headers["item"].to_s
          comp = Composer.compose

          # Get the price from redis
          price = nil
          redis_get = redis_conn.get(item) { |val|
            price = val
          }

          # Get the stock from the STOMP worker (request-response pattern)
          stock = nil
          response_returned = stomp_conn.request(STOMP_DESTINATION, {"item" => item}, nil) { |headers, body|
            stock = headers["stock"]
          }

          comp.when(redis_get, response_returned).# Get price and stock information
              when { props.headers["price"] = price # Format response message with info before sending
          props.headers["stock"] = stock
          chan.publish_with_props("", props.reply_to, props, nil) }.end
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
