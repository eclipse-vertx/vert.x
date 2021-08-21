package io.vertx.core.net;

import io.vertx.core.Vertx;
import io.vertx.core.net.impl.KeyStoreHelper;
import sun.security.x509.X500Name;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * This class is a simple implementation of {@link KeyCertOptions} & {@link TrustOptions}.
 *
 * The other implementations provided by vert.x work on filenames for the keystore,
 * however it's simpler to just pass in a KeyStore which is what this class operates on.
 */
public class StaticKeyStoreOptions implements KeyCertOptions, TrustOptions, Cloneable {

  private final String password;
  private final KeyStore keyStore;
  private final KeyManagerFactory keyManagerFactory;

  //our collected map of aliases/server names to X509KeyManagers
  //case insensitive because the key is hostname which is also case insensitive
  private final Map<String, X509KeyManager> x509KeyManagers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final Map<String, TrustManagerFactory> trustManagers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  private TrustManagerFactory trustManagerFactory;

  /**
   * Create a StaticKeyStore Options.
   * @param keyStore The key store that contains the private key and public keys.
   * @param password The password for the keystore; may be null.
   */
  public StaticKeyStoreOptions(KeyStore keyStore, String password) {
    try {
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm()
      );
      keyManagerFactory.init(keyStore, password != null ? password.toCharArray() : null);
      this.keyManagerFactory = keyManagerFactory;
      this.keyStore = keyStore;
      this.password = password;
      initialize();
    } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
      throw new IllegalArgumentException("Invalid keystore provided.", e);
    }
  }

  private StaticKeyStoreOptions(KeyStore keyStore, String password, KeyManagerFactory keyManagerFactory) {
    this.keyManagerFactory = keyManagerFactory;
    this.keyStore = keyStore;
    this.password = password;
    initialize();
  }

  private void initialize() {
    try {
      trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keyStore);

      //iterate over all the aliases in our KeyStore and for each one grab the certificate chain
      Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();

        //fetch the private key associated to this alias
        PrivateKey key = (PrivateKey) keyStore.getKey(alias, password != null ? password.toCharArray() : null);
        if (key == null) {
          continue;
        }

        //the certificate chain (ordered with the user's certificate first
        //and the root certificate authority last), or null if the alias can't be found.
        X509Certificate[] certificateChain = (X509Certificate[]) keyStore.getCertificateChain(alias);
        if (certificateChain == null || certificateChain.length == 0) {
          continue;
        }
        //there must be at least one in the chain!
        X509Certificate certificate = certificateChain[0];

        //store only the certificate chain in a new keystore
        final KeyStore trustManagerKeyStore = KeyStoreHelper.createEmptyKeyStore();
        int index = 1;
        for (X509Certificate cert : certificateChain) {
          trustManagerKeyStore.setCertificateEntry(String.format("cert-%s", index++), cert);
        }
        TrustManagerFactory trustManagerFactory
          = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustManagerKeyStore);
        trustManagers.put(alias, trustManagerFactory);

        //create our custom X509KeyManager for SNI handling
        X509KeyManager x509KeyManager = new X509KeyManager() {
          @Override
          public String[] getClientAliases(String s, Principal[] principals) {
            throw new UnsupportedOperationException();
          }

          @Override
          public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
            throw new UnsupportedOperationException();
          }

          @Override
          public String[] getServerAliases(String s, Principal[] principals) {
            throw new UnsupportedOperationException();
          }

          @Override
          public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
            throw new UnsupportedOperationException();
          }

          @Override
          public X509Certificate[] getCertificateChain(String s) {
            return certificateChain;
          }

          @Override
          public PrivateKey getPrivateKey(String s) {
            return key;
          }
        };

        String commonName = X500Name.asX500Name(certificate.getSubjectX500Principal()).getCommonName();
        x509KeyManagers.put(commonName, x509KeyManager);

        //subject alternative names  encoded as a two elements List with elem(0) representing object id
        // and elem(1) representing object (subject alternative name) itself.
        Collection<List<?>> subjectAltNames = certificate.getSubjectAlternativeNames();
        if (subjectAltNames != null) {
          for (final List<?> subjectAltName : subjectAltNames) {
            assert subjectAltName.size() == 2 : "The subject alternative names have incorrect size";
            String name = subjectAltName.get(1).toString();
            x509KeyManagers.put(name, x509KeyManager);
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failure setting up StaticKeyStoreKeyCertOptions.", e);
    }
  }

  @Override
  public StaticKeyStoreOptions clone() {
    return new StaticKeyStoreOptions(keyStore, password, keyManagerFactory);
  }

  @Override
  public KeyManagerFactory getKeyManagerFactory(Vertx vertx) {
    return keyManagerFactory;
  }

  @Override
  public Function<String, X509KeyManager> keyManagerMapper(Vertx vertx) {
    return x509KeyManagers::get;
  }

  @Override
  public TrustManagerFactory getTrustManagerFactory(Vertx vertx) {
    return trustManagerFactory;
  }

  @Override
  public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) {
    return s -> Optional.ofNullable(trustManagers.get(s)).map(TrustManagerFactory::getTrustManagers).orElse(null);
  }

}
