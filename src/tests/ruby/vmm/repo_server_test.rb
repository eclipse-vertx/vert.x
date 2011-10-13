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
require 'vmm/repo_server.rb'
require 'fileutils'
require 'utils'

include VertxRepo
include FileUtils

# @author {http://tfox.org Tim Fox}
class RepoServerTest < Test::Unit::TestCase

  TEST_OUTPUT = "ruby-test-output"

  REPO_DIR = TEST_OUTPUT + "/repo-root"

  def setup
    rm_r TEST_OUTPUT if File.exists? TEST_OUTPUT
    mkdir TEST_OUTPUT
    mkdir_p REPO_DIR
  end

  def teardown
    # rm_r REPO_DIR
  end

  def test_1

    json = create_descriptor("mymodule", "v0.1", "ruby", "main.rb", "desc", nil)
    create_module_dir("module1", json, "main.rb")
    abs_dir = File.expand_path(TEST_OUTPUT)
    cmd = "tar -zcf #{abs_dir}/foo.tar.gz -C #{abs_dir} module1"
    puts "cmd is #{cmd}"
    check = system(cmd)
    puts "result of tar is #{check}"

    latch = Utils::Latch.new(1)


    Vertx::go do

      server = RepoServer.new(REPO_DIR)

      client = HttpClient.new
      client.port = 8080
      client.host = "localhost"
      req = client.put("/default/acme-widget/v0.3/") do |resp|
        puts "Got response #{resp.status_code}"
        assert 200 == resp.status_code

        latch.countdown
      end

      req.put_header("Content-Length", File.size(TEST_OUTPUT + "/foo.tar.gz"))

      FileSystem::open(TEST_OUTPUT + "/foo.tar.gz").handler do |future|
        if future.succeeded?
          puts "succeeded in opening file"
          rs = future.result.read_stream
          pump = Pump.new(rs, req)
          pump.start
          rs.end_handler { req.end; puts "sent file" }
        end
      end

    end

    puts "sleeping"
    assert latch.await(5)

  end

  # TODO put these in common base class

  def create_descriptor(name, version, type, main, description, deps)
    json = {}
    json["name"] = name if name
    json["version"] = version if version
    json["type"] = type if type
    json["main"] = main if main
    json["description"] = description if description
    json["dependencies"] = deps if deps
    json
  end

  def create_module_dir(dir, descriptor, *files)

    dir = TEST_OUTPUT + "/" + dir

    puts "Creating directory #{dir}"

    mkdir_p dir
    if descriptor != nil
      f = File.new(dir + "/module.json", "w")
      f.write(descriptor.to_json)
      f.close
    end
    files.each do |file|
      f = File.new(dir + "/" + file, "w")
      f.write("blahiqwjdioqwjdioqwjdijoqwd")
      f.close
    end
  end

end