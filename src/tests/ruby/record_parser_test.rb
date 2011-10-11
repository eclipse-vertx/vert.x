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

require 'test/unit'
require 'vertx'
require 'utils'
include Vertx

class RecordParserTest < Test::Unit::TestCase

  def test_delimited

    str = ""
    iters = 100
    for i in 0..iters - 1 do
      str << "line #{i}"
      str << "\n" if i != iters - 1
    end

    lines = []
    parser = RecordParser::new_delimited("\n") { |line|
      lines << line
    }

    parser.input(Buffer.create_from_str(str))

    count = 0
    lines.each { |line|
      assert("line #{count}" == line.to_s)
      count += 1
    }
  end

end