package org.vertx.java.busmods.mailer;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Mailer extends BusModBase {

  private static final Logger log = Logger.getLogger(Mailer.class);

  private Session session;
  private Transport transport;

  private boolean ssl;
  private String host;
  private int port;
  private boolean auth;
  private String username;
  private String password;

  public Mailer(String address, String host) {
    this(address, false, host, 25, false, null, null);
  }

  public Mailer(String address, String host, int port) {
    this(address, false, host, port, false, null, null);
  }

  public Mailer(String address, boolean ssl, String host, int port, boolean auth, String username, String password) {
    super(address);
    this.ssl = ssl;
    this.host = host;
    this.port = port;
    this.auth = auth;
    this.username = username;
    this.password = password;
  }

  @Override
  public void start() {
    super.start();

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
      log.error("Failed to setup mail transport", e);
    }
  }

  @Override
  public void stop() {
    try {
      transport.close();
    } catch (MessagingException e) {
      log.error("Failed to stop mail transport", e);
    }

    super.stop();
  }

  public void handle(Message message, Map<String, Object> json) {

    String from = (String)json.get("from");
    String to = (String)json.get("to");
    String subject = (String)json.get("subject");
    String body = (String)json.get("body");

    javax.mail.Message msg = new MimeMessage(session);

    try {

      try {
        msg.setFrom(new InternetAddress(from));
      } catch (AddressException e) {
        sendError(message, "Invalid from address: " + from, e);
        return;
      }
      try {
        msg.setRecipients(javax.mail.Message.RecipientType.TO,  InternetAddress.parse(to, true));
      } catch (AddressException e) {
        sendError(message, "Invalid recipients: " + to, e);
        return;
      }

      msg.setSubject(subject);
      msg.setText(body);
      msg.setSentDate(new Date());

      transport.send(msg);

      sendOK(message);

    } catch (MessagingException e) {
      sendError(message, "Failed to send message", e);
    }
  }

  private void sendOK(Message message) {
    log.info("sending ok");
    Map<String, Object> json = new HashMap<>();
    json.put("status", "ok");
    helper.sendReply(message, json);
  }

  private void sendError(Message message, String error, Exception e) {
    log.info("sending error: " + error);
    log.trace(error, e);
    Map<String, Object> json = new HashMap<>();
    json.put("status", "error");
    json.put("message", error);
    helper.sendReply(message, json);
  }

}
