/**
 * fixed dns resolver to be removed when Netty with https://github.com/netty/netty/pull/5413
 * is released
 *
 * added dns search domain for doResolveUncached (https://github.com/netty/netty/issues/4665)
 * not implemented:
 * - doResolveAllUncached as we don't need it
 * - /etc/resolv.conf parsing to determine "domain" and "options/ndots"
 */
package io.vertx.core.dns.impl.fix;