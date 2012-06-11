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

import datetime
import core.streams
import org.vertx.java.core
import org.vertx.java.deploy.impl.VertxLocator

from core.buffer import Buffer

__author__ = "Scott Horn"
__email__ = "scott@hornmicro.com"

class FileProps(object):
    """Represents the properties of a file on the file system"""

    def __init__(self, java_obj):
        self.java_obj = java_obj

    @property
    def creation_time(self):
        """return [Time] The creation time of the file."""
        return datetime.datetime.fromtimestamp(self.java_obj.creationTime.getTime() / 1000)

    @property
    def last_access_time(self):
        """return [Time] The last access time of the file."""
        return datetime.datetime.fromtimestamp(self.java_obj.lastAccessTime.getTime() / 1000)        

    @property
    def last_modified_time(self):
        """return The last modified time of the file."""
        return datetime.datetime.fromtimestamp(self.java_obj.lastModifiedTime.getTime() / 1000)        
    
    @property 
    def directory(self):
        """return is the file a directory"""
        return self.java_obj.isDirectory()

    @property
    def other(self):
        """return Is the file some other file type?"""
        return self.java_obj.isOther()

    @property
    def regular_file(self):
        """returns   Is it a regular file?"""
        return self.java_obj.isRegularFile()

    @property
    def symbolic_link(self): 
        """returns is it a symbolic link?"""
        return self.java_obj.isSymbolicLink()

    @property
    def size(self):
        """returnsthe size of the file, in bytes."""
        return self.java_obj.size

class FSProps(object):
    """Represents the properties of a file system"""
  
    def __init__(self, java_obj):
        self.java_obj = java_obj
    
    @property
    def total_space(self): 
        """returns  the total space on the file system, in bytes."""
        return self.java_obj.totalSpace()

    @property
    def unallocated_space(self): 
        """returns unallocated space on the file system, in bytes."""
        return self.java_obj.unallocatedSpace()

    @property
    def usable_space(self): 
        """returns usable space on the file system, in bytes."""
        return self.java_obj.usableSpace()

class AsyncFile(object):
    """Represents a file on the file-system which can be read from, or written to asynchronously.
    Methods also exist to get a read stream or a write stream on the file. This allows the data to be pumped to and from
    other streams, e.g. an HttpClientRequest instance, using the Pump class
    """
    def __init__(self, java_obj):
        self.java_obj = java_obj

    def close(self, handler):
        """Close the file, asynchronously."""
        self.java_obj.close(FSWrappedHandler(handler))


    def write(self, buffer, position, handler):
        """Write a Buffer to the file, asynchronously.
        When multiple writes are invoked on the same file
        there are no guarantees as to order in which those writes actually occur.

        Keyword arguments
        buffer -- the buffer to write
        position -- the position in the file where to write the buffer. Position is measured in bytes and
        starts with zero at the beginning of the file.
        """
        self.java_obj.write(buffer._to_java_buffer(), position, FSWrappedHandler(handler))

    def read(self, buffer, offset, position, length, handler):
        """Reads some data from a file into a buffer, asynchronously.
        When multiple reads are invoked on the same file
        there are no guarantees as to order in which those reads actually occur.

        Keyword arguments  
        buffer -- the buffer into which the data which is read is written.
        offset -- the position in the buffer where to start writing the data.
        position -- the position in the file where to read the data.
        length -- the number of bytes to read.
        """
        def converter(buffer):
            return Buffer(buffer)
        self.java_obj.read(buffer._to_java_buffer(), offset, position, length, FSWrappedHandler(handler, converter))

    @property  
    def write_stream(self): 
        """returns a write stream operating on the file."""
        return AsyncFileWriteStream(self.java_obj.getWriteStream())

    @property
    def read_stream(self): 
        """returns  [ReadStream] A read stream operating on the file."""
        return AsyncFileReadStream(self.java_obj.getReadStream())

    def flush(self): 
        """Flush any writes made to this file to underlying persistent storage, asynchronously.
        If the file was opened with flush set to true then calling this method will have no effect.
        Keyword arguments

        handler -- the handler which is called on completion.
        """
        Future(self.java_obj.flush())

class AsyncFileWriteStream(core.streams.WriteStream):
    def __init__(self, java_obj):
        self.java_obj = java_obj

class AsyncFileReadStream(core.streams.ReadStream):
    def __init__(self, java_obj):
        self.java_obj = java_obj

class FSWrappedHandler(org.vertx.java.core.AsyncResultHandler):
    def __init__(self, handler, result_converter=None):
        self.handler = handler
        self.result_converter = result_converter

    def handle(self, async_result):
        if not (self.handler is None):
            if async_result.exception is None:
                if self.result_converter is None:
                    self.handler(None, async_result.result)
                else:
                    self.handler(None, self.result_converter(async_result.result))
            else:
                self.handler(async_result.exception, None)

class FileSystem(object):
    """Represents the file-system and contains a broad set of operations for manipulating files.
    An asynchronous and a synchronous version of each operation is provided.
    The asynchronous versions take a handler as a final argument which is
    called when the operation completes or an error occurs. The handler is called
    with two arguments; the first an exception, this will be nil if the operation has
    succeeded. The second is the result - this will be nil if the operation failed or
    there was no result to return.
    The synchronous versions return the results, or throw exceptions directly."""

    @staticmethod
    def java_file_system():
        return org.vertx.java.deploy.impl.VertxLocator.vertx.fileSystem()
    
    @staticmethod
    def copy(frm, to, handler):
        """Copy a file, asynchronously. The copy will fail if from does not exist, or if to already exists.

        Keyword arguments
        frm -- path of file to copy
        to -- path of file to copy to
        handler -- the handler which is called on completion."""
        FileSystem.java_file_system().copy(frm, to, FSWrappedHandler(handler))

    @staticmethod
    def copy_sync(frm, to):
        """Synchronous version of FileSystem.copy"""
        FileSystem.java_file_system().copySync(frm, to)

    @staticmethod 
    def copy_recursive(frm, to, handler):
        """Copy a file recursively, asynchronously. The copy will fail if from does not exist, or if to already exists and is not empty.
        If the source is a directory all contents of the directory will be copied recursively, i.e. the entire directory
        tree is copied.

        Keyword arguments
        frm -- path of file to copy
        to -- path of file to copy to
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().copy(frm, to, True, FSWrappedHandler(handler))

    @staticmethod 
    def copy_recursive_sync(frm, to):
        """Synchronous version of FileSystem.copy_recursive"""
        FileSystem.java_file_system().copySync(frm, to, True)

    @staticmethod 
    def move(frm, to, handler):
        """Move a file, asynchronously. The move will fail if from does not exist, or if to already exists.

        Keyword arguments
        frm -- Path of file to move
        to -- Path of file to move to
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().move(frm, to, FSWrappedHandler(handler))

    @staticmethod 
    def move_sync(frm, to):
        """Synchronous version of FileSystem.move"""
        FileSystem.java_file_system().moveSync(frm, to)

    @staticmethod 
    def truncate(path, len, handler):
        """Truncate a file, asynchronously. The move will fail if path does not exist.

        Keyword arguments
        path -- Path of file to truncate
        len -- Length to truncate file to. Will fail if len < 0. If len > file size then will do nothing.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().truncate(path, len, FSWrappedHandler(handler))

    @staticmethod 
    def truncate_sync(path, len):
        """Synchronous version of FileSystem.truncate"""
        FileSystem.java_file_system().truncateSync(path, len)

    @staticmethod 
    def chmod(path, perms, dir_perms=None, handler=None):
        """Change the permissions on a file, asynchronously. If the file is directory then all contents will also have their permissions changed recursively.

        Keyword arguments
          path Path of file to change permissions
          perms A permission string of the form rwxr-x--- as specified in http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html}. This is
        used to set the permissions for any regular files (not directories).
          dir_perms A permission string of the form rwxr-x---. Used to set permissions for regular files.
          handler -- the function to call when complete
        """
        FileSystem.java_file_system().chmod(path, perms, dir_perms, FSWrappedHandler(handler))

    @staticmethod 
    def chmod_sync(path, perms, dir_perms=None):
        """Synchronous version of FileSystem.chmod"""
        FileSystem.java_file_system().chmodSync(path, perms, dir_perms)

    @staticmethod 
    def props(path, handler):
        """Get file properties for a file, asynchronously.

        Keyword arguments
        path -- path to file
        handler -- the function to call when complete
        """
        def converter(obj):
            return FileProps(obj)
        return FileSystem.java_file_system().props(path, FSWrappedHandler(handler, converter))

    @staticmethod 
    def props_sync(path):
        """Synchronous version of FileSystem.props"""
        java_obj = FileSystem.java_file_system().propsSync(path)
        return FileProps(java_obj)

    @staticmethod 
    def link(link, existing, handler):
        """Create a hard link, asynchronously..

        Keyword arguments
        link -- path of the link to create.
        existing -- path of where the link points to.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().link(link, existing, FSWrappedHandler(handler))


    @staticmethod 
    def link_sync(link, existing):
        """Synchronous version of FileSystem.link"""
        FileSystem.java_file_system().linkSync(link, existing)

    @staticmethod 
    def sym_link(link, existing, handler):
        """Create a symbolic link, asynchronously.

        Keyword arguments
        link -- Path of the link to create.
        existing -- Path of where the link points to.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().symLink(link, existing, FSWrappedHandler(handler))

    @staticmethod 
    def sym_link_sync(link, existing):
        """Synchronous version of FileSystem.sym_link"""
        FileSystem.java_file_system().symLinkSync(link, existing)

    @staticmethod 
    def unlink(link, handler):
        """Unlink a hard link.

        Keyword arguments
        link -- path of the link to unlink.
        """
        FileSystem.java_file_system().unlink(link, FSWrappedHandler(handler))

    @staticmethod 
    def unlinkSync(link):
        """Synchronous version of FileSystem.unlink"""
        FileSystem.java_file_system().unlinkSync(link)

    @staticmethod 
    def read_sym_link(link, handler):
        """Read a symbolic link, asynchronously. I.e. tells you where the symbolic link points.

        Keyword arguments
        link -- path of the link to read.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().readSymLink(link, FSWrappedHandler(handler))

    @staticmethod 
    def read_sym_link_sync(link):
        """Synchronous version of FileSystem.read_sym_link"""
        FileSystem.java_file_system().readSymLinkSync(link)

    @staticmethod 
    def delete(path, handler):
        """Delete a file on the file system, asynchronously.
        The delete will fail if the file does not exist, or is a directory and is not empty.

        Keyword arguments
        path -- path of the file to delete.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().delete(path, FSWrappedHandler(handler))

    @staticmethod 
    def delete_sync(path):
        """Synchronous version of FileSystem.delete"""
        FileSystem.java_file_system().deleteSync(path)

    @staticmethod 
    def delete_recursive(path, handler):
        """Delete a file on the file system recursively, asynchronously.
        The delete will fail if the file does not exist. If the file is a directory the entire directory contents
        will be deleted recursively.

        Keyword arguments
        path -- path of the file to delete.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().delete(path, True, FSWrappedHandler(handler))

    @staticmethod 
    def delete_recursive_sync(path):
        """Synchronous version of FileSystem.delete_recursive"""
        FileSystem.java_file_system().deleteSync(path, true)

    @staticmethod 
    def mkdir(path, perms=None, handler=None):
        """Create a directory, asynchronously.
        The create will fail if the directory already exists, or if it contains parent directories which do not already
        exist.

        Keyword arguments
        path -- path of the directory to create.
        perms -- a permission string of the form rwxr-x--- to give directory.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().mkdir(path, perms, FSWrappedHandler(handler))

    @staticmethod 
    def mkdir_sync(path, perms=None):
        """Synchronous version of FileSystem.mkdir"""
        FileSystem.java_file_system().mkdirSync(path, perms)

    @staticmethod 
    def mkdir_with_parents(path, perms=None, handler=None):
        """Create a directory, and create all it's parent directories if they do not already exist, asynchronously.
        The create will fail if the directory already exists.

        Keyword arguments
        path -- path of the directory to create.
        perms -- a permission string of the form rwxr-x--- to give the created directory(ies).
        """
        FileSystem.java_file_system().mkdir(path, perms, True, FSWrappedHandler(handler))

    @staticmethod 
    def mkdir_with_parents_sync(path, perms=None):
        """Synchronous version of FileSystem.mkdir_with_parents"""
        FileSystem.java_file_system().mkdirSync(path, perms, true)

    @staticmethod 
    def read_dir(path, filter=None, handler=None):
        """Read a directory, i.e. list it's contents, asynchronously.
        The read will fail if the directory does not exist.

        Keyword arguments
        path -- path of the directory to read.
        filter -- a regular expression to filter out the contents of the directory. If the filter is not nil
        then only files which match the filter will be returned.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().readDir(path, filter, FSWrappedHandler(handler))
    
    @staticmethod 
    def read_dir_sync(path, filter=None):
        """Synchronous version of FileSystem.read_dir"""
        FileSystem.java_file_system().readDirSync(path, filter)

    @staticmethod 
    def read_file_as_buffer(path, handler):
        """Read the contents of an entire file as a Buffer, asynchronously.

        Keyword arguments
        path -- path of the file to read.
        handler -- the function to call when complete
        """
        def converter(buffer):
            return Buffer(buffer)
        FileSystem.java_file_system().readFile(path, FSWrappedHandler(handler, converter))

    @staticmethod 
    def read_file_as_buffer_sync(path):
        """Synchronous version of FileSystem.read_file_as_buffer"""
        FileSystem.java_file_system().readFileSync(path)

    @staticmethod 
    def write_buffer_to_file(path, buffer, handler):
        """Write a  as the entire contents of a file, asynchronously.

        Keyword arguments
        path -- path of the file to write.
        buffer -- the Buffer to write
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().writeFile(path, buffer, FSWrappedHandler(handler))

    @staticmethod 
    def write_buffer_to_file_sync(path, buffer):
        """Synchronous version of FileSystem.write_buffer_to_file"""
        FileSystem.java_file_system().writeFileSync(path, buffer)

    @staticmethod 
    def open(path, perms=None, read=True, write=True, create_new=True, flush=False, handler=None):
        """Open a file on the file system, asynchronously.

        Keyword arguments
        path -- path of the file to open.
        perms -- if the file does not exist and create_new is true, then the file will be created with these permissions.
        read -- open the file for reading?
        write -- open the file for writing?
        create_new -- Create the file if it doesn't already exist?
        flush -- whenever any data is written to the file, flush all changes to permanent storage immediately?
        handler -- the function to call when complete
        """
        def converter(file):
            return AsyncFile(file)
        FileSystem.java_file_system().open(path, perms, read, write, create_new, flush, FSWrappedHandler(handler, converter))

    @staticmethod 
    def open_sync(path, perms=None, read=True, write=True, create_new=True, flush=False):
        """Synchronous version of FileSystem.open"""
        java_obj = FileSystem.java_file_system().open(path, perms, read, write, create_new, flush)
        return AsyncFile(java_obj)

    @staticmethod 
    def create_file(path, perms=None, handler=None):
        """Create a new empty file, asynchronously.

        Keyword arguments
        path -- path of the file to create.
        perms -- the file will be created with these permissions.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().createFile(path, perms, FSWrappedHandler(handler))

    @staticmethod 
    def create_file_sync(path, perms=None):
        """Synchronous version of FileSystem.create_file"""
        FileSystem.java_file_system().createFileSync(path, perms)

    @staticmethod 
    def exists(path, handler):
        """Check if  a file exists, asynchronously.

        Keyword arguments
        path -- Path of the file to check.
        handler -- the function to call when complete
        """
        FileSystem.java_file_system().exists(path, FSWrappedHandler(handler))

    @staticmethod 
    def exists_sync(path):
        """Synchronous version of FileSystem.exists"""
        return FileSystem.java_file_system().existsSync(path)

    @staticmethod 
    def fs_props(path, handler):
        """Get properties for the file system, asynchronously.

        Keyword arguments
        path -- Path in the file system.
        handler -- the function to call when complete
        """
        def converter(props):
            return FSProps(props)
        FileSystem.java_file_system().fsProps(path, FSWrappedHandler(handler, converter))

    @staticmethod 
    def fs_props_sync(path):
        """Synchronous version of FileSystem.fs_props"""
        j_fsprops = FileSystem.java_file_system().fsPropsSync(path)
        return FSProps(j_fsprops)
