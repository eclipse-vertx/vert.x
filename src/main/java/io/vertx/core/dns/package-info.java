/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 * == DNS client
 * <p>
 * Often you will find yourself in situations where you need to obtain DNS informations in an asynchronous fashion.
 * Unfortunally this is not possible with the API that is shipped with the Java Virtual Machine itself. Because of
 * this Vert.x offers it's own API for DNS resolution which is fully asynchronous.
 * <p>
 * To obtain a DnsClient instance you will create a new via the Vertx instance.
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example1}
 * ----
 * <p>
 * Be aware that you can pass in a varargs of InetSocketAddress arguments to specifiy more then one DNS Server to try
 * to query for DNS resolution. The DNS Servers will be queried in the same order as specified here. Where the next
 * will be used once the first produce an error while be used.
 * <p>
 * === lookup
 * <p>
 * Try to lookup the A (ipv4) or AAAA (ipv6) record for a given name. The first which is returned will be used,
 * so it behaves the same way as you may be used from when using "nslookup" on your operation system.
 * <p>
 * To lookup the A / AAAA record for "vertx.io" you would typically use it like:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example2}
 * ----
 * <p>
 * === lookup4
 * <p>
 * Try to lookup the A (ipv4) record for a given name. The first which is returned will be used, so it behaves
 * the same way as you may be used from when using "nslookup" on your operation system.
 * <p>
 * To lookup the A record for "vertx.io" you would typically use it like:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example3}
 * ----
 * <p>
 * === lookup6
 * <p>
 * Try to lookup the AAAA (ipv6) record for a given name. The first which is returned will be used, so it behaves the
 * same way as you may be used from when using "nslookup" on your operation system.
 * <p>
 * To lookup the A record for "vertx.io" you would typically use it like:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example4}
 * ----
 * <p>
 * === resolveA
 * <p>
 * Try to resolve all A (ipv4) records for a given name. This is quite similar to using "dig" on unix like operation
 * systems.
 * <p>
 * To lookup all the A records for "vertx.io" you would typically do:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example5}
 * ----
 * <p>
 * === resolveAAAA
 * <p>
 * Try to resolve all AAAA (ipv6) records for a given name. This is quite similar to using "dig" on unix like
 * operation systems.
 * <p>
 * To lookup all the AAAAA records for "vertx.io" you would typically do:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example6}
 * ----
 * <p>
 * === resolveCNAME
 * <p>
 * Try to resolve all CNAME records for a given name. This is quite similar to using "dig" on unix like operation
 * systems.
 * <p>
 * To lookup all the CNAME records for "vertx.io" you would typically do:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example7}
 * ----
 * <p>
 * === resolveMX
 * <p>
 * Try to resolve all MX records for a given name. The MX records are used to define which Mail-Server accepts
 * emails for a given domain.
 * <p>
 * To lookup all the MX records for "vertx.io" you would typically do:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example8}
 * ----
 * <p>
 * Be aware that the List will contain the {@link io.vertx.core.dns.MxRecord} sorted by the priority of them, which
 * means MX records with smaller priority coming first in the List.
 * <p>
 * The {@link io.vertx.core.dns.MxRecord} allows you to access the priority and the name of the MX record by offer methods for it like:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example9}
 * ----
 * <p>
 * === resolveTXT
 * <p>
 * Try to resolve all TXT records for a given name. TXT records are often used to define extra informations for a domain.
 * <p>
 * To resolve all the TXT records for "vertx.io" you could use something along these lines:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example10}
 * ----
 * <p>
 * === resolveNS
 * <p>
 * Try to resolve all NS records for a given name. The NS records specify which DNS Server hosts the DNS informations
 * for a given domain.
 * <p>
 * To resolve all the NS records for "vertx.io" you could use something along these lines:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example11}
 * ----
 * <p>
 * === resolveSRV
 * <p>
 * Try to resolve all SRV records for a given name. The SRV records are used to define extra informations like port and
 * hostname of services. Some protocols need this extra informations.
 * <p>
 * To lookup all the SRV records for "vertx.io" you would typically do:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example12}
 * ----
 * <p>
 * Be aware that the List will contain the SrvRecords sorted by the priority of them, which means SrvRecords
 * with smaller priority coming first in the List.
 * <p>
 * The {@link io.vertx.core.dns.SrvRecord} allows you to access all informations contained in the SRV record itself:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example13}
 * ----
 * <p>
 * Please refer to the API docs for the exact details.
 * <p>
 * === resolvePTR
 * <p>
 * Try to resolve the PTR record for a given name. The PTR record maps an ipaddress to a name.
 * <p>
 * To resolve the PTR record for the ipaddress 10.0.0.1 you would use the PTR notion of "1.0.0.10.in-addr.arpa"
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example14}
 * ----
 * <p>
 * === reverseLookup
 * <p>
 * Try to do a reverse lookup for an ipaddress. This is basically the same as resolve a PTR record, but allows you to
 * just pass in the ipaddress and not a valid PTR query string.
 * <p>
 * To do a reverse lookup for the ipaddress 10.0.0.1 do something similar like this:
 * <p>
 * [source,$lang]
 * ----
 * {@link examples.DNSExamples#example15}
 * ----
 * <p>
 * include::override/dns.adoc[]
 */
@Document(fileName = "dns.adoc")
package io.vertx.core.dns;

import io.vertx.docgen.Document;

