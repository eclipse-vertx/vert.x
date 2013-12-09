package org.vertx.java.spi.cluster.impl.hazelcast;/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

import com.hazelcast.config.Config;
import org.vertx.java.core.spi.VertxSPI;
import org.vertx.java.core.spi.cluster.ClusterManager;
import org.vertx.java.core.spi.cluster.ClusterManagerFactory;

/**
 * Class to allow the programmatic configuration of a Hazelcast cluster in vert.x embedded.
 *
 * @author <a href="http://www.p14n.com">Dean Pehrsson-Chapman</a>
 */
public class ProgrammableClusterManagerFactory implements ClusterManagerFactory {

    private static Config config;

    @Override
    public ClusterManager createClusterManager(VertxSPI vertx) {
        return new HazelcastClusterManager(vertx){
            @Override
            protected Config getConfig() {
                return config;
            }
        };
    }

    /**
     * Returns a mutable Config object.  Call this method before creating the PlatformManager.
     */
    public static Config getConfig() {
        if(config==null) {
            System.setProperty("vertx.clusterManagerFactory", ProgrammableClusterManagerFactory.class.getName());
            config = new Config();
        }
        return config;
    }
}