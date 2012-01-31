package vertx.tests.core.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TLSTestParams implements Serializable {
  public final boolean clientCert;
  public final boolean clientTrust;
  public final boolean serverCert;
  public final boolean serverTrust;
  public final boolean requireClientAuth;
  public final boolean clientTrustAll;
  public final boolean shouldPass;

  public TLSTestParams(boolean clientCert, boolean clientTrust, boolean serverCert, boolean serverTrust,
                       boolean requireClientAuth, boolean clientTrustAll, boolean shouldPass) {
    this.clientCert = clientCert;
    this.clientTrust = clientTrust;
    this.serverCert = serverCert;
    this.serverTrust = serverTrust;
    this.requireClientAuth = requireClientAuth;
    this.clientTrustAll = clientTrustAll;
    this.shouldPass = shouldPass;
  }

  public static TLSTestParams deserialize(byte[] serialized) {
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
      ObjectInputStream oos = new ObjectInputStream(bais);
      return (TLSTestParams)oos.readObject();
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Failed to deserialise:" + e.getMessage());
    }
  }

  public byte[] serialize() {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(this);
      oos.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Failed to serialise:" + e.getMessage());
    }
  }
}
