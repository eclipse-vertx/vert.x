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

    @@logger = Logger.get_logger(self)

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
          req.response.put_header("Content-Type", "application/json").write_str(json).end
        end

      end

      matcher.put("/:reponame/:modulename/:version/") do |req|

        repo_name = req.params['reponame']
        module_name = req.params['modulename']
        version = req.params['version']

        md = module_dir(repo_name, module_name, version)

        # We pause the request, since we don't want to lose any data that arrives between now and when the data handler
        # is set, which doesn't happen until the file we are streaming the upload to has opened
        req.pause

        comp = Composer.new

        # The failure handler - this will get called if any step in the set of actions fails
        comp.exception_handler do |e|
          @@logger.error("Failed to process put", e)
          req.response.status_code = 500
          req.response.write_str_and_end(e.to_s)
        end

        # We create the module directory if it doesn't already exist
        fut_exists = comp.series { FileSystem::exists? md }
        comp.series{ FileSystem::mkdir_with_parents(md) if !fut_exists.result }

        # We open the file we're going to stream the tarball to
        arch_name = "#{module_name}-#{version}.tar.gz"
        fname = "#{md}/#{arch_name}"
        fut_open = comp.series{ FileSystem::open(fname) }

        # Once the file is open, we can set the data and end handlers on the HTTP request
        # When the end handler fires we signal this step is complete by setting the result
        # So we can move on to the next step
        comp.series do
          f = SimpleFuture.new
          ws = fut_open.result.write_stream
          req.data_handler { |data|  ws.write_buffer(data) }
          req.end_handler { f.result = nil }
          req.resume
          f
        end

        # The file has been streamed, so we can close it
        comp.series do
          f = SimpleFuture.new
          fut_open.result.close.handler { f.result = nil }
        end

        # The tarball has been uploaded, let's create a dir to unpack it in
        unpack_dir = "#{md}/unpack"
        comp.series{ FileSystem::mkdir_with_parents(unpack_dir) }

        # Now unpack it - we shell out to a system command that we run on the blocking pool for this
        comp.series do
          Vertx::run_blocking do
            dir_abs = File.expand_path(md)
            cmd = "tar -zxf #{dir_abs}/#{arch_name} -C #{unpack_dir}"
            system(cmd)
          end
        end

        ## Validate the tarball contains a single directory in which the actual module stuff lives
        fut_list = comp.series{ FileSystem::read_dir(unpack_dir) }

        comp.series{ raise "tarball must contain a single directory which contains the module" if fut_list.result.length != 1 }

        dir_name = ''

        fut_props = comp.series do
          dir_name = fut_list.result[0]
          FileSystem::props(dir_name)
        end

        comp.series{ raise "tarball file #{dir_name} is not a directory" if !fut_props.result.directory?}

        # Read the module.json file into a string
        fut_contents = comp.series { FileSystem::read_file_as_buffer("#{dir_name}/module.json") }

        # And parse it to make sure it is actual JSON
        fut_json = comp.series { JSON.parse(fut_contents.result.to_s) }

        # Then we do some basic validation on the module.json fields
        comp.series do
          # Some basic validation
          json = fut_json.result

          raise "name field in module.json should be #{module_name}" if module_name != json["name"]
          raise "version field in module.json should be #{version}" if version != json["version"]
        end

        # Now we copy module.json into the module directory
        comp.series{ FileSystem::copy("#{dir_name}/module.json", "#{md}/module.json") }

        # And we remove the temporary unpack directory
        comp.series{ FileSystem::delete_recursive(unpack_dir) }

        # And we're done!
        comp.series{ req.response.end }

        # Now execute the composer - nothing happens until we do this
        comp.execute

      end

      server.request_handler(matcher).listen(8080)
    end

    def module_dir(repo_name, module_name, version)
      "#{@repo_root}/#{repo_name}/#{module_name}/#{version}"
    end

  end

end

