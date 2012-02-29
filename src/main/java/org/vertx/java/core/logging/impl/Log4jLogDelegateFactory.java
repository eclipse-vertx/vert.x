/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.logging.impl;

/**
 * A {@link LogDelegateFactory} which creates {@link Log4jLogDelegate} instances.
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 *
 *
 */
public class Log4jLogDelegateFactory implements LogDelegateFactory
{
   public LogDelegate createDelegate(final String name)
   {
      return new Log4jLogDelegate(name);
   }

}
