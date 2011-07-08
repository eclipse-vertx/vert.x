package org.nodex.core.amqp;

import com.rabbitmq.client.AMQP;

import java.util.HashMap;
import java.util.Map;

/**
 * User: tfox
 * Date: 07/07/11
 * Time: 14:31
 */
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
  public Map<String, Object> headers = new HashMap<String, Object>();
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
