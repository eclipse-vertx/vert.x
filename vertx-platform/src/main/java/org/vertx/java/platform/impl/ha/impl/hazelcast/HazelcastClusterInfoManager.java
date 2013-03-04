package org.vertx.java.platform.impl.ha.impl.hazelcast;

import com.hazelcast.core.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.impl.ClusterManager;
import org.vertx.java.core.eventbus.impl.hazelcast.HazelCastVInstance;
import org.vertx.java.core.eventbus.impl.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.impl.ha.impl.ClusterInfoManager;

import java.util.Set;

/*
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
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterInfoManager implements ClusterInfoManager, MembershipListener {

  private static final String MAP_NAME = "__vertx.clusterMap";

  private HazelcastInstance instance;

  private IMap<String, JsonObject> map;
  private Handler<JsonObject> handler;
  private String nodeID;

  public HazelcastClusterInfoManager(ClusterManager cm) {
    if (cm instanceof HazelcastClusterManager) {
      // If the event is also clustered using Hazelcast we reuse the Hazelcast instance
      this.instance = ((HazelcastClusterManager)cm).getInstance();
    } else {
      this.instance = new HazelCastVInstance().getInstance();
    }
    instance.getCluster().addMembershipListener(this);
    nodeID = instance.getCluster().getLocalMember().getUuid();
    map = instance.getMap(MAP_NAME);
  }

  public String getNodeID() {
    return nodeID;
  }

  @Override
  public void leave() {
    if (!map.containsKey(nodeID)) {
      throw new IllegalStateException("member not in map");
    }
    instance.getCluster().removeMembershipListener(this);
    // Remove from the list first - this signifies a clean close not a crash
    map.remove(nodeID);
  }

  @Override
  public void simulateCrash() {
    if (!map.containsKey(nodeID)) {
      throw new IllegalStateException("member not in map");
    }
    instance.getCluster().removeMembershipListener(this);
    instance.getLifecycleService().kill();
  }

  @Override
  public void update(JsonObject clusterInfo) {
    map.put(nodeID, clusterInfo);
  }

  @Override
  public void memberAdded(MembershipEvent membershipEvent) {
    // Do nothing
  }

  @Override
  public void memberRemoved(MembershipEvent membershipEvent) {
    Member member = membershipEvent.getMember();
    String failedNodeID = member.getUuid();
    JsonObject clusterInfo = map.get(failedNodeID);
    if (clusterInfo == null) {
      // Clean close - do nothing
    } else {
      checkFailover(failedNodeID, clusterInfo);
    }
  }

  /*
  We work out which node will take over the failed node - the results of this calculation
  should be exactly the same on every node, so only one node will actually take it on
  Consequently it's crucial that the calculation done in chooseHashedNode takes place only locally
   */
  private void checkFailover(String failedNodeID, JsonObject clusterInfo) {
    JsonArray apps = clusterInfo.getArray("mods");
    if (apps != null) {
      for (Object obj: apps) {
        JsonObject app = (JsonObject)obj;
        String chosen = chooseHashedNode(failedNodeID.hashCode());
        if (chosen.equals(this.nodeID)) {
          handler.handle(app);
        }
      }
    }
  }

  private String chooseHashedNode(int hashCode) {
    Set<Member> members = instance.getCluster().getMembers();
    int size = members.size();
    int pos = hashCode % size;
    int count = 0;
    Member chosen = null;
    for (Member member: members) {
      if (count == pos) {
        chosen = member;
        break;
      }
    }
    if (chosen == null) {
      throw new IllegalStateException("Can't find member");
    }
    return chosen.getUuid();
  }

  @Override
  public void crashHandler(Handler<JsonObject> handler) {
    this.handler = handler;
  }
}
