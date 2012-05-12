require "vertx"
include Vertx

HttpServer.new.request_handler do |req|
  # req.response.end
  # req.response.send_file("httpperf/foo.html")
  FileSystem.read_file_as_buffer("httpperf/foo.html") { |err, file|
    req.response.headers["Content-Length"] = file.length
    req.response.headers["Content-Type"] = "text/html"
    req.response.write_buffer(file)
    req.response.end
  }
end.listen(8080)
