# Copyright 2011 VMware, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'nodex'
include Java
include Nodex

module Utils
  class Latch
    def initialize(cnt)
      @j_latch = java.util.concurrent.CountDownLatch.new(cnt)
    end

    def await(secs)
      @j_latch.await(secs, java.util.concurrent.TimeUnit::SECONDS)
    end

    def countdown
      @j_latch.countDown
    end
  end

  def Utils.gen_buffer(size)
    j_buff = org.nodex.tests.Utils.generateRandomBuffer(size)
    Buffer.new(j_buff)
  end

  def Utils.buffers_equal(buff1, buff2)
    puts "buff1 length #{buff1.length} buff2 length #{buff2.length}"
    org.nodex.tests.Utils.buffersEqual(buff1._to_java_buffer, buff2._to_java_buffer)
  end
end