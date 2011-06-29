include Java
require "net"
require "stomp"
include Net, Stomp

class StompServer

  class Connection
    def initialize(sock)
      @sock = sock
    end
  end

  COMMAND_CONNECT = "CONNECT"
  COMMAND_SUBSCRIBE = "SUBSCRIBE"
  COMMAND_UNSUBSCRIBE = "UNSUBSCRIBE"
  COMMAND_SEND = "SEND"
  COMMAND_MESSAGE = "MESSAGE"

  HEADER_DESTINATION = "destination"

  def StompServer.create_server
    Net.create_server{ |sock|
      conn = Connection.new(sock)
      parser = Parser.new{ |frame|
        case frame.command
          when COMMAND_CONNECT
            dest = frame.headers.get(HEADER_DESTINATION)
        end
      }
      sock.data(record_parser)

    }
  end

end
