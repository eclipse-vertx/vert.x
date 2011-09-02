# Copyright 2002-2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use
# this file except in compliance with the License. You may obtain a copy of the
# License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

require "core/buffer"

include Java

module FileSystem

  class FileStats

    def initialize(j_stats)
      @j_stats = j_stats
    end

    def creation_time
      Time.at(@j_stats.creationTime.getTime() / 1000)
    end

    def last_access_time
      Time.at(@j_stats.lastAccessTime.getTime() / 1000)
    end

    def last_modified_time
      Time.at(@j_stats.lastModifiedTime.getTime() / 1000)
    end

    def directory?
      @j_stats.isDirectory
    end

    def other?
      @j_stats.isOther
    end

    def regular_file?
      @j_stats.isRegularFile
    end

    def symbolic_link?
      @j_stats.isSymbolicLink
    end

    def size
      @j_stats.size
    end

    private :initialize
  end

  class FSStats

    def initialize(j_stats)
      @j_stats = j_stats
    end

    def total_space
      @j_stats.totalSpace
    end

    def unallocated_space
      @j_stats.unallocatedSpace
    end

    def usable_space
      @j_stats.usableSpace
    end

    private :initialize

  end

  class AsyncFile

    def initialize(j_file)
      @j_file = j_file
    end

    def close(&hndlr)
      @j_file.close(hndlr)
    end

    def write(buffer, position, &hndlr)
      @j_file.write(buffer._to_java_buffer, position, hndlr)
    end

    def read(buffer, offset, position, length, &hndlr)
      @j_file.read(buffer._to_java_buffer, offset, position, length, Proc.new { |compl|
        hndlr.call(org.nodex.core.Completion.new(Buffer.new(compl.result)))
      })
    end

    def write_stream
      WriteStream.new(@j_file.getWriteStream)
    end

    def read_stream
      ReadStream.new(@j_file.getReadStream)
    end

    def sync(meta_data = false, &hndlr)
      @j_file.sync(meta_data, hndlr)
    end

    class WriteStream

      def initialize(j_ws)
        @j_ws = j_ws
      end

      def write_buffer(buff)
        @j_ws.writeBuffer(buff._to_java_buffer)
      end

      def write_queue_max_size=(size)
        @j_ws.setWriteQueueMaxSize(size)
      end

      def write_queue_full?
        @j_ws.writeQueueFull
      end

      def drain_handler(&hndlr)
        @j_ws.drainHandler(hndlr)
      end

      def exception_handler(&hndlr)
        @j_ws.exceptionHandler(hndlr)
      end

      def _to_write_stream
        @j_ws
      end

      private :initialize
    end

    class ReadStream

      def initialize(j_rs)
        @j_rs = j_rs
      end

      def data_handler(&hndlr)
        @j_rs.dataHandler(Proc.new { |j_buff|
          hndlr.call(Buffer.new(j_buff))
        })
      end

      def pause
        @j_rs.pause
      end

      def resume
        @j_rs.resume
      end

      def exception_handler(&hndlr)
        @j_rs.exceptionHandler(hndlr)
      end

      def end_handler(&hndlr)
        @j_rs.endHandler(hndlr)
      end

      def _to_read_stream
        @j_rs
      end

      private :initialize

    end

    private :initialize

  end

  def FileSystem.copy(from, to, &hndlr)
    org.nodex.core.file.FileSystem.instance.copy(from, to, hndlr)
  end

  def FileSystem.copy_recursive(from, to, &hndlr)
    org.nodex.core.file.FileSystem.instance.copy(from, to, true, hndlr)
  end

  def FileSystem.move(from, to, &hndlr)
    org.nodex.core.file.FileSystem.instance.move(from, to, hndlr)
  end

  def FileSystem.truncate(path, len, &hndlr)
    org.nodex.core.file.FileSystem.instance.truncate(path, len, hndlr)
  end

  def FileSystem.chmod(path, perms, dir_perms = nil, &hndlr)
    org.nodex.core.file.FileSystem.instance.chmod(path, perms, dir_perms, hndlr)
  end

  def FileSystem.stat(path, &hndlr)
    org.nodex.core.file.FileSystem.instance.stat(path, Proc.new{ |compl|
      hndlr.call(org.nodex.core.Completion.new(FileStats.new(compl.result)))
    })
  end

  def FileSystem.link(link, existing, &hndlr)
    org.nodex.core.file.FileSystem.instance.link(link, existing, hndlr)
  end

  def FileSystem.sym_link(link, existing, &hndlr)
    org.nodex.core.file.FileSystem.instance.symLink(link, existing, hndlr)
  end

  def FileSystem.unlink(link, &hndlr)
    org.nodex.core.file.FileSystem.instance.unlink(link, hndlr)
  end

  def FileSystem.read_sym_link(link, &hndlr)
    org.nodex.core.file.FileSystem.instance.readSymLink(link, hndlr)
  end

  def FileSystem.delete(path, &hndlr)
    org.nodex.core.file.FileSystem.instance.delete(path, hndlr)
  end

  def FileSystem.delete_recursive(path, &hndlr)
    org.nodex.core.file.FileSystem.instance.delete(path, true, hndlr)
  end

  def FileSystem.mkdir(path, perms = nil, &hndlr)
    org.nodex.core.file.FileSystem.instance.mkdir(path, perms, hndlr)
  end

  def FileSystem.mkdir_with_parents(path, perms = nil, &hndlr)
    org.nodex.core.file.FileSystem.instance.mkdir(path, perms, true, hndlr)
  end

  def FileSystem.read_dir(path, filter = nil, &hndlr)
    org.nodex.core.file.FileSystem.instance.readDir(path, filter, hndlr)
  end

  def FileSystem.read_file_as_string(path, encoding = "UTF-8", &hndlr)
    org.nodex.core.file.FileSystem.instance.readFileAsString(path, encoding, hndlr)
  end

  def FileSystem.write_string_to_file(path, str, encoding = "UTF-8", &hndlr)
    org.nodex.core.file.FileSystem.instance.writeFileAsString(path, str, encoding, hndlr)
  end

  def FileSystem.lock
    # TODO
  end

  def FileSystem.unlock
    # TODO
  end

  def FileSystem.watch_file(path)
    # TODO
  end

  def FileSystem.unwatch_file(path)
    # TODO
  end

  def FileSystem.open(path, perms = nil, read = true, write = true, create_new = true, sync = false, sync_meta = false, &hndlr)
    org.nodex.core.file.FileSystem.instance.open(path, perms, read, write, create_new, sync, sync_meta, Proc.new { |compl|
      if compl.succeeded
        hndlr.call(org.nodex.core.Completion.new(AsyncFile.new(compl.result)))
      else
        hndlr.call(compl)
      end
    })
  end

  def FileSystem.create_file(path, perms = nil, &hndlr)
    org.nodex.core.file.FileSystem.instance.createFile(path, perms, hndlr)
  end

  def FileSystem.exists(path, &hndlr)
    org.nodex.core.file.FileSystem.instance.exists(path, hndlr)
  end

  def FileSystem.fs_stats(path, &hndlr)
    org.nodex.core.file.FileSystem.instance.getFSStats(path, Proc.new { |j_stats|
      hndlr.call(org.nodex.core.Completion.new(FSStats.new(j_stats)))
    })
  end


end