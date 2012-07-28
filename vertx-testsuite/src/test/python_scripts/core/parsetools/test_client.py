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

from test_utils import TestUtils
from core.parsetools import RecordParser
from core.buffer import Buffer

tu = TestUtils()

class ParseToolsTest(object):
    def test_delimited(self):
        str = ""
        iters = 100
        for i in range(0, iters):
            str += "line %s"% i
            if i != iters - 1:
                str += "\n" 

        self.lines = []
        def each_line(line):
            self.lines.append(line)
        parser = RecordParser.new_delimited("\n", each_line)
        
        parser.input(Buffer.create_from_str(str))

        count = 0
        for line in self.lines:    
            tu.azzert("line %i"% count == line.to_string())
            count += 1
        tu.test_complete()


def vertx_stop():
    tu.unregister_all()
    tu.app_stopped()

tu.register_all(ParseToolsTest())
tu.app_ready()
