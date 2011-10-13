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

require 'rubygems'
require 'json'
require 'fileutils'
require 'set'

include FileUtils

module Vmm

  # @author {http://tfox.org Tim Fox}
  class Installer

    ERR_VMM_ROOT_MISSING = 1
    ERR_MODULE_DIR_MISSING = 2
    ERR_DESCRIPTOR_MISSING = 3
    ERR_INVALID_JSON = 4
    ERR_NAME_MISSING = 5
    ERR_VERSION_MISSING = 6
    ERR_TYPE_MISSING = 7
    ERR_INVALID_TYPE = 8
    ERR_INVALID_NAME = 9
    ERR_INVALID_VERSION = 10
    ERR_DESCRIPTION_MISSING = 11
    ERR_MAIN_SCRIPT_MISSING = 12
    ERR_ALREADY_INSTALLED = 13
    ERR_DEPENDENCIES_MISSING = 14
    ERR_DEPENDENCY_VERSION_MISMATCH = 15

    VALID_TYPES = Set.new ["java", "ruby"]

    def initialize(vmm_root)
      @vmm_root = vmm_root
      @error_code = 0
    end

    def error_code
      @error_code
    end

    def error_msg
      @error_msg
    end

    def set_err(error_code, err_msg)
      @error_code = error_code
      @error_msg = err_msg == nil ? lookup_msg(error_code) : err_msg
    end

    def install_from_dir(module_dir)

      puts "Installing module from directory #{module_dir}"

      if !check_dir(@vmm_root)
        set_err(ERR_VMM_ROOT_MISSING, "vmm root directory #{@vmm_root} is missing")
        return
      end

      if !check_dir(module_dir)
        set_err(ERR_MODULE_DIR_MISSING, "module directory #{module_dir} is missing")
        return
      end

      if (!validate_module(module_dir))
        return
      end

      descriptor = load_module_descriptor(module_dir + "/")
      deps = {}
      if obtain_dependencies(descriptor, deps)
        name = descriptor["name"]
        version = descriptor["version"]
        dest = module_location(name, version)
        if !File.exists? dest
          mkdir_p dest
          cp_r(module_dir + "/.", dest)
          puts "Successfully installed module #{name}:#{version}"
        else
          set_err(ERR_ALREADY_INSTALLED, "module #{name}:#{version} is already installed")
        end
      end
    end

    def check_dir(dir)
      File.exists?(dir) && File.directory?(dir)
    end

    def validate_module(dir)
      descriptor_fname = dir + "/module.json"
      if !File.exists? descriptor_fname
        set_err(ERR_DESCRIPTOR_MISSING, "module descriptor module.json is missing")
        return
      end

      file = File.open(descriptor_fname, "rb")
      contents = file.read
      file.close
      begin
        json = JSON.parse(contents)
      rescue
        set_err(ERR_INVALID_JSON, "module.json contains invalid JSON")
        return
      end

      name = json["name"]
      version = json["version"]

      # Check mandatory fields
      if name == nil
        @error_code = ERR_NAME_MISSING
        field = "name"
      elsif json["description"] == nil
        @error_code = ERR_DESCRIPTION_MISSING
        field = "description"
      elsif version == nil
        @error_code = ERR_VERSION_MISSING
        field = "version"
      elsif json["type"] == nil
        @error_code = ERR_TYPE_MISSING
        field = "type"
      end
      if @error_code != 0
        @error_msg = "mandatory #{field} field missing from module.json"
        return
      end

      if !validate_name(name)
        set_err(ERR_INVALID_NAME, "Module name #{name} is invalid")
        return
      end

      if !validate_version(version)
        set_err(ERR_INVALID_VERSION, "Module version #{version} is invalid")
        return
      end

      type = json["type"]
      if !VALID_TYPES.member? type
        set_err(ERR_INVALID_TYPE, "Invalid module type #{type}")
        return
      end

      main = json["main"]
      if main != nil
        # Check main exists
        case type
          when "ruby"
            if !File.exists? dir + "/" + main
              set_err(ERR_MAIN_SCRIPT_MISSING, "Main script #{main} referenced in module.json does mot exist in module")
              return
            end
        end
      end
      true
    end

    def obtain_dependencies(json, deps)
      name = json["name"]
      version = json["version"]
      existing = deps[name]
      if existing != nil
        if existing["version"] == version
          # Already traversed this so ignore
          true
        else
          # More than one version of same module
          set_err(ERR_DEPENDENCY_VERSION_MISMATCH, "Dependency graph contains two versions of same module: " +
                                                   "#{name} versions: #{existing["version"]}, #{version}")
          false
        end
      else
        deps[name] = json
        json_deps = json["dependencies"]
        if json_deps != nil
          json_deps.each do |name, version|
            json2 = get_module_descriptor(name, version)
            if json2 == nil
              set_err(ERR_DEPENDENCIES_MISSING, "dependency #{name}:#{version} is not installed")
              return false
            end
            if !obtain_dependencies(json2, deps)
              return false
            end
          end
        end
        true
      end
    end

    def validate_name(name)
      # TODO
      true
    end

    def validate_version(version)
      # TODO
      true
    end

    def module_location(name, version)
      @vmm_root + name + "/" + version + "/"
    end

    # Get module descriptor for installed pkg
    def get_module_descriptor(name, version)
      ml = module_location(name, version)
      load_module_descriptor(ml)
    end

    def load_module_descriptor(module_location)
      if File.exists? module_location
        json_fname = module_location + "module.json"
        file = File.open(json_fname, "rb")
        contents = file.read
        file.close
        JSON.parse(contents)
      else
        nil
      end
    end

  end

end
