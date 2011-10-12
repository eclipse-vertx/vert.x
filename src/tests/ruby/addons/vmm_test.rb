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
require 'vmm/installer.rb'
require 'fileutils'
require 'json'

include FileUtils
include Vmm


# @author {http://tfox.org Tim Fox}
class VmmTest < Test::Unit::TestCase

  TEST_OUTPUT = "ruby-test-output"

  VMM_DIR = TEST_OUTPUT + "/vmm-root"

  def setup
    rm_r TEST_OUTPUT if File.exists? TEST_OUTPUT
    mkdir TEST_OUTPUT
    mkdir_p VMM_DIR
  end

  def teardown
    rm_r VMM_DIR
  end

  def test_simple

    installer = Installer.new(VMM_DIR)

    desc1 = create_descriptor("module1", "v0.1", "ruby", nil, "description1", nil)
    module_dir1 = TEST_OUTPUT + "/module1"
    create_module_dir(module_dir1, desc1, "main.rb")

    installer.install_from_dir(module_dir)
    installer.report_errors
    assert(installer.errors.empty?)

  end

  def test_deps1

    installer = Installer.new(VMM_DIR)

    desc1 = create_descriptor("module1", "v0.1", "ruby", nil, "module1", nil)
    module_dir1 = TEST_OUTPUT + "/module1"
    create_module_dir(module_dir1, desc1)

    desc2 = create_descriptor("module2", "v1.3", "ruby", nil, "module2", nil)
    module_dir2 = TEST_OUTPUT + "/module2"
    create_module_dir(module_dir2, desc2)

    deps = {"module1" => "v0.1", "module2" => "v1.3"}
    desc3 = create_descriptor("module3", "v6.5", "ruby", nil, "module3", deps)
    module_dir3 = TEST_OUTPUT + "/module3"
    create_module_dir(module_dir3, desc3)


    installer.install_from_dir(module_dir1)
    installer.report_errors
    assert(installer.errors.empty?)

    installer.install_from_dir(module_dir2)
    installer.report_errors
    assert(installer.errors.empty?)

    installer.install_from_dir(module_dir3)
    installer.report_errors
    assert(installer.errors.empty?)

  end

  def create_descriptor(name, version, type, main, description, deps)
    json = {}
    json["name"] = name
    json["version"] = version
    json["type"] = type
    json["main"] = main if main
    json["description"] = description
    json["dependencies"] = deps if deps
    json
  end

  def create_module_dir(dir, descriptor, *files)
    puts "Creating directory #{dir}"

    mkdir_p dir
    f = File.new(dir + "/module.json", "w")
    f.write(descriptor.to_json)
    f.close
#    files.each do |file|
#      f = File.new(dir + "/" + file)
#      f.write("blahiqwjdioqwjdioqwjdijoqwd")
#      f.close
#    end
  end

end