/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.mods;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;

/**
 * Mailer Bus Module<p>
 * Please see the busmods manual for a full description<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Mailer extends BusModBase implements Handler<Message<JsonObject>> {

  private Session session;
  private Transport transport;

  private String address;
  private boolean ssl;
  private String host;
  private int port;
  private boolean auth;
  private String username;
  private String password;

  @Override
  public void start() {
    super.start();
    address = getOptionalStringConfig("address", "vertx.mailer");
    ssl = getOptionalBooleanConfig("ssl", false);
    host = getOptionalStringConfig("host", "localhost");
    port = getOptionalIntConfig("port", 25);
    auth = getOptionalBooleanConfig("auth", false);
    username = getOptionalStringConfig("username", null);
    password = getOptionalStringConfig("password", null);

    eb.registerHandler(address, this);

    Properties props = new Properties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.host", host);
    props.put("mail.smtp.socketFactory.port", port);
    if (ssl) {
      props.put("mail.smtp.socketFactory.class",
        "javax.net.ssl.SSLSocketFactory");
    }
    props.put("mail.smtp.socketFactory.fallback", false);
    props.put("mail.smtp.auth", String.valueOf(auth));
    //props.put("mail.smtp.quitwait", "false");

    session = Session.getInstance(props,
        new javax.mail.Authenticator() {
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password);
          }
        });
    //session.setDebug(true);

    try {
      transport = session.getTransport();
      transport.connect();
    } catch (MessagingException e) {
      logger.error("Failed to setup mail transport", e);
    }
  }

  @Override
  public void stop() {
    try {
      transport.close();
    } catch (MessagingException e) {
      logger.error("Failed to stop mail transport", e);
    }
  }

  private InternetAddress[] parseAddresses(Message<JsonObject> message, String fieldName,
                                           boolean required)
  {
    Object oto = message.body.getField(fieldName);
    if (oto == null) {
      if (required) {
        sendError(message, fieldName + " address(es) must be specified");
      }
      return null;
    }
    try {
      InternetAddress[] addresses = null;
      if (oto instanceof String) {
        addresses = InternetAddress.parse((String)oto, true);
      } else if (oto instanceof JsonArray) {
        JsonArray loto = (JsonArray)oto;
        addresses = new InternetAddress[loto.size()];
        int count = 0;
        for (Object addr: loto) {
          if (addr instanceof String == false) {
            sendError(message, "Invalid " + fieldName + " field");
            return null;
          }
          InternetAddress[] ia = InternetAddress.parse((String)addr, true);
          addresses[count++] = ia[0];
        }
      }
      return addresses;
    } catch (AddressException e) {
      sendError(message, "Invalid " + fieldName + " field");
      return null;
    }
  }

  public void handle(Message<JsonObject> message) {

    String from = message.body.getString("from");

    if (from == null) {
      sendError(message, "from address must be specified");
      return;
    }

    InternetAddress fromAddress;
    try {
      fromAddress = new InternetAddress(from, true);
    } catch (AddressException e) {
      sendError(message, "Invalid from address: " + from, e);
      return;
    }

    InternetAddress[] recipients = parseAddresses(message, "to", true);
    if (recipients == null) {
      return;
    }
    InternetAddress[] cc = parseAddresses(message, "cc", false);
    InternetAddress[] bcc = parseAddresses(message, "bcc", false);

    String subject = message.body.getString("subject");
    if (subject == null) {
      sendError(message, "subject must be specified");
      return;
    }
    String body = message.body.getString("body");
    if (body == null) {
      sendError(message, "body must be specified");
      return;
    }

    javax.mail.Message msg = new MimeMessage(session);

    try {
      msg.setFrom(fromAddress);
      msg.setRecipients(javax.mail.Message.RecipientType.TO, recipients);
      msg.setRecipients(javax.mail.Message.RecipientType.CC, cc);
      msg.setRecipients(javax.mail.Message.RecipientType.BCC, bcc);
      msg.setSubject(subject);
      msg.setText(body);
      msg.setSentDate(new Date());
      transport.send(msg);
      sendOK(message);
    } catch (MessagingException e) {

      sendError(message, "Failed to send message", e);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

}
