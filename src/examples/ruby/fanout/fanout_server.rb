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

conns = SharedData::get_set("conns")

NetServer.new.connect_handler do |socket|
  conns.add(socket.write_handler_id)
  socket.data_handler do |data|
    conns.each { |address| EventBus::send(address, data) }
  end
  socket.closed_handler { conns.delete(socket.write_handler_id) }
end.listen(1234)


