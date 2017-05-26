package io.vertx.test.core;

import javax.net.ssl.TrustManagerFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Created by dominic on 14/02/17.
 *
 * Test Trust Manager Factory for SSLHelperTest. Required due to protected constructor.
 */
class TestTrustManagerFactory extends TrustManagerFactory {
    TestTrustManagerFactory(Runnable callback) throws NoSuchAlgorithmException {
        super(new TestTrustManagerFactorySpi(callback),
                KeyPairGenerator.getInstance("RSA").getProvider(),
                KeyPairGenerator.getInstance("RSA").getAlgorithm());
    }
}
