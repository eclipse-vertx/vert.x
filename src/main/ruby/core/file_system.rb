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
require 'core/composition'

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
    # This method must be called using the same event loop the file was opened from.
    # @return [Future] a Future representing the future result of closing the file.
    def close
      Future.new(@j_file.close)
    end

    # Write a {Buffer} to the file, asynchronously.
    # When multiple writes are invoked on the same file
    # there are no guarantees as to order in which those writes actually occur.
    # This method must be called using the same event loop the file was opened from.
    # @param [Buffer] buffer The buffer to write
    # @param [FixNum] position The position in the file where to write the buffer. Position is measured in bytes and
    # starts with zero at the beginning of the file.
    # @return [Future] a Future representing the future result of the action.
    def write(buffer, position)
      Future.new(@j_file.write(buffer._to_java_buffer, position))
    end

    # Reads some data from a file into a buffer, asynchronously.
    # When multiple reads are invoked on the same file
    # there are no guarantees as to order in which those reads actually occur.
    # This method must be called using the same event loop the file was opened from.
    # @param [Buffer] buffer The buffer into which the data which is read is written.
    # @param [FixNum] offset The position in the buffer where to start writing the data.
    # @param [FixNum] position The position in the file where to read the data.
    # @param [FixNum] length The number of bytes to read.
    # @return [Future] a Future representing the future result of the action. The type of {Future#result} is {Buffer}.
    def read(buffer, offset, position, length, &hndlr)
      Future.new(@j_file.read(buffer._to_java_buffer, offset, position, length)){ |j_buff| Buffer.new(j_buff) }
    end

    # @return [WriteStream] A write stream operating on the file.
    # This method must be called using the same event loop the file was opened from.
    def write_stream
      AsyncFileWriteStream.new(@j_file.getWriteStream)
    end

    # @return [ReadStream] A read stream operating on the file.
    # This method must be called using the same event loop the file was opened from.
    def read_stream
      AsyncFileReadStream.new(@j_file.getReadStream)
    end

    # Flush any writes made to this file to underlying persistent storage, asynchronously.
    # If the file was opened with flush set to true then calling this method will have no effect.
    # This method must be called using the same event loop the file was opened from.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    # @return [Future] a Future representing the future result of the action.
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

  # Represents the file-system and contains a broad set of operations for manipulating files.
  # @author {http://tfox.org Tim Fox}
  class FileSystem

    # Copy a file, asynchronously. The copy will fail if from does not exist, or if to already exists.
    # @param [String] from Path of file to copy
    # @param [String] to Path of file to copy to
    # @param [Block] hndlr a block representing the handler which is called on completion.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.copy(from, to)
      Future.new(org.vertx.java.core.file.FileSystem.instance.copy(from, to))
    end

    def FileSystem.copy_sync(from, to)
      org.vertx.java.core.file.FileSystem.instance.copySync(from, to)
    end

    # Copy a file recursively, asynchronously. The copy will fail if from does not exist, or if to already exists and is not empty.
    # If the source is a directory all contents of the directory will be copied recursively, i.e. the entire directory
    # tree is copied.
    # @param [String] from Path of file to copy
    # @param [String] to Path of file to copy to
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.copy_recursive(from, to)
      Future.new(org.vertx.java.core.file.FileSystem.instance.copy(from, to, true))
    end

    def FileSystem.copy_recursive_sync(from, to)
      org.vertx.java.core.file.FileSystem.instance.copySync(from, to, true)
    end

    # Move a file, asynchronously. The move will fail if from does not exist, or if to already exists.
    # @param [String] from Path of file to move
    # @param [String] to Path of file to move to
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.move(from, to)
      Future.new(org.vertx.java.core.file.FileSystem.instance.move(from, to))
    end

    def FileSystem.move_sync(from, to)
      org.vertx.java.core.file.FileSystem.instance.moveSync(from, to)
    end

    # Truncate a file, asynchronously. The move will fail if path does not exist.
    # @param [String] path Path of file to truncate
    # @param [FixNum] len Length to truncate file to. Will fail if len < 0. If len > file size then will do nothing.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.truncate(path, len)
      Future.new(org.vertx.java.core.file.FileSystem.instance.truncate(path, len))
    end

    def FileSystem.truncate_sync(path, len)
      org.vertx.java.core.file.FileSystem.instance.truncateSync(path, len)
    end

    # Change the permissions on a file, asynchronously. If the file is directory then all contents will also have their permissions changed recursively.
    # @param [String] path Path of file to change permissions
    # @param [String] perms A permission string of the form rwxr-x--- as specified in
    # {http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html}. This is
    # used to set the permissions for any regular files (not directories).
    # @param [String] dir_perms A permission string of the form rwxr-x---. Used to set permissions for regular files.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.chmod(path, perms, dir_perms = nil)
      Future.new(org.vertx.java.core.file.FileSystem.instance.chmod(path, perms, dir_perms))
    end

    def FileSystem.chmod_sync(path, perms, dir_perms = nil)
      org.vertx.java.core.file.FileSystem.instance.chmodSync(path, perms, dir_perms)
    end

    # Get file properties for a file, asynchronously.
    # @param [String] path Path to file
    # @return [Future] a Future representing the future result of the action. The type of {Future#result} is {FileProps}.
    def FileSystem.props(path)
      Future.new(org.vertx.java.core.file.FileSystem.instance.props(path)) { |j_props| FileProps.new(j_props) }
    end

    def FileSystem.props_sync(path)
      j_props = org.vertx.java.core.file.FileSystem.instance.propsSync(path)
      FileProps.new(j_props)
    end

    # Create a hard link, asynchronously..
    # @param [String] link Path of the link to create.
    # @param [String] existing Path of where the link points to.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.link(link, existing)
      Future.new(org.vertx.java.core.file.FileSystem.instance.link(link, existing))
    end

    def FileSystem.link_sync(link, existing)
      org.vertx.java.core.file.FileSystem.instance.linkSync(link, existing)
    end

    # Create a symbolic link, asynchronously.
    # @param [String] link Path of the link to create.
    # @param [String] existing Path of where the link points to.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.sym_link(link, existing)
      Future.new(org.vertx.java.core.file.FileSystem.instance.symLink(link, existing))
    end

    def FileSystem.sym_link_sync(link, existing)
      org.vertx.java.core.file.FileSystem.instance.symLinkSync(link, existing)
    end

    # Unlink a hard link.
    # @param [String] link Path of the link to unlink.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.unlink(link)
      Future.new(org.vertx.java.core.file.FileSystem.instance.unlink(link))
    end

    def FileSystem.unlinkSync(link)
      org.vertx.java.core.file.FileSystem.instance.unlinkSync(link)
    end

    # Read a symbolic link, asynchronously. I.e. tells you where the symbolic link points.
    # @param [String] link Path of the link to read.
    # @return [Future] a Future representing the future result of the action. The type of {Future#result} is String..
    def FileSystem.read_sym_link(link)
      Future.new(org.vertx.java.core.file.FileSystem.instance.readSymLink(link))
    end

    def FileSystem.read_sym_link_sync(link)
      org.vertx.java.core.file.FileSystem.instance.readSymLinkSync(link)
    end

    # Delete a file on the file system, asynchronously.
    # The delete will fail if the file does not exist, or is a directory and is not empty.
    # @param [String] path Path of the file to delete.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.delete(path)
      Future.new(org.vertx.java.core.file.FileSystem.instance.delete(path))
    end

    def FileSystem.delete_sync(path)
      org.vertx.java.core.file.FileSystem.instance.deleteSync(path)
    end

    # Delete a file on the file system recursively, asynchronously.
    # The delete will fail if the file does not exist. If the file is a directory the entire directory contents
    # will be deleted recursively.
    # @param [String] path Path of the file to delete.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.delete_recursive(path)
      Future.new(org.vertx.java.core.file.FileSystem.instance.delete(path, true))
    end

    def FileSystem.delete_recursive_sync(path)
      org.vertx.java.core.file.FileSystem.instance.deleteSync(path, true)
    end

    # Create a directory, asynchronously.
    # The create will fail if the directory already exists, or if it contains parent directories which do not already
    # exist.
    # @param [String] path Path of the directory to create.
    # @param [String] perms. A permission string of the form rwxr-x--- to give directory.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.mkdir(path, perms = nil)
      Future.new(org.vertx.java.core.file.FileSystem.instance.mkdir(path, perms))
    end

    def FileSystem.mkdir_sync(path, perms = nil)
      org.vertx.java.core.file.FileSystem.instance.mkdirSync(path, perms)
    end

    # Create a directory, and create all it's parent directories if they do not already exist, asynchronously.
    # The create will fail if the directory already exists.
    # @param [String] path Path of the directory to create.
    # @param [String] perms. A permission string of the form rwxr-x--- to give the created directory(ies).
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.mkdir_with_parents(path, perms = nil)
      Future.new(org.vertx.java.core.file.FileSystem.instance.mkdir(path, perms, true))
    end

    def FileSystem.mkdir_with_parents_sync(path, perms = nil)
      org.vertx.java.core.file.FileSystem.instance.mkdirSync(path, perms, true)
    end

    # Read a directory, i.e. list it's contents, asynchronously.
    # The read will fail if the directory does not exist.
    # @param [String] path Path of the directory to read.
    # @param [String] filter A regular expression to filter out the contents of the directory. If the filter is not nil
    # then only files which match the filter will be returned.
    # @return [Future] a Future representing the future result of the action. The type of {Future#result} is an Array of String.
    def FileSystem.read_dir(path, filter = nil)
      Future.new(org.vertx.java.core.file.FileSystem.instance.readDir(path, filter))
    end

    def FileSystem.read_dir_sync(path, filter = nil)
      org.vertx.java.core.file.FileSystem.instance.readDirSync(path, filter)
    end

    # Read the contents of an entire file as a {Buffer}, asynchronously.
    # @param [String] path Path of the file to read.
    # @return [Future] a Future representing the future result of the action. The type of {Future#result} is {Buffer}.
    def FileSystem.read_file_as_buffer(path)
      Future.new(org.vertx.java.core.file.FileSystem.instance.readFile(path))
    end

    def FileSystem.read_file_as_buffer_sync(path)
      org.vertx.java.core.file.FileSystem.instance.readFileSync(path)
    end

    # Write a [Buffer] as the entire contents of a file, asynchronously.
    # @param [String] path Path of the file to write.
    # @param [String] buffer The Buffer to write
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.write_buffer_to_file(path, buffer)
      Future.new(org.vertx.java.core.file.FileSystem.instance.writeFile(path, buffer))
    end

    def FileSystem.write_buffer_to_file_sync(path, buffer)
      org.vertx.java.core.file.FileSystem.instance.writeFileSync(path, buffer)
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

    # Open a file on the file system, asynchronously.
    # @param [String] path Path of the file to open.
    # @param [String] perms If the file does not exist and create_new is true, then the file will be created with these permissions.
    # @param [Boolean] read Open the file for reading?
    # @param [Boolean] write Open the file for writing?
    # @param [Boolean] create_new Create the file if it doesn't already exist?
    # @param [Boolean] flush Whenever any data is written to the file, flush all changes to permanent storage immediately?
    # @return [Future] a Future representing the future result of the action. The type of {Future#result} is {AsyncFile}.
    def FileSystem.open(path, perms = nil, read = true, write = true, create_new = true, flush = false)
      Future.new(org.vertx.java.core.file.FileSystem.instance.open(path, perms, read, write, create_new, flush)) { |j_file| AsyncFile.new(j_file)}
    end

    def FileSystem.open_sync(path, perms = nil, read = true, write = true, create_new = true, flush = false)
      j_af = org.vertx.java.core.file.FileSystem.instance.open(path, perms, read, write, create_new, flush)
      AsyncFile.new(j_af)
    end

    # Create a new empty file, asynchronously.
    # @param [String] path Path of the file to create.
    # @param [String] perms The file will be created with these permissions.
    # @return [Future] a Future representing the future result of the action.
    def FileSystem.create_file(path, perms = nil)
      Future.new(org.vertx.java.core.file.FileSystem.instance.createFile(path, perms))
    end

    def FileSystem.create_file_sync(path, perms = nil)
      org.vertx.java.core.file.FileSystem.instance.createFileSync(path, perms)
    end

    # Check if  a file exists, asynchronously.
    # @param [String] path Path of the file to check.
    # @return [Future] a Future representing the future result of the action. The type of {Future#result} is boolean.
    def FileSystem.exists?(path)
      Future.new(org.vertx.java.core.file.FileSystem.instance.exists(path))
    end

    def FileSystem.exists_sync?(path)
      org.vertx.java.core.file.FileSystem.instance.existsSync(path)
    end

    # Get properties for the file system, asynchronously.
    # @param [String] path Path in the file system.
    # @return [Future] a Future representing the future result of the action. The type of {Future#result} is {FSProps}.
    def FileSystem.fs_props(path)
      Future.new(org.vertx.java.core.file.FileSystem.instance.fsProps(path)){ |j_props| FSProps.new(j_props)}
    end

    def FileSystem.fs_props_sync(path)
      j_fsprops = org.vertx.java.core.file.FileSystem.instance.fsProps(path)
      FSProps.new(j_fsprops)
    end

  end
end