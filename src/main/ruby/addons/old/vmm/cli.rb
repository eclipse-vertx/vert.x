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

require 'addons.old.vmm/installer'

module Vmm

  # @author {http://tfox.org Tim Fox}
  class CLI

    VMM_ROOT = "./addons.old.vmm-root/"

    def parse
      if ARGV.length == 0
        display_help
      else
        case ARGV[0]
          when 'install'
            parse_install
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

    def parse_install
      if ARGV.length != 2
        display_help
      else
        module_dir = ARGV[1]
        installer = Installer.new(VMM_ROOT)
        installer.install_from_dir(module_dir)
        installer.errors.each { |error| puts "Error: #{error}" }
      end
    end

    def display_help
      puts "Invalid argument(s):"
      ARGV.each do|a|
        puts "Argument: #{a}"
      end
      puts "Usage:"
      puts "  addons.old.vmm install DIRECTORY"
      puts "Installs a module into the local repository"
      puts "DIRECTORY is the name of a directory which contains a valid vert.x module"
    end

  end

end

Vmm::CLI.new.parse

