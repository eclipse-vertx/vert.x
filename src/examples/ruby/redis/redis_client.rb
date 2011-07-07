require "redis"
include Redis

Client.create_client.connect(6379, "localhost") { |conn|
  conn.set("foo", "bar") {
    conn.get("foo") { |val|
      puts "value for foo is #{val}"
      conn.close
    }
  }
}

STDIN.gets
