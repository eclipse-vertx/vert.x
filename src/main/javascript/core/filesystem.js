/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var vertx = vertx || {};

if (!vertx.FileSystem) {
  vertx.FileSystem = {};

  (function() {
    var j_fs = org.vertx.java.core.file.FileSystem.instance;

    function wrapHandler(handler) {
      return function(asyncResult) {
        if (!asyncResult.exception) {
          handler(null, asyncResult.result);
        } else {
          handler(asyncResult.exception, null);
        }
      }
    }

    function wrapPropsHandler(handler) {
      return function(asyncResult) {
        if (!asyncResult.exception) {
          var jsProps = convertProps(asyncResult.result);
          handler(null, jsProps);
        } else {
          handler(asyncResult.exception, null);
        }
      }
    }

    function convertProps(j_props) {
      var jsProps = {
        creationTime: j_props.creationTime.getTime(),
        lastAccessTime: j_props.lastAccessTime.getTime(),
        lastModifiedTime: j_props.lastModifiedTime.getTime(),
        isDirectory: j_props.isDirectory,
        isOther: j_props.isOther,
        isRegularFile: j_props.isRegularFile,
        isSymbolicLink: j_props.isSymbolicLink,
        size: j_props.size
      }
      return jsProps;
    }

    vertx.FileSystem.copy = function(from, to, arg2, arg3) {
      var handler;
      var recursive;
      if (arguments.length === 4) {
        handler = arg3;
        recursive = arg2;
      } else {
        handler = arg2;
        recursive = false;
      }
      j_fs.copy(from, to, recursive, wrapHandler(handler));
    }

    vertx.FileSystem.copySync = function(from, to, recursive) {
      if (!recursive) recursive = false;
      j_fs.copySync(from, to, recursive);
    }

    vertx.FileSystem.move = function(from, to, handler) {
      j_fs.move(from, to, wrapHandler(handler));
    }

    vertx.FileSystem.moveSync = function(from, to) {
      j_fs.moveSync(from, to);
    }

    vertx.FileSystem.truncate = function(path, len, handler) {
      j_fs.truncate(path, len, wrapHandler(handler));
    }

    vertx.FileSystem.truncateSync = function(path, len) {
      j_fs.truncateSync(path, len);
    }

    vertx.FileSystem.chmod = function(path, perms, arg2, arg3) {
      var handler;
      var dirPerms;
      if (arguments.length === 4) {
        handler = arg3;
        dirPerms = arg2;
      } else {
        handler = arg2;
        dirPerms = null;
      }
      j_fs.chmod(path, perms, dirPerms, wrapHandler(handler));
    }

    vertx.FileSystem.chmodSync = function(path, perms, dirPerms) {
      if (!dirPerms) dirPerms = null;
      j_fs.chmodSync(path, perms, dirPerms);
    }

    vertx.FileSystem.props = function(path, handler) {
      j_fs.props(path, wrapPropsHandler(handler));
    }

    vertx.FileSystem.propsSync = function(path) {
      var j_props = j_fs.propsSync(path);
      return convertProps(j_props);
    }

    vertx.FileSystem.lprops = function(path, handler) {
      j_fs.lprops(path, wrapPropsHandler(handler));
    }

    vertx.FileSystem.lpropsSync = function(path) {
      var j_props = j_fs.lpropsSync(path);
      return convertProps(j_props);
    }

    vertx.FileSystem.link = function(link, existing, handler) {
      j_fs.link(link, existing, wrapHandler(handler));
    }

    vertx.FileSystem.linkSync = function(link, existing) {
      j_fs.linkSync(link, existing);
    }

    vertx.FileSystem.symlink = function(link, existing, handler) {
      j_fs.symlink(link, existing, wrapHandler(handler));
    }

    vertx.FileSystem.symlinkSync = function(link, existing) {
      j_fs.symlinkSync(link, existing);
    }

    vertx.FileSystem.unlink = function(link, handler) {
      j_fs.unlink(link, wrapHandler(handler));
    }

    vertx.FileSystem.unlinkSync = function(link) {
      j_fs.unlinkSync(link);
    }

    vertx.FileSystem.readSymlink = function(link, handler) {
      j_fs.readSymlink(link, wrapHandler(handler));
    }

    vertx.FileSystem.readSymlinkSync = function(link, handler) {
      return j_fs.readSymlinkSync(link);
    }

    vertx.FileSystem.delete = function(path, arg1, arg2) {
      var handler;
      var recursive;
      if (arguments.length === 3) {
        handler = arg2;
        recursive = arg1;
      } else {
        handler = arg1;
        recursive = false;
      }
      j_fs.delete(path, recursive, wrapHandler(handler));
    }

    vertx.FileSystem.deleteSync = function(path, recursive) {
      if (!recursive) recursive = false;
      j_fs.deleteSync(path, recursive);
    }

    vertx.FileSystem.mkDir = function(path, arg1, arg2, arg3) {
      var createParents;
      var perms;
      var handler;
      switch (arguments.length) {
        case 2:
          createParents = false;
          perms = null;
          handler = arg1;
          break;
        case 3:
          createParents = arg2;
          perms = null;
          handler = arg2;
          break;
        case 4:
          createParents = arg1;
          perms = arg2;
          handler = arg3;
          break;
        default:
          throw 'Invalid number of arguments';
      }
      j_fs.mkdir(path, perms, createParents, wrapHandler(handler));
    }

    vertx.FileSystem.mkDirSync = function(path, arg1, arg2) {
      var createParents;
      var perms;
      switch (arguments.length) {
        case 2:
          createParents = false;
          perms = null;
          break;
        case 2:
          createParents = arg1;
          perms = null;
          break;
        case 3:
          createParents = arg1;
          perms = arg2;
          break;
        default:
          throw 'Invalid number of arguments';
      }
      j_fs.mkdirSync(path, perms, createParents);
    }

    vertx.FileSystem.readDir = function(path, arg1, arg2) {
      var filter;
      var handler;
      if (arguments.length === 3) {
        handler = arg2;
        filter = arg1;
      } else {
        handler = arg1;
        filter = null;
      }
      j_fs.readDir(path, filter, wrapHandler(handler));
    }

    vertx.FileSystem.readDirSync = function(path, filter) {
      if (!filter) filter = false;
      return j_fs.readDirSync(path, filter);
    }

    vertx.FileSystem.readFile = function(path, handler) {
      j_fs.readFile(path, wrapHandler(handler));
    }

    vertx.FileSystem.readFileSync = function(path) {
      return j_fs.readFileSync(path);
    }

    vertx.FileSystem.writeFile = function(path, data, handler) {
      if (typeof data === 'string') {
        data = new org.vertx.java.core.buffer.Buffer(data);
      }
      j_fs.writeFile(path, data, wrapHandler(handler));
    }

    vertx.FileSystem.writeFileSync = function(path, data) {
      if (typeof data === 'string') {
        data = new org.vertx.java.core.buffer.Buffer(data);
      }
      j_fs.writeFileSync(path, data);
    }

    vertx.FileSystem.OPEN_READ = 1;
    vertx.FileSystem.OPEN_WRITE = 2;
    vertx.FileSystem.CREATE_NEW = 4;

    vertx.FileSystem.openSync = function(path, arg1, arg2, arg3) {

      // TODO combine this code with the similar code in open
      var openFlags;
      var flush;
      var perms;
      var handler;
      switch (arguments.length) {
        case 1:
          openFlags = vertx.FileSystem.OPEN_READ | vertx.FileSystem.OPEN_WRITE
                    | vertx.FileSystem.CREATE_NEW;
          flush = false;
          perms = null;
          break;
        case 2:
          openFlags = arg1;
          flush = false;
          perms = null;
          break;
        case 3:
          openFlags = arg1;
          flush = arg2;
          perms = null;
          break;
        case 4:
          openFlags = arg1;
          flush = arg2;
          perms = arg3;
          break;
        default:
          throw 'Invalid number of arguments';
      }

      var read = (openFlags & vertx.FileSystem.OPEN_READ) == vertx.FileSystem.OPEN_READ;
      var write = (openFlags & vertx.FileSystem.OPEN_WRITE) == vertx.FileSystem.OPEN_WRITE;
      var createNew = (openFlags & vertx.FileSystem.CREATE_NEW) == vertx.FileSystem.CREATE_NEW;

      var asyncFile = j_fs.openSync(path, perms, read, write, createNew, flush);

      return wrapAsyncFile(asyncFile);
    }

    vertx.FileSystem.open = function(path, arg1, arg2, arg3, arg4) {

      var openFlags;
      var flush;
      var perms;
      var handler;
      switch (arguments.length) {
        case 2:
          openFlags = vertx.FileSystem.OPEN_READ | vertx.FileSystem.OPEN_WRITE
                    | vertx.FileSystem.CREATE_NEW;
          flush = false;
          perms = null;
          handler = arg1;
          break;
        case 3:
          openFlags = arg1;
          flush = false;
          perms = null;
            handler = arg2;
          break;
        case 4:
          openFlags = arg1;
          flush = arg2;
          perms = null;
          handler = arg3;
          break;
        case 5:
          openFlags = arg1;
          flush = arg2;
          perms = arg3;
          handler = arg4;
          break;
        default:
          throw 'Invalid number of arguments';
      }

      var read = (openFlags & vertx.FileSystem.OPEN_READ) == vertx.FileSystem.OPEN_READ;
      var write = (openFlags & vertx.FileSystem.OPEN_WRITE) == vertx.FileSystem.OPEN_WRITE;
      var createNew = (openFlags & vertx.FileSystem.CREATE_NEW) == vertx.FileSystem.CREATE_NEW;

      j_fs.open(path, perms, read, write, createNew, flush, function(asyncResult) {
        if (!asyncResult.exception) {
          var j_af = asyncResult.result;
          var wrapped = wrapAsyncFile(j_af);
          handler(null, wrapped);
        } else {
          handler(asyncResult.exception, null);
        }
      });
    }

    function wrapAsyncFile(j_af) {
      return {
        close: function(handler) {
          if (handler) {
            j_af.close(wrapHandler(handler))
          } else {
            j_af.close();
          }
        },

        write: function(buffer, position, handler) {
          j_af.write(buffer, position, wrapHandler(handler));
        },

        read: function(buffer, offset, position, length, handler) {
          j_af.read(buffer, offset, position, length, wrapHandler(handler));
        },

        getWriteStream: function() {
          return j_af.getWriteStream();
        },

        getReadStream: function() {
          return j_af.getReadStream();
        },

        flush: function(handler) {
          if (handler) {
            j_af.flush(wrapHandler(handler));
          } else {
            j_af.flush();
          }
        }
      }
    }

    vertx.FileSystem.createFile = function(path, handler) {
      j_fs.createFile(path, wrapHandler(handler));
    }

    vertx.FileSystem.createFileSync = function(path) {
      j_fs.createFileSync(path);
    }

    vertx.FileSystem.exists = function(path, handler) {
      j_fs.exists(path, wrapHandler(handler));
    }

    vertx.FileSystem.existsSync = function(path) {
      return j_fs.existsSync(path);
    }

    vertx.FileSystem.fsProps = function(path, handler) {
      j_fs.fsProps(path, wrapHandler(handler));
    }

    vertx.FileSystem.fsPropsSync = function(path) {
      return j_fs.fsPropsSync(path);
    }

  })();
}