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

require "vertx"
include Vertx

NetClient.new.connect(1234, "localhost") do |socket|
  socket.data_handler { |data| puts "Echo client received #{data.to_s}" }
  (1..10).each do |i|
    str = "hello #{i}\n"
    puts "Echo client sending #{str}"
    socket.write_buffer(Buffer.create_from_str(str))
  end
end





