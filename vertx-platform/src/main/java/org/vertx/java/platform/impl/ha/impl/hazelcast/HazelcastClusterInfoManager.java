package org.vertx.java.platform.impl.ha.impl.hazelcast;

import com.hazelcast.core.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.impl.ClusterManager;
import org.vertx.java.core.eventbus.impl.hazelcast.HazelCastVInstance;
import org.vertx.java.core.eventbus.impl.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.impl.ha.impl.ClusterInfoManager;

import java.util.ArrayList;
import java.util.List;
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
  private int quorumSize;
  private boolean attainedQuorum;
  private Handler<Boolean> quorumHandler;

  public HazelcastClusterInfoManager(ClusterManager cm, int quorumSize) {
    if (cm instanceof HazelcastClusterManager) {
      // If the event is also clustered using Hazelcast we reuse the Hazelcast instance
      this.instance = ((HazelcastClusterManager)cm).getInstance();
    } else {
      this.instance = new HazelCastVInstance().getInstance();
    }
    this.quorumSize = quorumSize;
    instance.getCluster().addMembershipListener(this);
    nodeID = instance.getCluster().getLocalMember().getUuid();
    map = instance.getMap(MAP_NAME);
  }

  public String getNodeID() {
    return nodeID;
  }

  /*
  The quorum handler will be called with true when the quorum is attained
  and false when there is not a quorum any more
   */
  public synchronized void quorumHandler(Handler<Boolean> handler) {
    this.quorumHandler = handler;
    checkQuorum();
  }

  @Override
  public synchronized void leave() {
    if (!map.containsKey(nodeID)) {
      throw new IllegalStateException("member not in map");
    }
    instance.getCluster().removeMembershipListener(this);
    // Remove from the list first - this signifies a clean close not a crash
    map.remove(nodeID);
  }

  @Override
  public synchronized void simulateCrash() {
    if (!map.containsKey(nodeID)) {
      throw new IllegalStateException("member not in map");
    }
    instance.getCluster().removeMembershipListener(this);
    instance.getLifecycleService().kill();
  }

  @Override
  public synchronized void update(JsonObject clusterInfo) {
    map.put(nodeID, clusterInfo);
    System.out.println("node " + nodeID + " putting cluster info");
  }

  @Override
  public synchronized void memberAdded(MembershipEvent membershipEvent) {
    checkQuorum();
  }

  @Override
  public synchronized void memberRemoved(MembershipEvent membershipEvent) {
    checkQuorum();
    if (attainedQuorum) {
      Member member = membershipEvent.getMember();
      System.out.println("node " + nodeID + " received notification of member removed, node: " + member.getUuid());
      String failedNodeID = member.getUuid();
      JsonObject clusterInfo = map.get(failedNodeID);
      if (clusterInfo == null) {
        // Clean close - do nothing
      } else {
        checkFailover(failedNodeID, clusterInfo);
      }
    }
  }

  private void checkQuorum() {
    boolean attained = instance.getCluster().getMembers().size() >= quorumSize;
    if (quorumHandler != null) {
      if (!attainedQuorum && attained) {
        quorumHandler.handle(true);
      } else if (attainedQuorum && !attained) {
        quorumHandler.handle(false);
      }
    }
    this.attainedQuorum = attained;
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
        String group = app.getString("group");
        String moduleName = app.getString("module_name");
        String chosen = chooseHashedNode(group, moduleName.hashCode());
        if (chosen != null && chosen.equals(this.nodeID)) {
          System.out.println("node " + nodeID + " is handling failure of app " + moduleName + " from node " + failedNodeID);
          handler.handle(app);
          break;
        }
      }
    }
  }

  private String chooseHashedNode(String group, int hashCode) {
    Set<Member> members = instance.getCluster().getMembers();
    ArrayList<String> matchingMembers = new ArrayList<>();
    for (Member member: members) {
      JsonObject clusterInfo = map.get(member.getUuid());
      if (clusterInfo == null) {
        throw new IllegalStateException("Can't find member in map");
      }
      String memberGroup = clusterInfo.getString("group");
      if (group.equals(memberGroup)) {
        matchingMembers.add(member.getUuid());
      }
    }
    if (!matchingMembers.isEmpty()) {
      int pos = hashCode % matchingMembers.size();
      return matchingMembers.get(pos);
    } else {
      return null;
    }
  }

  @Override
  public void crashHandler(Handler<JsonObject> handler) {
    this.handler = handler;
  }
}
