package org.vertx.java.testframework;
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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** For tracking down Heisenbugs that only appear when running CI and disappear when you add normal logging
 *
 */
public class EventLog {

  public static void addEvent(String event) {
    instance.paddEvent(event);
  }

  public static void clear() {
    instance.pclear();
  }

  public static void dump() {
    instance.pdump();
  }

  private static final EventLog instance = new EventLog();

  private final Queue<String> events = new ConcurrentLinkedQueue<String>();

  private EventLog() {
  }

  private void paddEvent(String event) {
    events.add(event);
  }

  private void pclear() {
    events.clear();
  }

  private void pdump() {
    System.out.println("Dump of test events");
    System.out.println("===================");
    for (String event: events) {
      System.out.println(event);
    }
    System.out.println("===================");
  }
}
