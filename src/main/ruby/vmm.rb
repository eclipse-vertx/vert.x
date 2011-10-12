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
      puts "Installing package"
      puts "argv length is #{ARGV.length}"

      if ARGV.length == 2
        dir = ARGV[1]

        if !File.exists? dir
          puts "Directory #{dir} does not exist"
        else
          puts "installing from directory #{dir}"

          json_fname = dir + "/module.json"
          if !File.exists? json_fname
            puts "Package directory must contain a module.json file"
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

            if check_json(obj)
              puts "module.json is well formed"

            end


          end
        end
      end
    end

    def check_json(json)
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
        true
      end
    end

  end



end

puts "in vmm"

vmm = Vmm::CLI.new
vmm.parse

