package io.vertx.core.net.impl;

import io.vertx.core.Vertx;
import io.vertx.core.net.TrustOptions;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import java.security.*;
import java.util.function.Supplier;

/**
 * Created by Dominic Hauton on 15/02/17.
 *
 * Implementation of Trust Options that returns the given Trust Managers
 */
public class TrustOptionsImpl implements TrustOptions {

  private final TrustManagerFactory trustManagerFactory;

  /**
   * Constructs a TrustOptions implementation that will return the given TrustManagers
   *
   * @param trustManagerSupplier Trust managers that should be supplied
   * @param algorithm - the standard string name of the algorithm. See the KeyPairGenerator section in the
   *                  Java Cryptography Architecture Standard Algorithm Name Documentation for information
   *                  about standard algorithm names.
   * @throws NoSuchAlgorithmException
   *                  If no Provider supports a KeyPairGeneratorSpi implementation for the specified algorithm.
   */
  public TrustOptionsImpl(Supplier<TrustManager[]> trustManagerSupplier, String algorithm) throws NoSuchAlgorithmException {
    trustManagerFactory = new DummyTrustManagerFactory(trustManagerSupplier, algorithm);
  }

  private TrustOptionsImpl(TrustManagerFactory trustManagerFactory) {
    this.trustManagerFactory = trustManagerFactory;
  }

  @Override
  public TrustOptions clone() {
    return new TrustOptionsImpl(trustManagerFactory);
  }

  @Override
  public TrustManagerFactory getTrustManagerFactory(Vertx vertx) throws Exception {
    return trustManagerFactory;
  }

  /**
   * A trust manager factory that calls the constructor with the DummyTrustManagerFactorySpi and given algo
   */
  private class DummyTrustManagerFactory extends TrustManagerFactory {
    DummyTrustManagerFactory(Supplier<TrustManager[]> trustManagerSupplier, String algorithm) throws NoSuchAlgorithmException {
      super(new DummyTrustManagerFactorySpi(trustManagerSupplier),
              KeyPairGenerator.getInstance(algorithm).getProvider(),
              KeyPairGenerator.getInstance(algorithm).getAlgorithm());
    }
  }

  /**
   * Dummy TrustManagerFactorySpi that returns the TrustManagers from the supplier.
   */
  private class DummyTrustManagerFactorySpi extends TrustManagerFactorySpi {

    private final Supplier<TrustManager[]> trustManagerSupplier;

    DummyTrustManagerFactorySpi(Supplier<TrustManager[]> trustManagerSupplier) {
      this.trustManagerSupplier = trustManagerSupplier;
    }

    @Override
    protected void engineInit(KeyStore keyStore) throws KeyStoreException {
      // n/a
    }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws InvalidAlgorithmParameterException {
      // n/a
    }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
      return trustManagerSupplier.get();
    }
  }
}
