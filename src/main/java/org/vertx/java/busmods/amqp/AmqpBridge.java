package org.vertx.java.busmods.amqp;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Prototype for AMQP bridge
 * Currently only does pub/sub and does not declare exchanges so only works with default exchanges
 * Three operations:
 * 1) Create a consumer on a topic given exchange name (use amqp.topic) and routing key (topic name)
 * 2) Close a consumer
 * 3) Send message
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AmqpBridge extends BusModBase {

  private String uri;

  private ConnectionFactory factory;
  private Connection conn;
  private EventBus eb;
  private Map<Long, Channel> consumerChannels = new HashMap<>();
  private long consumerSeq;
  private Queue<Channel> availableChannels = new LinkedList<>();

  public AmqpBridge() {
    super(false);
  }

  /**
   * Start the busmod
   */
  public void start() {
    super.start();

    eb = vertx.eventBus();
    this.uri = super.getMandatoryStringConfig("uri");

    try {
      factory = new ConnectionFactory();
      factory.setUri(uri);
      conn = factory.newConnection();

      eb.registerHandler(address + ".create-consumer", new Handler<Message<JsonObject>>() {

        public void handle(Message<JsonObject> message) {
          String exchange = message.body.getString("exchange");
          String routingKey = message.body.getString("routing_key");
          String forwardAddress = message.body.getString("forward");
          long id = createConsumer(exchange, routingKey, forwardAddress);
          message.reply(new JsonObject().putNumber("id", id));
        }
      });

      eb.registerHandler(address + ".close-consumer", new Handler<Message<JsonObject>>() {

        public void handle(Message<JsonObject> message) {
          long id = (Long)message.body.getNumber("id");
          closeConsumer(id);
        }
      });

      eb.registerHandler(address + ".send", new Handler<Message<JsonObject>>() {

        public void handle(Message<JsonObject> message) {
          String exchange = message.body.getString("exchange");
          String routingKey = message.body.getString("routing_key");
          String body = message.body.getString("body");
          send(exchange, routingKey, body);
        }
      });
    } catch (Exception e) {
      container.getLogger().error("Failed to create connection", e);
    }
  }

  /**
  Stop the busmod
   */
  public void stop() {
    consumerChannels.clear();
    try {
      conn.close();
    } catch (Exception e) {
      container.getLogger().error("Failed to close", e);
    }
  }

  private Channel getChannel() throws Exception {
    if (!availableChannels.isEmpty()) {
      return availableChannels.remove();
    } else {
      return conn.createChannel();
    }
  }

  private void send(String exchangeName, String routingKey, String message) {
    Channel channel = null;
    try {
      channel = getChannel();
      channel.basicPublish(exchangeName, routingKey, null, message.getBytes("UTF-8"));
    } catch (Exception e) {
      container.getLogger().error("Failed to send", e);
    } finally {
      availableChannels.add(channel);
    }
  }

  private long createConsumer(String exchangeName, String routingKey, final String forwardAddress) {
    try {
      // URRG! AMQP is so clunky :(
      // all this code just to set up a pub/sub consumer
      final Channel channel = getChannel();
      String queueName = channel.queueDeclare().getQueue();
      channel.queueBind(queueName, exchangeName, routingKey);
      Consumer cons = new DefaultConsumer(channel) {
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
          long deliveryTag = envelope.getDeliveryTag();
          eb.send(forwardAddress, new String(body));
          channel.basicAck(deliveryTag, false);
        }
      };
      channel.basicConsume(queueName, cons);
      long id = consumerSeq++;
      consumerChannels.put(id, channel);
      return id;
    } catch (Exception e) {
      container.getLogger().error("Failed to create consumer", e);
      return -1;
    }
  }

  private void closeConsumer(long id) {
    try {
      Channel channel = consumerChannels.remove(id);
      if (channel != null) {
        availableChannels.add(channel);
      }
    } catch (Exception e) {
      e.printStackTrace();
      // TODO better exception handling
    }
  }
}
