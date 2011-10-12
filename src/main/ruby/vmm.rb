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

include FileUtils

module Vmm

  class CLI

    def parse
      if ARGV.length == 0
        display_help
      else
        case ARGV[0]
          when 'install'
            install
          when 'repo'
          when 'list'
          when 'publish'
          when 'start'
          when 'stop'
          when 'uninstall'
          when 'unpublish'
          when 'search'
          else
            display_help
        end
      end
    end

    def display_help
      puts "Invalid argument(s):"
      ARGV.each do|a|
        puts "Argument: #{a}"
      end
      puts "Valid arguments are:"
      puts "    blah blah"
    end

    def install
      puts "Installing module"
      puts "argv length is #{ARGV.length}"

      if ARGV.length == 2
        dir = ARGV[1]

        if !File.exists? dir
          puts "Directory #{dir} does not exist"
        else
          puts "installing from directory #{dir}"

          json_fname = dir + "/module.json"
          if !File.exists? json_fname
            puts "Module directory must contain a module.json file"
          else
            file = File.open(json_fname, "rb")
            contents = file.read
            file.close
            begin
              obj = JSON.parse(contents)
            rescue
              puts "module.json file is invalid JSON"
              return
            end

            if check_module(obj, dir)
              puts "module is well formed"

              if check_vmm_dir
                install_module(dir, obj)
              end
            else
              puts "module not well formed"
            end


          end
        end
      end
    end

    VMM_ROOT = "./vmm-root/"

    def module_location(name, version)
      VMM_ROOT + name + "/" + version + "/"
    end

    def module_installed?(name, version)
      ml = module_location(name, version)
      File.exists?(ml) && File.directory?(ml)
    end

    def check_vmm_dir
      if !(File.exists?(VMM_ROOT) && File.directory?(VMM_ROOT))
        puts "VMM root: #{VMM_ROOT} does not exist or is not a directory"
        false
      else
        true
      end
    end

    def verify_installed(name, version)

    end

    def install_module(dir, json)

      deps_hash = json["dependencies"]
      deps_hash.each do |name, version|

      end

      dest = module_location(json["name"], json["version"])
      if File.exists? dest
        puts "Module already installed"
      else
        mkdir_p dest
        cp_r(dir + "/.", dest)
        puts "Installed module #{json["name"]}"
      end
    end

    def validate_version(version)

    end

    def validate_name(name)

    end



    def check_module(json, dir)
      # Check mandatory fields
      if json["name"] == nil
        err = "name"
      elsif json["description"] == nil
        err = "description"
      elsif json["version"] == nil
        err = "version"
      elsif json["type"] == nil
        err = "type"
      end
      if err != nil
        puts err + " field missing from module.json"
        false
      else
        main = json["main"]
        if main != nil
          # Check main exists
          case json["type"]
            when "ruby"
              if !File.exists? dir + "/" + main
                puts "Main script #{main} must be at top level of module"
                false
              else
                true
              end
            else
              puts "Invalid module type " + json["type"]
              false
          end
        end
      end
    end

  end



end

puts "in vmm"

vmm = Vmm::CLI.new
vmm.parse

