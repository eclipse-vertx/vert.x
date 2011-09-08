/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.addons.amqp;

import com.rabbitmq.client.AMQP;

import java.util.HashMap;
import java.util.Map;

public class AmqpProps {
  public String appId;
  public int classId = -1;
  public String className;
  public String clusterId;
  public String contentEncoding;
  public String contentType;
  public String correlationId;
  public int deliveryMode = 1;
  public String expiration;
  public Map<String, Object> headers = new HashMap();
  public String messageId;
  public int priority = 4;
  public String replyTo;
  public long timestamp;
  public String type;
  public String userId;

  public AMQP.BasicProperties toBasicProperties() {
    return new AMQP.BasicProperties(contentType, contentEncoding, headers,
        deliveryMode, priority, correlationId, replyTo, expiration, messageId, new java.util.Date(timestamp),
        type, userId, appId, clusterId);
  }

  public AmqpProps() {
  }

  AmqpProps(AMQP.BasicProperties props) {
    this.appId = props.getAppId();
    this.classId = props.getClassId();
    this.className = props.getClassName();
    this.clusterId = props.getClusterId();
    this.contentEncoding = props.getContentEncoding();
    this.contentType = props.getContentType();
    this.correlationId = props.getCorrelationId();
    this.deliveryMode = props.getDeliveryMode() == null ? 1 : props.getDeliveryMode();
    this.expiration = props.getExpiration();
    if (props.getHeaders() != null) {
      this.headers.putAll(props.getHeaders());
    }
    this.messageId = props.getMessageId();
    this.priority = props.getPriority() == null ? 0 : props.getPriority();
    this.replyTo = props.getReplyTo();
    this.timestamp = props.getTimestamp() == null ? 0 : props.getTimestamp().getTime();
    this.type = props.getType();
    this.userId = props.getUserId();
  }


}
