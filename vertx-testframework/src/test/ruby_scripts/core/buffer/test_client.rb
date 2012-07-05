# Copyright 2011-2012 the original author or authors.
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
require "test_utils"

@tu = TestUtils.new

def test_append_buff
  buff_len = 100
  buff1 = create_buffer(buff_len)
  buff2 = Buffer.create
  buff2.append_buffer(buff1)
  @tu.azzert(buff_len == buff2.length, 'Invalid length')
  @tu.test_complete
end

def test_append_fixnum_1
  append_fixnum(1)
end

def test_append_fixnum_2
  append_fixnum(2)
end

def test_append_fixnum_4
  append_fixnum(4)
end

def test_append_fixnum_8
  append_fixnum(8)
end

def append_fixnum(num_bytes)
  buff1 = Buffer.create
  for i in -128..127
    buff1.append_fixnum(i << ((num_bytes -1) * 8), num_bytes)
  end
  for i in -128..127
    val = buff1.get_fixnum((i + 128) * num_bytes, num_bytes)
    @tu.azzert(val == i << ((num_bytes -1)* 8))
  end
  @tu.test_complete
end

def test_append_float_4
  append_float(4)
end

def test_append_float_8
  append_float(8)
end

def append_float(num_bytes)
  buff1 = Buffer.create
  for i in 0..99
    buff1.append_float(i, num_bytes)
  end
  for i in 0..99
    val = buff1.get_float(i * num_bytes, num_bytes)
    @tu.azzert(val == i);
  end
  @tu.test_complete
end

def test_append_string_1
  buff1 = Buffer.create
  str = "piajdioasdioasdoiasdjiqjiqdjiqwjidqwid"
  buff1.append_str(str)
  @tu.azzert(str == buff1.to_s)
  @tu.test_complete
end

def test_append_string_2
  buff1 = Buffer.create
  str = "piajdioasdioasdoiasdjiqjiqdjiqwjidqwid"
  buff1.append_str(str, 'UTF-8')
  @tu.azzert(str == buff1.to_s('UTF-8'))
  @tu.test_complete
end


def test_set_fixnum_1
  set_fixnum(1)
end

def test_set_fixnum_2
  set_fixnum(2)
end

def test_set_fixnum_4
  set_fixnum(4)
end

def test_set_fixnum_8
  set_fixnum(8)
end

def set_fixnum(num_bytes)
  buff1 = Buffer.create
  for i in -128..127
    buff1.set_fixnum((i + 128) * num_bytes, i << ((num_bytes -1) * 8), num_bytes)
  end
  for i in -128..127
    val = buff1.get_fixnum((i + 128) * num_bytes, num_bytes)
    @tu.azzert(val == i << ((num_bytes -1)* 8))
  end
  @tu.test_complete
end

def test_set_float_4
  set_float(4)
end

def test_set_float_8
  set_float(8)
end

def set_float(num_bytes)
  buff1 = Buffer.create
  for i in 0..99
    buff1.set_float(i * num_bytes, i, num_bytes)
  end
  for i in 0..99
    val = buff1.get_float(i * num_bytes, num_bytes)
    @tu.azzert(val == i);
  end
  @tu.test_complete
end

def test_length
  buff1 = Buffer.create
  @tu.azzert(buff1.length == 0)
  num = 50
  for i in 0..num - 1
    buff1.append_fixnum(i, 1)
  end
  @tu.azzert(buff1.length == num)
  @tu.test_complete
end

def test_copy
  str = "iajdoiqwjdiqwdioqwdjiqwd"
  buff1 = Buffer.create(str)
  buff2 = buff1.copy
  @tu.azzert(buff1.length == buff2.length)
  for i in 0..buff1.length - 1
    @tu.azzert(buff1.get_byte(i) == buff2.get_byte(i))
  end
  @tu.test_complete
end

def test_create
  buff1 = Buffer.create
  @tu.azzert(0 == buff1.length)
  buff2 = Buffer.create(100)
  @tu.azzert(0 == buff1.length)
  str = "oqkdioqjwdijqwed"
  buff3 = Buffer.create_from_str(str)
  @tu.azzert(str == buff3.to_s)
  @tu.test_complete
end


def create_buffer(len)
  TestUtils.gen_buffer(len)
end

def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
