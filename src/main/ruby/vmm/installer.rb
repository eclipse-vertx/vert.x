
require 'rubygems'
require 'json'
require 'fileutils'

include FileUtils

module Vmm

  class Installer

    VMM_ROOT = "./vmm-root/"

    def initialize
      @errors = []
    end

    def errors
      @errors
    end

    def install_from_dir(module_dir)

      puts "Installing module from directory #{module_dir}"

      if !check_dir(VMM_ROOT)
        return
      end

      if !check_dir(module_dir)
        return
      end

      if (!validate_module(module_dir))
        return
      end

      descriptor = load_module_descriptor(module_dir + "/")
      deps = {}
      if obtain_dependencies(descriptor, deps)
        dest = module_location(descriptor["name"], descriptor["version"])
        if !File.exists? dest
          mkdir_p dest
          cp_r(module_dir + "/.", dest)
          puts "Installed module #{descriptor["name"]}"
        else
          @errors << "Module is already installed"
        end
      end
    end

    def check_dir(dir)
      if !File.exists?(dir)
        @errors << "#{dir} does not exist"
      elsif !File.directory?(dir)
        @errors << "#{dir} is not a directory"
      else
        return true
      end
      false
    end

    def validate_module(dir)
      descriptor_fname = dir + "/module.json"
      if !File.exists? descriptor_fname
        @errors << "Module directory must contain a module.json file"
        return
      end

      file = File.open(descriptor_fname, "rb")
      contents = file.read
      file.close
      begin
        json = JSON.parse(contents)
      rescue
        @errors << "module.json file is invalid JSON"
        return
      end

      name = json["name"]
      version = json["version"]

      # Check mandatory fields
      if name == nil
        err = "name"
      elsif json["description"] == nil
        err = "description"
      elsif version == nil
        err = "version"
      elsif json["type"] == nil
        err = "type"
      end
      if err != nil
        @errors << err + " field missing from module.json"
        return
      end

      if !validate_name(name)
        return
      end

      if !validate_version(version)
        return
      end

      main = json["main"]
      if main != nil
        # Check main exists
        case json["type"]
          when "ruby"
            if !File.exists? dir + "/" + main
              @errors << "Main script #{main} must be at top level of module"
              false
            else
              true
            end
          else
            @errors << "Invalid module type " + json["type"]
            false
        end
      end
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
          @errors << "Dependency graph contains two versions of same module: #{name} versions: #{existing["version"]}, #{version}"
          false
        end
      else
        deps[name] = json
        json_deps = json["dependencies"]
        if json_deps != nil
          json_deps.each do |name, version|
            json2 = get_module_descriptor(name, version)
            if json2 == nil
              @errors << "dependency #{name}:#{version} is not installed."
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
      VMM_ROOT + name + "/" + version + "/"
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
