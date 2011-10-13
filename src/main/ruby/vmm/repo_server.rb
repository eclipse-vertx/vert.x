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
require 'vertx'
require 'json'

include Vertx

module VertxRepo

  # @author {http://tfox.org Tim Fox}
  class RepoServer

    def initialize(repo_root)

      @repo_root = repo_root

      server = HttpServer.new
      matcher = RouteMatcher.new

      matcher.get("/:reponame/:modulename/:version/") do |req|

        md = module_dir(req.params['reponame'], req.params['modulename'], req.params['version'])

        if !File.exists?(md)
          req.response.status_code = 404
          req.response.end
        else
          file = File.new(md)
          json = JSON.parse(file.read)
          file.close
          puts "Got json #{json}"
          req.response.put_header("Content-Type", "application/json").write_str(json).end
        end

      end

      matcher.put("/:reponame/:modulename/:version/") do |req|

        md = module_dir(req.params['reponame'], req.params['modulename'], req.params['version'])

        if !File.exists?(md)

          # Stream the body direct to file

          req.pause

          fname = md + "/foo.tar.gz"

          future = FileSystem::open(fname)
          future.handler{
            if (future.succeeded?)
              file = future.result
              pump = Pump.new(req, file)
              pump.start
              req.end_handler do
                file.close{
                  fnam_abs = File.absolute_path(fname)
                  puts "absolute path is #{fnam_abs}"
                  # Now unpack the file on the file system
                  cmd = "tar -zxf #{fnam_abs}"
                  check = system(cmd)
                  puts "return value from system is #{check}"
                }

              end
            else
              puts "Failed to open file #{future.exception}"
              req.response.status_code = 500;
              req.response.end
            end

          }

#          # Read the body
#          buff = Buffer.create(0)
#          req.data_handler{ |data| data.append_buffer(buff)}
#          req.end_handler do
#            file = File.new
#            # Unpack it on the file system
#
#            cmd = "tar -zxf"
#          end

        else
          # TODO update
          req.response.status_code = 405
          req.response.end
        end

      end

      server.request_handler(matcher).listen(8080)
    end

    def module_dir(repo_name, module_name, version)
      "#{@repo_root}/#{module_name}/#{version}"
    end

  end

end

