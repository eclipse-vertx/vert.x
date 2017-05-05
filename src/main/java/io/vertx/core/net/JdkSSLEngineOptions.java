package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Configures a {@link TCPSSLOptions} to use the JDK ssl engine implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
// TODO: 17/1/1 by zmyer
@DataObject
public class JdkSSLEngineOptions extends SSLEngineOptions {

    private static Boolean jdkAlpnAvailable;

    /**
     * @return if alpn support is available via the JDK SSL engine
     */
    // TODO: 17/1/1 by zmyer
    public static synchronized boolean isAlpnAvailable() {
        if (jdkAlpnAvailable == null) {
            boolean available = false;
            try {
                // Always use bootstrap class loader.
                JdkSSLEngineOptions.class.getClassLoader().loadClass("sun.security.ssl.ALPNExtension");
                available = true;
            } catch (Exception ignore) {
                // alpn-boot was not loaded.
            } finally {
                jdkAlpnAvailable = available;
            }
        }
        return jdkAlpnAvailable;
    }

    public JdkSSLEngineOptions() {
    }

    public JdkSSLEngineOptions(JsonObject json) {
    }

    public JdkSSLEngineOptions(JdkSSLEngineOptions that) {
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof JdkSSLEngineOptions;
    }

    @Override
    public SSLEngineOptions clone() {
        return new JdkSSLEngineOptions();
    }
}
