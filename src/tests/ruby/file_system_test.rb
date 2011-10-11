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
require 'vertx'
require 'utils'
include Vertx

# TODO More thorough testing required
class FileSystemTest < Test::Unit::TestCase

  FileDir = "ruby-test-output"

  def setup
    latch = Utils::Latch.new 1
    Vertx::go {
      FileSystem::exists(FileDir).handler{ |exists|
        if exists
          FileSystem::delete_recursive(FileDir).handler{
            FileSystem::mkdir(FileDir).handler {
              latch.countdown
            }
          }
        else
          FileSystem::mkdir(FileDir).handler {
            latch.countdown
          }
        end
      }
    }
    latch.await(5)
  end

  def teardown
    latch = Utils::Latch.new 1
    Vertx::go {
      FileSystem::delete_recursive(FileDir).handler{
        FileSystem::mkdir(FileDir).handler{
          latch.countdown
        }
      }
    }
    latch.await(5)
  end

  def test_stats
    latch = Utils::Latch.new 1
    Vertx::go {
      filename = FileDir + "/test-file.txt"
      FileSystem::create_file(filename).handler{
        FileSystem::props(filename).handler{ |compl|
          assert(compl.succeeded?)
          stats = compl.result
#          puts "creation time #{stats.creation_time}"
#          puts "last access time #{stats.last_access_time}"
#          puts "last modification time #{stats.last_modified_time}"
#          puts "directory? #{stats.directory?}"
#          puts "regular file? #{stats.regular_file?}"
#          puts "symbolic link? #{stats.symbolic_link?}"
#          puts "other? #{stats.other?}"
#          puts "size #{stats.size}"
          assert(stats.regular_file?)
          latch.countdown
        }
      }
    }
    assert(latch.await(5))
  end

  def test_async_file
    latch = Utils::Latch.new 1
    Vertx::go {
      FileSystem::open(FileDir + "/somefile.txt").handler{ |compl|
        assert(compl.succeeded?)
        file = compl.result
        num_chunks = 100;
        chunk_size = 1000;
        tot_buff = Buffer.create(0)
        written =0
        for i in 0..num_chunks - 1
          buff = Utils.gen_buffer(chunk_size)
          tot_buff.append_buffer(buff)
          file.write(buff, i * chunk_size).handler{
            written += 1
            if written == num_chunks
              # all written
              tot_read = Buffer.create(0)
              read = 0
              for j in 0..num_chunks - 1
                pos = j * chunk_size
                file.read(tot_read, pos, pos, chunk_size).handler{ |compl|
                  assert(compl.succeeded?)
                  buff = compl.result
                  read += 1
                  if read == num_chunks
                    # all read
                    assert(Utils.buffers_equal(tot_buff, tot_read))
                    file.close.handler{
                      latch.countdown
                    }
                  end
                }
              end
            end
          }
        end
      }
    }
    assert(latch.await(5))
  end

  def test_async_file_streams
    latch = Utils::Latch.new 1
    filename = FileDir + "/somefile.txt"
    Vertx::go {
      FileSystem::open(filename).handler{ |compl|
        assert(compl.succeeded?)
        file = compl.result
        num_chunks = 100;
        chunk_size = 1000;
        tot_buff = Buffer.create(0)
        write_stream = file.write_stream
        for i in 0..num_chunks - 1
          buff = Utils.gen_buffer(chunk_size)
          tot_buff.append_buffer(buff)
          write_stream.write_buffer(buff)
        end
        file.close.handler{
          FileSystem::open(filename).handler{ |compl|
            assert(compl.succeeded?)
            file = compl.result
            read_stream = file.read_stream
            tot_read = Buffer.create(0)
            read_stream.data_handler{ |data|
              tot_read.append_buffer(data)
            }
            read_stream.end_handler{
              assert(Utils.buffers_equal(tot_buff, tot_read))
              file.close.handler {
                latch.countdown
              }
            }
          }
        }
      }
    }
    assert(latch.await(5))
  end

end