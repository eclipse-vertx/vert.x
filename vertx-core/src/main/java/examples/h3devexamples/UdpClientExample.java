package examples.h3devexamples;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Base64;

public class UdpClientExample {
  public static void main(String[] args) {
    String host = "localhost";
    int port = 8090;

    try {
      // Create UDP socket
      DatagramSocket socket = new DatagramSocket();

      // Generate AES key
      SecretKey secretKey = generateAESKey();
      String encryptedMessage = encryptMessage("Hello, encrypted UDP Server!", secretKey);

      // Send the encrypted message
      DatagramPacket packet = new DatagramPacket(encryptedMessage.getBytes(), encryptedMessage.length(), InetAddress.getByName(host), port);
      socket.send(packet);
      System.out.println("Encrypted message sent");

      socket.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // AES encryption method
  private static String encryptMessage(String message, SecretKey key) throws Exception {
    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    byte[] encryptedBytes = cipher.doFinal(message.getBytes());
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  // Generate AES secret key
  private static SecretKey generateAESKey() throws Exception {
    KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
    keyGenerator.init(128); // AES key size
    return keyGenerator.generateKey();
  }
}
