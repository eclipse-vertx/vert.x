# Copyright 2011 VMware, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'core/streams'
require 'core/composition'

module Nodex

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

    # Close the file
    # The actual close will happen asynchronously, and the handler will be called
    # when the operation is complete, or if the operation fails.
    # This method must be called using the same event loop the file was opened from.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def close
      Future.new(@j_file.close)
    end

    def close_deferred
      Deferred.new(@j_file.closeDeferred)
    end

    # Write a {Buffer} to the file.
    # The actual write will happen asynchronously, and the handler will be called
    # when the operation is complete, or, if the operation fails. When multiple writes are invoked on the same file
    # there are no guarantees as to order in which those writes actually occur.
    # This method must be called using the same event loop the file was opened from.
    # @param [Buffer] buffer The buffer to write
    # @param [FixNum] position The position in the file where to write the buffer. Position is measured in bytes and
    # starts with zero at the beginning of the file.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def write(buffer, position)
      Future.new(@j_file.write(buffer._to_java_buffer, position))
    end

    def write_deferred(buffer, position)
      Deferred.new(@j_file.writeDeferred(buffer._to_java_buffer, position))
    end

    # Reads some data from a file into a buffer.
    # The actual write will happen asynchronously, and the handler will be called
    # when the operation is complete, or, if the operation fails.  When multiple reads are invoked on the same file
    # there are no guarantees as to order in which those reads actually occur.
    # This method must be called using the same event loop the file was opened from.
    # @param [Buffer] buffer The buffer into which the data which is read is written.
    # @param [FixNum] offset The position in the buffer where to start writing the data.
    # @param [FixNum] position The position in the file where to read the data.
    # @param [FixNum] length The number of bytes to read.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def read(buffer, offset, position, length, &hndlr)
      Future.new(@j_file.read(buffer._to_java_buffer, offset, position, length)){ |j_buff| Buffer.new(j_buff) }
    end

    def read_deferred(buffer, offset, position, length, &hndlr)
      Deferred.new(@j_file.readDeferred(buffer._to_java_buffer, offset, position, length)){ |j_buff| Buffer.new(j_buff) }
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

    # Flush any writes made to this file to underlying persistent storage.
    # If the file was opened with flush set to true then calling this method will have no effect.
    # The actual flush will happen asynchronously, and the handler will be called
    # when the operation is complete, or, if the operation fails.
    # This method must be called using the same event loop the file was opened from.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def flush
      Future.new(@j_file.flush)
    end

    def flush_deferred
      Deferred.new(@j_file.flushDeferred)
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

    # Copy a file. The copy will fail if from does not exist, or if to already exists.
    # The actual copy will happen asynchronously, and the handler will be called
    # when the operation is complete, or, if the operation fails.
    # @param [String] from Path of file to copy
    # @param [String] to Path of file to copy to
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.copy(from, to)
      Future.new(org.nodex.java.core.file.FileSystem.instance.copy(from, to))
    end

    def FileSystem.copy_deferred(from, to)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.copyDeferred(from, to))
    end

    # Copy a file recursively. The copy will fail if from does not exist, or if to already exists and is not empty.
    # If the source is a directory all contents of the directory will be copied recursively, i.e. the entire directory
    # tree is copied.
    # The actual copy will happen asynchronously, and the handler will be called
    # when the operation is complete, or, if the operation fails.
    # @param [String] from Path of file to copy
    # @param [String] to Path of file to copy to
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.copy_recursive(from, to)
      Future.new(org.nodex.java.core.file.FileSystem.instance.copy(from, to, true))
    end

    def FileSystem.copy_recursive_deferred(from, to)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.copyDeferred(from, to, true))
    end

    # Move a file. The move will fail if from does not exist, or if to already exists.
    # The actual move will happen asynchronously, and the handler will be called
    # when the operation is complete, or, if the operation fails.
    # @param [String] from Path of file to move
    # @param [String] to Path of file to move to
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.move(from, to)
      Future.new(org.nodex.java.core.file.FileSystem.instance.move(from, to))
    end

    def FileSystem.move_deferred(from, to)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.moveDeferred(from, to))
    end

    # Truncate a file. The move will fail if path does not exist.
    # The actual truncate will happen asynchronously, and the handler will be called
    # when the operation is complete, or, if the operation fails.
    # @param [String] path Path of file to truncate
    # @param [FixNum] len Length to truncate file to. Will fail if len < 0. If len > file size then will do nothing.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.truncate(path, len)
      Future.new(org.nodex.java.core.file.FileSystem.instance.truncate(path, len))
    end

    def FileSystem.truncate_deferred(path, len)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.truncateDeferred(path, len))
    end

    # Change the permissions on a file. If the file is directory then all contents will also have their permissions changed recursively.
    # The actual chmod will happen asynchronously, and the handler will be called
    # when the operation is complete, or, if the operation fails.
    # @param [String] path Path of file to change permissions
    # @param [String] perms A permission string of the form rwxr-x--- as specified in
    # {http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html}. This is
    # used to set the permissions for any regular files (not directories).
    # @param [String] dir_perms A permission string of the form rwxr-x---. Used to set permissions for regular files.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.chmod(path, perms, dir_perms = nil)
      Future.new(org.nodex.java.core.file.FileSystem.instance.chmod(path, perms, dir_perms))
    end

    def FileSystem.chmod_deferred(path, perms, dir_perms = nil)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.chmodDeferred(path, perms, dir_perms))
    end

    # Get file properties for a file. The properties are obtained asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] path Path to file
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.props(path)
      Future.new(org.nodex.java.core.file.FileSystem.instance.props(path)) { |j_props| FileProps.new(j_props) }
    end

    def FileSystem.props_deferred(path)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.propsDeferred(path)) { |j_props| FileProps.new(j_props) }
    end

    # Create a hard link. The link is done asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] link Path of the link to create.
    # @param [String] existing Path of where the link points to.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.link(link, existing)
      Future.new(org.nodex.java.core.file.FileSystem.instance.link(link, existing))
    end

    def FileSystem.link_deferred(link, existing)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.linkDeferred(link, existing))
    end

    # Create a symbolic link. The link is done asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] link Path of the link to create.
    # @param [String] existing Path of where the link points to.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.sym_link(link, existing)
      Future.new(org.nodex.java.core.file.FileSystem.instance.symLink(link, existing))
    end

    def FileSystem.sym_link_deferred(link, existing)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.symLinkDeferred(link, existing))
    end

    # Unlink a hard link. The unlink is done asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] link Path of the link to unlink.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.unlink(link)
      Future.new(org.nodex.java.core.file.FileSystem.instance.unlink(link))
    end

    def FileSystem.unlink_deferred(link)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.unlinkDeferred(link))
    end

    # Read a symbolic link. I.e. tells you where the symbolic link points.
    # The read is done asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] link Path of the link to read.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.read_sym_link(link)
      Future.new(org.nodex.java.core.file.FileSystem.instance.readSymLink(link))
    end

    def FileSystem.read_sym_link_deferred(link)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.readSymLinkDeferred(link))
    end

    # Delete a file on the file system.
    # The delete will fail if the file does not exist, or is a directory and is not empty.
    # The delete is done asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] path Path of the file to delete.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.delete(path)
      Future.new(org.nodex.java.core.file.FileSystem.instance.delete(path))
    end

    def FileSystem.delete_deferred(path)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.deleteDeferred(path))
    end

    # Delete a file on the file system, recursively.
    # The delete will fail if the file does not exist. If the file is a directory the entire directory contents
    # will be deleted recursively.
    # The delete is done asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] path Path of the file to delete.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.delete_recursive(path)
      Future.new(org.nodex.java.core.file.FileSystem.instance.delete(path, true))
    end

    def FileSystem.delete_recursive_deferred(path)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.deleteDeferred(path, true))
    end

    # Create a directory.
    # The create will fail if the directory already existsl, or it contains parent directories which do not already
    # exist.
    # The create is done asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] path Path of the directory to create.
    # @param [String] perms. A permission string of the form rwxr-x--- to give directory.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.mkdir(path, perms = nil)
      Future.new(org.nodex.java.core.file.FileSystem.instance.mkdir(path, perms))
    end

    def FileSystem.mkdir_deferred(path, perms = nil)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.mkdir(path, perms))
    end

    # Create a directory, and create all it's parent directories if they do not already exist.
    # The create will fail if the directory already exists.
    # The create is done asynchronously and the handler will be called when
    # the operation is complete, or if the operation fails.
    # @param [String] path Path of the directory to create.
    # @param [String] perms. A permission string of the form rwxr-x--- to give the created directory(ies).
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.mkdir_with_parents(path, perms = nil)
      Future.new(org.nodex.java.core.file.FileSystem.instance.mkdir(path, perms, true))
    end

    def FileSystem.mkdir_with_parents_deferred(path, perms = nil)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.mkdirDeferred(path, perms, true))
    end

    # Read a directory, i.e. list it's contents
    # The read will fail if the directory does not exist.
    # The read is done asynchronously and the handler will be called when
    # the operation is complete with an array of Strings representing the paths of all the files in the directory.
    # @param [String] path Path of the directory to read.
    # @param [String] filter A regular expression to filter out the contents of the directory. If the filter is not nil
    # then only files which match the filter will be returned.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.read_dir(path, filter = nil)
      Future.new(org.nodex.java.core.file.FileSystem.instance.readDir(path, filter))
    end

    def FileSystem.read_dir_deferred(path, filter = nil)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.readDirDeferred(path, filter))
    end

    # Read the contents of an entire file as a String.
    # The read is done asynchronously and the handler will be called when
    # the operation is complete or if the operation failed.
    # @param [String] path Path of the file to read.
    # @param [String] encoding Encoding to assume when decoding the bytes to a String
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.read_file_as_string(path, encoding = "UTF-8")
      Future.new(org.nodex.java.core.file.FileSystem.instance.readFileAsString(path, encoding))
    end

    def FileSystem.read_file_as_string_deferred(path, encoding = "UTF-8")
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.readFileAsStringDeferred(path, encoding))
    end

    # Write a String as the entire contents of a file.
    # The write is done asynchronously and the handler will be called when
    # the operation is complete or if the operation failed.
    # @param [String] path Path of the file to write.
    # @param [String] str The String to write
    # @param [String] encoding Encoding to assume when endoding the String to bytes
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.write_string_to_file(path, str, encoding = "UTF-8")
      Future.new(org.nodex.java.core.file.FileSystem.instance.writeFileAsString(path, str, encoding))
    end

    def FileSystem.write_string_to_file_deferred(path, str, encoding = "UTF-8")
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.writeFileAsStringDeferred(path, str, encoding))
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

    # Open a file on the file system, returning a handle to a {AsyncFile} asynchronously.
    # The open is done asynchronously and the handler will be called when
    # the operation is complete or if the operation failed.
    # @param [String] path Path of the file to open.
    # @param [String] perms If the file does not exist and create_new is true, then the file will be created with these permissions.
    # @param [Boolean] read Open the file for reading?
    # @param [Boolean] write Open the file for writing?
    # @param [Boolean] create_new Create the file if it doesn't already exist?
    # @param [Boolean] flush Whenever any data is written to the file, flush all changes to permanent storage immediately?
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.open(path, perms = nil, read = true, write = true, create_new = true, flush = false)
      Future.new(org.nodex.java.core.file.FileSystem.instance.open(path, perms, read, write, create_new, flush)) { |j_file| AsyncFile.new(j_file)}
    end

    def FileSystem.open_deferred(path, perms = nil, read = true, write = true, create_new = true, flush = false)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.openDeferred(path, perms, read, write, create_new, flush)) { |j_file| AsyncFile.new(j_file)}
    end

    # Create a new empty file.
    # The create is done asynchronously and the handler will be called when
    # the operation is complete or if the operation failed.
    # @param [String] path Path of the file to create.
    # @param [String] perms The file will be created with these permissions.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.create_file(path, perms = nil)
      Future.new(org.nodex.java.core.file.FileSystem.instance.createFile(path, perms))
    end

    def FileSystem.create_file_deferred(path, perms = nil)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.createFileDeferred(path, perms))
    end

    # Does a file exist?
    # The check is done asynchronously and the handler will be called when
    # the operation is complete or if the operation failed.
    # @param [String] path Path of the file to check.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.exists(path)
      Future.new(org.nodex.java.core.file.FileSystem.instance.exists(path))
    end

    def FileSystem.exists_deferred(path)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.existsDeferred(path))
    end

    # Get properties for the file system.
    # The properties are obtained asynchronously and the handler will be called when
    # the operation is complete or if the operation failed.
    # @param [String] path Path in the file system.
    # @param [Block] hndlr a block representing the handler which is called on completion.
    def FileSystem.fs_props(path, &hndlr)
      Future.new(org.nodex.java.core.file.FileSystem.instance.getFSProps(path)){ |j_props| FSProps.new(j_props)}
    end

    def FileSystem.fs_props_deferred(path, &hndlr)
      Deferred.new(org.nodex.java.core.file.FileSystem.instance.getFSPropsDeferred(path)){ |j_props| FSProps.new(j_props)}
    end
  end
end