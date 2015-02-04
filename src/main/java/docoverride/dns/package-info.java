/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 * === Error handling
 *
 * As you saw in previous sections the DnsClient allows you to pass in a Handler which will be notified with an
 * AsyncResult once the query was complete. In case of an error it will be notified with a DnsException which will
 * hole a {@link io.vertx.core.dns.DnsResponseCode} that indicate why the resolution failed. This DnsResponseCode
 * can be used to inspect the cause in more detail.
 *
 * Possible DnsResponseCodes are:
 *
 * - {@link io.vertx.core.dns.DnsResponseCode#NOERROR} No record was found for a given query
 * - {@link io.vertx.core.dns.DnsResponseCode#FORMERROR} Format error
 * - {@link io.vertx.core.dns.DnsResponseCode#SERVFAIL} Server failure
 * - {@link io.vertx.core.dns.DnsResponseCode#NXDOMAIN} Name error
 * - {@link io.vertx.core.dns.DnsResponseCode#NOTIMPL} Not implemented by DNS Server
 * - {@link io.vertx.core.dns.DnsResponseCode#REFUSED} DNS Server refused the query
 * - {@link io.vertx.core.dns.DnsResponseCode#YXDOMAIN} Domain name should not exist
 * - {@link io.vertx.core.dns.DnsResponseCode#YXRRSET} Resource record should not exist
 * - {@link io.vertx.core.dns.DnsResponseCode#NXRRSET} RRSET does not exist
 * - {@link io.vertx.core.dns.DnsResponseCode#NOTZONE} Name not in zone
 * - {@link io.vertx.core.dns.DnsResponseCode#BADVERS} Bad extension mechanism for version
 * - {@link io.vertx.core.dns.DnsResponseCode#BADSIG} Bad signature
 * - {@link io.vertx.core.dns.DnsResponseCode#BADKEY} Bad key
 * - {@link io.vertx.core.dns.DnsResponseCode#BADTIME} Bad timestamp
 *
 * All of those errors are "generated" by the DNS Server itself.
 *
 * You can obtain the DnsResponseCode from the DnsException like:
 *
 * [source,java]
 * ----
 * {@link docoverride.dns.Examples#example16}
 * ----
 */
@Document(fileName = "override/dns.adoc")
package docoverride.dns;

import io.vertx.docgen.Document;