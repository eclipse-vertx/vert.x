package org.vertx.java.tests;

import org.vertx.java.core.Immutable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TLSTestParams implements Immutable {
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
}
