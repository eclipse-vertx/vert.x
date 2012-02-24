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

  def Vertx.deploy_verticle(main, config = nil, instances = 1, &block)
    if config
      json_str = JSON.generate(config)
      config = new org.vertx.java.core.json.JsonObject(json_str)
    end
    org.vertx.java.core.Vertx.instance.deployVerticle(main, config, instances, block)
  end

  def Vertx.deploy_worker_verticle(main, config = nil, instances = 1, &block)
    if config
      json_str = JSON.generate(config)
      config = new org.vertx.java.core.json.JsonObject(json_str)
    end
    org.vertx.java.core.Vertx.instance.deployWorkerVerticle(main, config, instances, block)
  end

  def Vertx.undeploy_verticle(id)
    org.vertx.java.core.Vertx.instance.undeployVerticle(id)
  end

  def Vertx.config
    if !@@j_conf
      @@j_conf = org.vertx.java.core.Vertx.instance.getConfig
      @@j_conf = JSON.parse(@@j_conf.encode) if @@j_conf
    end
    @@j_conf
  end

  def Vertx.exit
    org.vertx.java.core.Vertx.instance.exit
  end

end
