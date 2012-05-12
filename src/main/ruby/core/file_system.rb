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

require 'core/streams'

module Vertx

  # Represents the properties of a file on the file system
  # @author {http://tfox.org Tim Fox}
  class FileProps

    # @private
    def initialize(j_props)
      @j_props = j_props
    end

    # @return [Time] The creation time of the file.
    def creation_time
      Time.at(@j_props.creationTime.getTime() / 1000)
    end

    # @return [Time] The last access time of the file.
    def last_access_time
      Time.at(@j_props.lastAccessTime.getTime() / 1000)
    end

    # @return [Time] The last modified time of the file.
    def last_modified_time
      Time.at(@j_props.lastModifiedTime.getTime() / 1000)
    end

    # @return [Boolean] Is the file a directory?
    def directory?
      @j_props.isDirectory
    end

    # @return [Boolean] Is the file some other file type?
    def other?
      @j_props.isOther
    end

    # @return [Boolean] Is it a regular file?
    def regular_file?
      @j_props.isRegularFile
    end

    # @return [Boolean] Is it a symbolic link?
    def symbolic_link?
      @j_props.isSymbolicLink
    end

    # @return [FixNum] The size of the file, in bytes.
    def size
      @j_props.size
    end

  end

  # Represents the properties of a file system
  # @author {http://tfox.org Tim Fox}
  class FSProps

    # @private
    def initialize(j_props)
      @j_props = j_props
    end

    # @return [FixNum] The total space on the file system, in bytes.
    def total_space
      @j_props.totalSpace
    end

    # @return [FixNum] Unallocated space on the file system, in bytes.
    def unallocated_space
      @j_props.unallocatedSpace
    end

    # @return [FixNum] Usable space on the file system, in bytes.
    def usable_space
      @j_props.usableSpace
    end

  end

  # Represents a file on the file-system which can be read from, or written to asynchronously.
  # Methods also exist to get a read stream or a write stream on the file. This allows the data to be pumped to and from
  # other streams, e.g. an {HttpClientRequest} instance, using the {Pump} class
  # @author {http://tfox.org Tim Fox}
  class AsyncFile

    # @private
    def initialize(j_file)
      @j_file = j_file
    end

    # Close the file, asynchronously.
    def close(&block)
      @j_file.close(FSWrappedHandler.new(block))
    end

    # Write a {Buffer} to the file, asynchronously.
    # When multiple writes are invoked on the same file
    # there are no guarantees as to order in which those writes actually occur.
    # @param [Buffer] buffer The buffer to write
    # @param [FixNum] position The position in the file where to write the buffer. Position is measured in bytes and
    # starts with zero at the beginning of the file.
    def write(buffer, position, &block)
      @j_file.write(buffer._to_java_buffer, position, FSWrappedHandler.new(block))
    end

    # Reads some data from a file into a buffer, asynchronously.
    # When multiple reads are invoked on the same file
    # there are no guarantees as to order in which those reads actually occur.
    # @param [Buffer] buffer The buffer into which the data which is read is written.
    # @param [FixNum] offset The position in the buffer where to start writing the data.
    # @param [FixNum] position The position in the file where to read the data.
    # @param [FixNum] length The number of bytes to read.
    def read(buffer, offset, position, length, &block)
      @j_file.read(buffer._to_java_buffer, offset, position, length, FSWrappedHandler.new(block) { |j_buff| Buffer.new(j_buff) })
    end

    # @return [WriteStream] A write stream operating on the file.
    def write_stream
      AsyncFileWriteStream.new(@j_file.getWriteStream)
    end

    # @return [ReadStream] A read stream operating on the file.
    def read_stream
      AsyncFileReadStream.new(@j_file.getReadStream)
    end

    # Flush any writes made to this file to underlying persistent storage, asynchronously.
    # If the file was opened with flush set to true then calling this method will have no effect.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def flush
      Future.new(@j_file.flush)
    end

    # @private
    class AsyncFileWriteStream
      include WriteStream
      def initialize(j_ws)
        @j_del = j_ws
      end
    end

    # @private
    class AsyncFileReadStream
      include ReadStream
      def initialize(j_rs)
        @j_del = j_rs
      end
    end

  end

  # @private
  class FSWrappedHandler
    include org.vertx.java.core.AsyncResultHandler

    def initialize(handler, &result_converter)
      @handler = handler
      @result_converter = result_converter
    end

    def handle(async_result)
      if @handler
        if !async_result.exception
          if @result_converter
            @handler.call(nil, @result_converter.call(async_result.result))
          else
            @handler.call(nil, async_result.result)
          end
        else
          @handler.call(async_result.exception, nil)
        end
      end
    end

  end

  # Represents the file-system and contains a broad set of operations for manipulating files.
  # An asynchronous and a synchronous version of each operation is provided.
  # The asynchronous versions take a handler as a final argument which is
  # called when the operation completes or an error occurs. The handler is called
  # with two arguments; the first an exception, this will be nil if the operation has
  # succeeded. The second is the result - this will be nil if the operation failed or
  # there was no result to return.
  # The synchronous versions return the results, or throw exceptions directly.
  # @author {http://tfox.org Tim Fox}
  class FileSystem

    @@j_fs = org.vertx.java.deploy.impl.VertxLocator.vertx.fileSystem()

    # Copy a file, asynchronously. The copy will fail if from does not exist, or if to already exists.
    # @param [String] from Path of file to copy
    # @param [String] to Path of file to copy to
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.copy(from, to, &block)
      @@j_fs.copy(from, to, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.copy}
    def FileSystem.copy_sync(from, to)
      @@j_fs.copySync(from, to)
    end

    # Copy a file recursively, asynchronously. The copy will fail if from does not exist, or if to already exists and is not empty.
    # If the source is a directory all contents of the directory will be copied recursively, i.e. the entire directory
    # tree is copied.
    # @param [String] from Path of file to copy
    # @param [String] to Path of file to copy to
    def FileSystem.copy_recursive(from, to, &block)
      @@j_fs.copy(from, to, true, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.copy_recursive}
    def FileSystem.copy_recursive_sync(from, to)
      @@j_fs.copySync(from, to, true)
    end

    # Move a file, asynchronously. The move will fail if from does not exist, or if to already exists.
    # @param [String] from Path of file to move
    # @param [String] to Path of file to move to
    def FileSystem.move(from, to, &block)
      @@j_fs.move(from, to, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.move}
    def FileSystem.move_sync(from, to)
      @@j_fs.moveSync(from, to)
    end

    # Truncate a file, asynchronously. The move will fail if path does not exist.
    # @param [String] path Path of file to truncate
    # @param [FixNum] len Length to truncate file to. Will fail if len < 0. If len > file size then will do nothing.
    def FileSystem.truncate(path, len, &block)
      @@j_fs.truncate(path, len, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.truncate}
    def FileSystem.truncate_sync(path, len)
      @@j_fs.truncateSync(path, len)
    end

    # Change the permissions on a file, asynchronously. If the file is directory then all contents will also have their permissions changed recursively.
    # @param [String] path Path of file to change permissions
    # @param [String] perms A permission string of the form rwxr-x--- as specified in
    # {http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html}. This is
    # used to set the permissions for any regular files (not directories).
    # @param [String] dir_perms A permission string of the form rwxr-x---. Used to set permissions for regular files.
    def FileSystem.chmod(path, perms, dir_perms = nil, &block)
      @@j_fs.chmod(path, perms, dir_perms, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.chmod}
    def FileSystem.chmod_sync(path, perms, dir_perms = nil)
      @@j_fs.chmodSync(path, perms, dir_perms)
    end

    # Get file properties for a file, asynchronously.
    # @param [String] path Path to file
    def FileSystem.props(path, &block)
      @@j_fs.props(path, FSWrappedHandler.new(block) { |j_props| FileProps.new(j_props) })
    end

    # Synchronous version of {#FileSystem.props}
    def FileSystem.props_sync(path)
      j_props = @@j_fs.propsSync(path)
      FileProps.new(j_props)
    end

    # Create a hard link, asynchronously..
    # @param [String] link Path of the link to create.
    # @param [String] existing Path of where the link points to.
    def FileSystem.link(link, existing, &block)
       @@j_fs.link(link, existing, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.link}
    def FileSystem.link_sync(link, existing)
      @@j_fs.linkSync(link, existing)
    end

    # Create a symbolic link, asynchronously.
    # @param [String] link Path of the link to create.
    # @param [String] existing Path of where the link points to.
    def FileSystem.sym_link(link, existing, &block)
       @@j_fs.symLink(link, existing, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.sym_link}
    def FileSystem.sym_link_sync(link, existing)
      @@j_fs.symLinkSync(link, existing)
    end

    # Unlink a hard link.
    # @param [String] link Path of the link to unlink.
    def FileSystem.unlink(link, &block)
      @@j_fs.unlink(link, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.unlink}
    def FileSystem.unlinkSync(link)
      @@j_fs.unlinkSync(link)
    end

    # Read a symbolic link, asynchronously. I.e. tells you where the symbolic link points.
    # @param [String] link Path of the link to read.
    def FileSystem.read_sym_link(link, &block)
      @@j_fs.readSymLink(link, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.read_sym_link}
    def FileSystem.read_sym_link_sync(link)
      @@j_fs.readSymLinkSync(link)
    end

    # Delete a file on the file system, asynchronously.
    # The delete will fail if the file does not exist, or is a directory and is not empty.
    # @param [String] path Path of the file to delete.
    def FileSystem.delete(path, &block)
      @@j_fs.delete(path, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.delete}
    def FileSystem.delete_sync(path)
      @@j_fs.deleteSync(path)
    end

    # Delete a file on the file system recursively, asynchronously.
    # The delete will fail if the file does not exist. If the file is a directory the entire directory contents
    # will be deleted recursively.
    # @param [String] path Path of the file to delete.
    def FileSystem.delete_recursive(path, &block)
      @@j_fs.delete(path, true, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.delete_recursive}
    def FileSystem.delete_recursive_sync(path)
      @@j_fs.deleteSync(path, true)
    end

    # Create a directory, asynchronously.
    # The create will fail if the directory already exists, or if it contains parent directories which do not already
    # exist.
    # @param [String] path Path of the directory to create.
    # @param [String] perms. A permission string of the form rwxr-x--- to give directory.
    def FileSystem.mkdir(path, perms = nil, &block)
      @@j_fs.mkdir(path, perms, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.mkdir}
    def FileSystem.mkdir_sync(path, perms = nil)
      @@j_fs.mkdirSync(path, perms)
    end

    # Create a directory, and create all it's parent directories if they do not already exist, asynchronously.
    # The create will fail if the directory already exists.
    # @param [String] path Path of the directory to create.
    # @param [String] perms. A permission string of the form rwxr-x--- to give the created directory(ies).
    def FileSystem.mkdir_with_parents(path, perms = nil, &block)
      @@j_fs.mkdir(path, perms, true, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.mkdir_with_parents}
    def FileSystem.mkdir_with_parents_sync(path, perms = nil)
      @@j_fs.mkdirSync(path, perms, true)
    end

    # Read a directory, i.e. list it's contents, asynchronously.
    # The read will fail if the directory does not exist.
    # @param [String] path Path of the directory to read.
    # @param [String] filter A regular expression to filter out the contents of the directory. If the filter is not nil
    # then only files which match the filter will be returned.
    def FileSystem.read_dir(path, filter = nil, &block)
      @@j_fs.readDir(path, filter, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.read_dir}
    def FileSystem.read_dir_sync(path, filter = nil)
      @@j_fs.readDirSync(path, filter)
    end

    # Read the contents of an entire file as a {Buffer}, asynchronously.
    # @param [String] path Path of the file to read.
    def FileSystem.read_file_as_buffer(path, &block)
      @@j_fs.readFile(path, FSWrappedHandler.new(block) { |j_buff| Buffer.new(j_buff)})
    end

    # Synchronous version of {#FileSystem.read_file_as_buffer}
    def FileSystem.read_file_as_buffer_sync(path)
      @@j_fs.readFileSync(path)
    end

    # Write a [Buffer] as the entire contents of a file, asynchronously.
    # @param [String] path Path of the file to write.
    # @param [String] buffer The Buffer to write
    def FileSystem.write_buffer_to_file(path, buffer, &block)
      @@j_fs.writeFile(path, buffer, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.write_buffer_to_file}
    def FileSystem.write_buffer_to_file_sync(path, buffer)
      @@j_fs.writeFileSync(path, buffer)
    end

    # Open a file on the file system, asynchronously.
    # @param [String] path Path of the file to open.
    # @param [String] perms If the file does not exist and create_new is true, then the file will be created with these permissions.
    # @param [Boolean] read Open the file for reading?
    # @param [Boolean] write Open the file for writing?
    # @param [Boolean] create_new Create the file if it doesn't already exist?
    # @param [Boolean] flush Whenever any data is written to the file, flush all changes to permanent storage immediately?
    def FileSystem.open(path, perms = nil, read = true, write = true, create_new = true, flush = false, &block)
      @@j_fs.open(path, perms, read, write, create_new, flush, FSWrappedHandler.new(block){ |j_file| AsyncFile.new(j_file)})
    end

    # Synchronous version of {#FileSystem.open}
    def FileSystem.open_sync(path, perms = nil, read = true, write = true, create_new = true, flush = false)
      j_af = @@j_fs.open(path, perms, read, write, create_new, flush)
      AsyncFile.new(j_af)
    end

    # Create a new empty file, asynchronously.
    # @param [String] path Path of the file to create.
    # @param [String] perms The file will be created with these permissions.
    def FileSystem.create_file(path, perms = nil, &block)
      @@j_fs.createFile(path, perms, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.create_file}
    def FileSystem.create_file_sync(path, perms = nil)
      @@j_fs.createFileSync(path, perms)
    end

    # Check if  a file exists, asynchronously.
    # @param [String] path Path of the file to check.
    def FileSystem.exists?(path, &block)
      @@j_fs.exists(path, FSWrappedHandler.new(block))
    end

    # Synchronous version of {#FileSystem.exists?}
    def FileSystem.exists_sync?(path)
      @@j_fs.existsSync(path)
    end

    # Get properties for the file system, asynchronously.
    # @param [String] path Path in the file system.
    def FileSystem.fs_props(path, &block)
      @@j_fs.fsProps(path, FSWrappedHandler.new(block) { |j_props| FSProps.new(j_props)})
    end

    # Synchronous version of {#FileSystem.fs_props}
    def FileSystem.fs_props_sync(path)
      j_fsprops = @@j_fs.fsPropsSync(path)
      FSProps.new(j_fsprops)
    end

  end
end