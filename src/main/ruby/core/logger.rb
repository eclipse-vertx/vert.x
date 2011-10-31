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


module Vertx

  # @author {http://tfox.org Tim Fox}
  class Logger

    def Logger.get_logger(clazz)
      raise "Please provide a class to get_logger method" if !clazz.is_a? Class
      Logger.new(org.vertx.java.core.logging.Logger.getLogger(clazz))
    end

    def initialize(j_del)
      @j_del = j_del
    end

    def info_enabled?
      @j_del.infoEnabled
    end

    def debug_enabled?
      @j_del.debugEnabled
    end

    def trace_enabled?
      @j_del.traceEnabled
    end

    def fatal

    end

    def error(msg, ex = nil)

      if ex == nil
        @j_del.error(msg)
      elsif ex.is_a? java.lang.Throwable
        @j_del.error(msg, ex)
      elsif ex.is_a? Exception
        @j_del.error(obj.message)
        trace = ''
        obj.backtrace.each { |line| trace << line << "\n" }
        @j_del.error(trace)
      end
    end


    def warn

    end

    def info

    end

    def trace

    end



  end
end