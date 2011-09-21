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

module Nodex

  class Future

    # @private
    def initialize(j_del, &result_converter)
      @j_del = j_del
      @result_converter = result_converter
    end

    def result
      if !@result_converted
        j_result = @j_del.result
        if (j_result == nil)
          @result = nil
        else
          if (@result_converter == nil)
            @result = j_result
          else
            @result = @result_converter.call(j_result)
          end
          @result_converted = true
        end
      end
      @result
    end

    def exception
      @j_del.exception
    end

    def complete?
      @j_del.complete
    end

    def succeeded?
      @j_del.succeeded
    end

    def failed?
      @j_del.failed
    end

    def handler(proc = nil, &hndlr)
      hndlr = proc if proc
      @j_del.handler{ |j_def|
        hndlr.call(self)
      }
    end

    # @private
    def _to_j_del
      @j_del
    end

  end

  class Deferred < Future

    def initialize(j_del, &result_converter)
      super(j_del, &result_converter)
      @j_del = j_del
    end

    def execute
      @j_del.execute
    end

    # @private
    def _to_j_del
      @j_del
    end

  end

  class Composer
    def initialize
      @j_comp = org.nodex.java.core.composition.Composer.new
    end

    def parallel(deferred)
      check_deferred(deferred)
      @j_comp.parallel(deferred._to_j_del)
      deferred
    end

    def series(deferred)
      check_deferred(deferred)
      @j_comp.series(deferred._to_j_del)
      deferred
    end

    def check_deferred(deferred)
      raise "Please specify an instance of Nodex::Deferred" if !deferred.is_a? Deferred
    end

    private :check_deferred
  end

end

