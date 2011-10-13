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
require 'fileutils'

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

        puts "got a put"

        md = module_dir(req.params['reponame'], req.params['modulename'], req.params['version'])

        if !File.exists?(md)

          #TODO use file system for this
          mkdir_p md

          puts "File does not exist"

          # Stream the body direct to file

          req.pause
          puts "paused in ruby"

          arch_name = "foo.tar.gz"
          fname = md + "/" + arch_name

          future = FileSystem::open(fname)
          future.handler do
            if (future.succeeded?)
              puts "Opened file for writing"
              file = future.result
              ws = file.write_stream
              req.data_handler do |data|
                puts "writing data"
                ws.write_buffer(data)
              end

              req.end_handler do
                puts "closing file"
                file.close.handler do
                  puts "file closed"
                  dir_abs = File.expand_path(md)
                  puts "absolute path is #{dir_abs}"
                  # Now unpack the file on the file system
                  # TODO this should be done using a core gzip stream
                  # or using  background task
                  cmd = "tar -zxf #{dir_abs}/#{arch_name} -C #{dir_abs}"
                  check = system(cmd)
                  puts "return value from system is #{check}"
                  req.response.end
                end
              end
              req.resume
              puts "resumed"
            else
              puts "Failed to open file #{future.exception}"
              req.response.status_code = 500;
              req.response.end
            end

          end

        else
          # TODO update
          req.response.status_code = 405
          req.response.end
        end

      end

      server.request_handler(matcher).listen(8080)
    end

    def module_dir(repo_name, module_name, version)
      "#{@repo_root}/#{repo_name}/#{module_name}/#{version}"
    end

  end

end

