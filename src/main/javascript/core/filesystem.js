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

    function wrapHandler(handler, fut) {
      fut.handler(function() {
        if (fut.succeeded()) {
          handler(null, fut.result());
        } else {
          handler(fut.exception(), null);
        }
      });
    }

    function wrapProps(handler, fut) {
      fut.handler(function() {
        if (fut.succeeded()) {
          var j_res = fut.result();
          var jsProps = {
            creationTime: j_res.creationTime.getTime(),
            lastAccessTime: j_res.lastAccessTime.getTime(),
            lastModifiedTime: j_res.lastModifiedTime.getTime(),
            isDirectory: j_res.isDirectory,
            isOther: j_res.isOther,
            isRegularFile: j_res.isRegularFile,
            isSymbolicLink: j_res.isSymbolicLink
          }
          handler(null, jsProps);
        } else {
          handler(fut.exception(), null);
        }
      });
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
      var fut = j_fs.copy(from, to, recursive);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.move = function(from, to, handler) {
      var fut = j_fs.move(from, to);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.truncate = function(path, len, handler) {
      var fut = j_fs.truncate(path, len);
      wrapHandler(handler, fut);
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
      var fut = j_fs.chmod(path, perms, dirPerms);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.props = function(path, handler) {
      var fut = j_fs.props(path);
      wrapProps(handler, fut);
    }

    vertx.FileSystem.lprops = function(path, handler) {
      var fut = j_fs.lprops(path);
      wrapProps(handler, fut);
    }

    vertx.FileSystem.link = function(link, existing, handler) {
      var fut = j_fs.link(link, existing);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.symlink = function(link, existing, handler) {
      var fut = j_fs.symlink(link, existing);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.unlink = function(link, handler) {
      var fut = j_fs.unlink(link);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.readSymlink = function(link, handler) {
      var fut = j_fs.readSymlink(link);
      wrapHandler(handler, fut);
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
      var fut = j_fs.delete(path, recursive);
      wrapHandler(handler, fut);
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
      var fut = j_fs.mkdir(path, perms, createParents);
      wrapHandler(handler, fut);
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
      var fut = j_fs.readDir(path, filter);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.readFile = function(path, handler) {
      var fut = j_fs.readFile(path);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.writeFile = function(path, data, handler) {
      if (typeof data === 'string') {
        data = org.vertx.java.core.buffer.Buffer.create(data);
      }
      var fut = j_fs.writeFile(path, data);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.OPEN_READ = 1;
    vertx.FileSystem.OPEN_WRITE = 2;
    vertx.FileSystem.CREATE_NEW = 4;

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

      var fut = j_fs.open(path, perms, read, write, createNew, flush);

      fut.handler(function() {
        if (fut.succeeded()) {
          var j_af = fut.result();

          var wrapped = {
            close: function(handler) {
              if (handler) {
                j_af.closeDeferred().handler(handler).execute();
              } else {
                j_af.close();
              }
            },

            write: function(buffer, position, handler) {
              var fut = j_af.write(buffer, position);
              wrapHandler(handler, fut);
            },

            read: function(buffer, offset, position, length, handler) {
              var fut = j_af.read(buffer, offset, position, length);
              wrapHandler(handler, fut);
            },

            getWriteStream: function() {
              return j_af.getWriteStream();
            },

            getReadStream: function() {
              return j_af.getReadStream();
            },

            flush: function(handler) {
              if (handler) {
                j_af.flushDeferred().handler(handler).execute();
              } else {
                j_af.flush();
              }
            }
          }

          handler(null, wrapped);
        } else {
          handler(fut.exception(), null);
        }
      });
    }

    vertx.FileSystem.createFile = function(path, handler) {
      var fut = j_fs.createFile(path);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.exists = function(path, handler) {
      var fut = j_fs.exists(path);
      wrapHandler(handler, fut);
    }

    vertx.FileSystem.fsProps = function(path, handler) {
      var fut = j_fs.fsProps(path);
      wrapHandler(handler, fut);
    }

  })();
}