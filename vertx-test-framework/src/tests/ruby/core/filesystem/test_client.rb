# Copyright 2011-2012 the original author or authors.
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

require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

FILEDIR = "ruby-test-output"

@tu.check_context

def setup
  FileSystem::exists?(FILEDIR) do |err, exists|
    if exists
      FileSystem::delete_recursive(FILEDIR) do
        FileSystem::mkdir(FILEDIR) do
          yield
        end
      end
    else
      FileSystem::mkdir(FILEDIR) do
        yield
      end
    end
  end
end

def teardown
  FileSystem::delete_recursive(FILEDIR) do
    yield
  end
end

def test_copy
  filename = FILEDIR + "/test-file.txt"
  tofile = FILEDIR + "/to-file.txt"
  FileSystem::create_file(filename) do
    @tu.check_context
    FileSystem::copy(filename, tofile) do |err, res|
      @tu.check_context
      @tu.azzert(err == nil)
      @tu.test_complete
    end
  end
end

def test_stats
  filename = FILEDIR + "/test-file.txt"
  FileSystem::create_file(filename) do
    @tu.check_context
    FileSystem::props(filename)do |err, stats|
      @tu.check_context
      @tu.azzert(err == nil)
      puts "creation time #{stats.creation_time}"
      puts "last access time #{stats.last_access_time}"
      puts "last modification time #{stats.last_modified_time}"
      puts "directory? #{stats.directory?}"
      puts "regular file? #{stats.regular_file?}"
      puts "symbolic link? #{stats.symbolic_link?}"
      puts "other? #{stats.other?}"
      puts "size #{stats.size}"
      @tu.azzert(stats.regular_file?)
      @tu.test_complete
    end
  end
end

def test_async_file
  FileSystem::open(FILEDIR + "/somefile.txt") do |err, file|
    @tu.check_context
    @tu.azzert(err == nil)
    num_chunks = 100;
    chunk_size = 1000;
    tot_buff = Buffer.create()
    written =0
    for i in 0..num_chunks - 1
      buff = TestUtils.gen_buffer(chunk_size)
      tot_buff.append_buffer(buff)
      file.write(buff, i * chunk_size) do
        @tu.check_context
        written += 1
        if written == num_chunks
          # all written
          tot_read = Buffer.create()
          read = 0
          for j in 0..num_chunks - 1
            pos = j * chunk_size
            file.read(tot_read, pos, pos, chunk_size) do |err, buff|
              @tu.check_context
              @tu.azzert(err == nil)
              read += 1
              if read == num_chunks
                # all read
                @tu.azzert(TestUtils.buffers_equal(tot_buff, tot_read))
                file.close do
                  @tu.check_context
                  @tu.test_complete
                end
              end
            end
          end
        end
      end
    end
  end
end

def test_async_file_streams
  filename = FILEDIR + "/somefile.txt"
  FileSystem::open(filename) do |err, file|

    @tu.check_context
    @tu.azzert(err == nil)
    num_chunks = 100;
    chunk_size = 1000;
    tot_buff = Buffer.create()
    write_stream = file.write_stream
    for i in 0..num_chunks - 1
      buff = TestUtils.gen_buffer(chunk_size)
      tot_buff.append_buffer(buff)
      write_stream.write_buffer(buff)
    end
    file.close do
      FileSystem::open(filename) do |err, file|
        @tu.check_context
        @tu.azzert(err == nil)
        read_stream = file.read_stream
        tot_read = Buffer.create()
        read_stream.data_handler do |data|
          tot_read.append_buffer(data)
        end
        read_stream.end_handler do
          @tu.azzert(TestUtils.buffers_equal(tot_buff, tot_read))
          @tu.check_context
          file.close do
            @tu.check_context
            @tu.test_complete
          end
        end
      end
    end
  end
end

def vertx_stop
  @tu.check_context
  teardown do
    @tu.unregister_all
    @tu.app_stopped
  end
end

@tu.register_all(self)
setup { @tu.app_ready }
