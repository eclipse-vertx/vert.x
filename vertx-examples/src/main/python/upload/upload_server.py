# Copyright 2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import random
import string
from datetime import datetime

import vertx
from core.file_system import FileSystem
from core.streams import Pump

server = vertx.create_http_server()

@server.request_handler
def request_handler(req):
    req.pause()

    filename = ''
    for i in range(10):
        filename += string.uppercase[random.randrange(26)]
    filename += '.uploaded'

    print "Got request storing in %s"% filename

    def file_open(err, file):
        pump = Pump(req, file.write_stream)
        start_time = datetime.now()

        def end_handler(stream):
            def file_close(err, file):
                end_time = datetime.now()
                print "Uploaded %d bytes to %s in %s"%(pump.bytes_pumped, filename, end_time-start_time)
                req.response.end()
            file.close(file_close)
        req.end_handler(end_handler)
        pump.start()
        req.resume()

    FileSystem.open(filename, handler=file_open)

server.listen(8080)