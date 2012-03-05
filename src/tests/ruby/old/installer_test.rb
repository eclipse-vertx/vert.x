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
require 'addons.old.vmm/installer.rb'
require 'fileutils'
require 'json'

include FileUtils
include Vmm

# @author {http://tfox.org Tim Fox}
class InstallerTest < Test::Unit::TestCase

  TEST_OUTPUT = "ruby-test-output"

  VMM_DIR = TEST_OUTPUT + "/addons.old.vmm-root"

  def setup
    rm_r TEST_OUTPUT if File.exists? TEST_OUTPUT
    mkdir TEST_OUTPUT
    mkdir_p VMM_DIR
    @installer = Installer.new(VMM_DIR)
  end

  def teardown
    rm_r VMM_DIR
  end

  def test_install_with_no_deps
    desc = create_descriptor("module1", "v6.5", "ruby", "main.rb", "module1", nil)
    create_module_dir("module1", desc, "main.rb")
    assert_install("module1")
  end

  def test_install_with_no_deps_no_main
    desc = create_descriptor("module1", "v6.5", "ruby", nil, "module1", nil)
    create_module_dir("module1", desc)
    assert_install("module1")
  end

  def test_install_with_deps_ok
    desc1 = create_descriptor("module1", "v0.1", "ruby", nil, "module1", nil)
    create_module_dir("module1", desc1)

    desc2 = create_descriptor("module2", "v1.3", "ruby", nil, "module2", nil)
    create_module_dir("module2", desc2)

    deps = {"module1" => "v0.1", "module2" => "v1.3"}
    desc3 = create_descriptor("module3", "v6.5", "ruby", nil, "module3", deps)
    create_module_dir("module3", desc3)

    assert_install("module1")
    assert_install("module2")
    assert_install("module3")
  end

  def test_install_with_deps_which_bring_in_different_versions

    desc4_1 = create_descriptor("module4", "v0.1", "ruby", nil, "module4", nil)
    create_module_dir("module4_1", desc4_1)

    desc4_2 = create_descriptor("module4", "v0.2", "ruby", nil, "module4", nil)
    create_module_dir("module4_2", desc4_2)


    deps2 = {"module4" => "v0.1"}
    desc2 = create_descriptor("module2", "v0.1", "ruby", nil, "module2", deps2)
    create_module_dir("module2", desc2)

    deps3 = {"module4" => "v0.2"}
    desc3 = create_descriptor("module3", "v0.1", "ruby", nil, "module3", deps3)
    create_module_dir("module3", desc3)

    deps1 = {"module2" => "v0.1", "module3" => "v0.1"}
    desc1 = create_descriptor("module1", "v6.5", "ruby", nil, "module1", deps1)
    create_module_dir("module1", desc1)

    assert_install("module4_1")
    assert_install("module4_2")

    assert_install("module2")
    assert_install("module3")

    assert_install("module1", Installer::ERR_DEPENDENCY_VERSION_MISMATCH)
  end

  def test_install_with_missing_dep
    desc1 = create_descriptor("module1", "v0.1", "ruby", nil, "module1", nil)
    create_module_dir("module1", desc1)

    deps = {"module1" => "v0.1", "module2" => "v1.3"}
    desc3 = create_descriptor("module3", "v6.5", "ruby", nil, "module3", deps)
    create_module_dir("module3", desc3)

    assert_install("module1")
    assert_install("module3", Installer::ERR_DEPENDENCIES_MISSING)
  end

  def test_install_with_all_missing_deps
    deps = {"module2" => "v0.1", "module3" => "v1.3"}
    desc = create_descriptor("module1", "v6.5", "ruby", nil, "module1", deps)
    create_module_dir("module1", desc)
    assert_install("module1", Installer::ERR_DEPENDENCIES_MISSING)
  end

  def test_already_installed
    desc = create_descriptor("module1", "v6.5", "ruby", nil, "module1", nil)
    create_module_dir("module1", desc)
    assert_install("module1")

    # Now try and install same name different version, should be ok
    desc = create_descriptor("module1", "v6.6", "ruby", nil, "module1", nil)
    create_module_dir("module1-2", desc)
    assert_install("module1-2")

    # Now try and install same name, same version, should fail
    create_module_dir("module1-3", desc)
    assert_install("module1-3", Installer::ERR_ALREADY_INSTALLED)
  end

  def test_no_directory
    assert_install("doesn't exist", Installer::ERR_MODULE_DIR_MISSING)
  end

  def test_no_descriptor
    create_module_dir("module1", nil)
    assert_install("module1", Installer::ERR_DESCRIPTOR_MISSING)
  end

  def test_invalid_json
    create_module_dir("module1", nil)
    f = File.new(TEST_OUTPUT + "/module1/module.json", "w")
    f.write("qoiwjdoqiwjd")
    f.close
    assert_install("module1", Installer::ERR_INVALID_JSON)
  end

  def test_name_missing
    desc = create_descriptor(nil, "v6.5", "ruby", nil, "module1", nil)
    create_module_dir("module1", desc)
    assert_install("module1", Installer::ERR_NAME_MISSING)
  end

  def test_version_missing
    desc = create_descriptor("module1", nil, "ruby", nil, "module1", nil)
    create_module_dir("module1", desc)
    assert_install("module1", Installer::ERR_VERSION_MISSING)
  end

  def test_description_missing
    desc = create_descriptor("module1", "v6.5", "ruby", nil, nil, nil)
    create_module_dir("module1", desc)
    assert_install("module1", Installer::ERR_DESCRIPTION_MISSING)
  end

  def test_type_missing
    desc = create_descriptor("module1", "v6.5", nil, nil, "module1", nil)
    create_module_dir("module1", desc)
    assert_install("module1", Installer::ERR_TYPE_MISSING)
  end

  def test_invalid_type
    desc = create_descriptor("module1", "v6.5", "cheesecake", nil, "module1", nil)
    create_module_dir("module1", desc)
    assert_install("module1", Installer::ERR_INVALID_TYPE)
  end

  def test_main_script_missing
    desc = create_descriptor("module1", "v6.5", "ruby", "main.rb", "module1", nil)
    create_module_dir("module1", desc)
    assert_install("module1", Installer::ERR_MAIN_SCRIPT_MISSING)
  end




  def install_from_dir(dir)
    @installer.install_from_dir(TEST_OUTPUT + "/" + dir)
  end

  def assert_install(dir, error_code = 0)
    install_from_dir(dir)
    assert(@installer.error_code == error_code, "Expected: #{error_code} Actual: #{@installer.error_code}")
  end

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