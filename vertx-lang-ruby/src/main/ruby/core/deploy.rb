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

  # Deploy a verticle. The actual deploy happens asynchronously
  # @param main [String] The main of the verticle to deploy
  # @param config [Hash] JSON configuration for the verticle
  # @param instances [FixNum] Number of instances to deploy
  # @param block [Block] Block will be executed when deploy has completed
  # @return [String] Unique id of deployment
  def Vertx.deploy_verticle(main, config = nil, instances = 1, &block)
    if config
      json_str = JSON.generate(config)
      config = org.vertx.java.core.json.JsonObject.new(json_str)
    end
    org.vertx.java.deploy.impl.VertxLocator.container.deployVerticle(main, config, instances, block)
  end

  # Deploy a workerverticle. The actual deploy happens asynchronously
  # @param main [String] The main of the verticle to deploy
  # @param config [Hash] JSON configuration for the verticle
  # @param instances [FixNum] Number of instances to deploy
  # @param block [Block] Block will be executed when deploy has completed
  def Vertx.deploy_worker_verticle(main, config = nil, instances = 1, &block)
    if config
      json_str = JSON.generate(config)
      config = org.vertx.java.core.json.JsonObject.new(json_str)
    end
    org.vertx.java.deploy.impl.VertxLocator.container.deployWorkerVerticle(main, config, instances, block)
  end

  # Undeploy a verticle
  # @param id [String] The unique id of the deployment
  def Vertx.undeploy_verticle(id)
    org.vertx.java.deploy.impl.VertxLocator.container.undeployVerticle(id)
  end

  # Cause the container to exit
  def Vertx.exit
    org.vertx.java.deploy.impl.VertxLocator.container.exit
  end

  # Get config for the verticle
  # @return [Hash] The JSON config for the verticle
  def Vertx.config
    if !defined? @@j_conf
      @@j_conf = org.vertx.java.deploy.impl.VertxLocator.container.getConfig
      @@j_conf = JSON.parse(@@j_conf.encode) if @@j_conf
    end
    @@j_conf
  end

end
