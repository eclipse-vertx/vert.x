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

import vertx
from test_utils import TestUtils
from core.buffer import Buffer

tu = TestUtils()    

class BufferTest(object):

    def test_append_buff(self):
        buff_len = 100
        buff1 = self.create_buffer(buff_len)
        buff2 = Buffer.create()
        buff2.append_buffer(buff1)
        tu.azzert(buff_len == buff2.length, 'Invalid length')
        tu.test_complete()

    def test_append_fixnum_1(self):
        self.append_fixnum(1)

    def test_append_fixnum_2(self):
        self.append_fixnum(2)

    def test_append_fixnum_4(self):
        self.append_fixnum(4)

    def test_append_fixnum_8(self):
        self.append_fixnum(8)

    def append_fixnum(self, num_bytes):
        buff1 = Buffer.create()
        for i in range(-128,128):
            buff1.append_fixnum(i << ((num_bytes -1) * 8), num_bytes)

        for i in range(-128,128):
            val = buff1.get_fixnum((i + 128) * num_bytes, num_bytes)
            tu.azzert(val == i << ((num_bytes -1)* 8))

        tu.test_complete()

    def test_append_float_4(self):
        self.append_float(4)

    def test_append_float_8(self):
        self.append_float(8)

    def append_float(self, num_bytes):
        buff1 = Buffer.create()
        for i in range(0,100):
            buff1.append_float(i, num_bytes)

        for i in range(0,100):
            val = buff1.get_float(i * num_bytes, num_bytes)
            tu.azzert(val == i)
        tu.test_complete()

    def test_append_string_1(self):
        buff1 = Buffer.create()
        str = "piajdioasdioasdoiasdjiqjiqdjiqwjidqwid"
        buff1.append_str(str)
        tu.azzert(str == buff1.to_string())
        tu.test_complete()

    def test_append_string_2(self):
        buff1 = Buffer.create()
        str = "piajdioasdioasdoiasdjiqjiqdjiqwjidqwid"
        buff1.append_str(str, 'UTF-8')
        tu.azzert(str == buff1.to_string('UTF-8'))
        tu.test_complete()

    def test_set_fixnum_1(self):
        self.set_fixnum(1)

    def test_set_fixnum_2(self):
        self.set_fixnum(2)

    def test_set_fixnum_4(self):
        self.set_fixnum(4)

    def test_set_fixnum_8(self):
        self.set_fixnum(8)

    def set_fixnum(self, num_bytes):
        buff1 = Buffer.create()
        for i in range(-128,128):
            buff1.set_fixnum((i + 128) * num_bytes, i << ((num_bytes -1) * 8), num_bytes)
        for i in range(-128,128):
            val = buff1.get_fixnum((i + 128) * num_bytes, num_bytes)
            tu.azzert(val == i << ((num_bytes -1)* 8))
        tu.test_complete()

    def test_set_float_4(self):
        self.set_float(4)

    def test_set_float_8(self):
        self.set_float(8)

    def set_float(self, num_bytes):
        buff1 = Buffer.create()
        for i in range(0,100):
            buff1.set_float(i * num_bytes, i, num_bytes)
        
        for i in range(0,100):
            val = buff1.get_float(i * num_bytes, num_bytes)
            tu.azzert(val == i);

        tu.test_complete()

    def test_length(self):
        buff1 = Buffer.create()
        tu.azzert(buff1.length == 0)
        num = 50
        for i in range(0,num):
            buff1.append_fixnum(i, 1)
        tu.azzert(buff1.length == num, "Received %d expected %d"% (buff1.length, num))
        tu.test_complete()

    def test_copy(self):
        str = "iajdoiqwjdiqwdioqwdjiqwd"
        buff1 = Buffer.create(str)
        buff2 = buff1.copy()
        tu.azzert(buff1.length == buff2.length)
        for i in range(0,buff1.length):
            tu.azzert(buff1.get_byte(i) == buff2.get_byte(i))
        tu.test_complete()

    def test_create(self):
        buff1 = Buffer.create()
        tu.azzert(0 == buff1.length)
        buff2 = Buffer.create(100)
        tu.azzert(0 == buff1.length)
        str = "oqkdioqjwdijqwed"
        buff3 = Buffer.create_from_str(str)
        tu.azzert(str == buff3.to_string())
        tu.test_complete()

    def create_buffer(self, len):
        return TestUtils.gen_buffer(len)

def vertx_stop():
    tu.unregister_all()
    tu.app_stopped()

tu.register_all(BufferTest())
tu.app_ready()
