package io.vertx.test.core;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;

/**
 * Created by dominic on 14/02/17.
 *
 * Test Trust Manager Factory SPI for SSLHelperTest. Required due to protected constructor.
 */
public class TestTrustManagerFactorySpi extends TrustManagerFactorySpi {

    private final Runnable callback;
    private volatile boolean initiated;

    TestTrustManagerFactorySpi(Runnable callback) {
        this.callback = callback;
        initiated = false;
    }

    @Override
    protected void engineInit(KeyStore keyStore) throws KeyStoreException {
        callback.run();
        initiated = true;
    }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws InvalidAlgorithmParameterException {
        callback.run();
        initiated = true;
    }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        if(initiated) {
            return new TrustManager[0];
        } else {
            throw new Error("Trust Manager was not initiated");
        }
    }
}
