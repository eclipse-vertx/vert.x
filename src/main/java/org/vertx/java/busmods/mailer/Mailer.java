package org.vertx.java.busmods.mailer;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.VertxApp;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Mailer extends BusModBase implements VertxApp, Handler<Message> {

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
    this(address, host, 25, false, false, null, null);
  }

  public Mailer(String address, String host, int port) {
    this(address, host, port, false, false, null, null);
  }

  public Mailer(String address, String host, int port, boolean ssl,boolean auth, String username, String password) {
    super(address, true);
    this.ssl = ssl;
    this.host = host;
    this.port = port;
    this.auth = auth;
    this.username = username;
    this.password = password;
  }

  @Override
  public void start() {
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
      log.error("Failed to setup mail transport", e);
    }
  }

  @Override
  public void stop() {
    eb.unregisterHandler(address, this);

    try {
      transport.close();
    } catch (MessagingException e) {
      log.error("Failed to stop mail transport", e);
    }
  }

  private InternetAddress[] parseAddresses(Message message, Map<String, Object> json, String fieldName,
                                           boolean required)
  {
    Object oto = json.get(fieldName);
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
      } else if (oto instanceof List) {
        List loto = (List)oto;
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

  public void handle(Message message) {
    Map<String, Object> json = helper.toJson(message);

    String from = (String)json.get("from");

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

    InternetAddress[] recipients = parseAddresses(message, json, "to", true);
    if (recipients == null) {
      return;
    }
    InternetAddress[] cc = parseAddresses(message, json, "cc", false);
    InternetAddress[] bcc = parseAddresses(message, json, "bcc", false);

    String subject = (String)json.get("subject");
    String body = (String)json.get("body");

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
    }
  }

}
