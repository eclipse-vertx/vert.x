package io.vertx.core.dns.impl.decoder;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.dns.DnsRecordType;
import io.vertx.core.dns.dnsrecord.DohResourceRecord;
import io.vertx.core.dns.impl.MxRecordImpl;
import io.vertx.core.dns.impl.SrvRecordImpl;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 *
 * Handles the decoding of DoH records.
 *
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class DohRecordDecoder {
  private static final Logger log = LoggerFactory.getLogger(DohRecordDecoder.class);


  /**
   * Decodes MX (mail exchanger) resource records.
   */
  public static final Function<DohResourceRecord, MxRecordImpl> MX = dohResourceRecord -> {
    String[] parts = dohResourceRecord.getData().replaceAll("[\\[\\]]", "").split(" ", 2);
    int priority = Integer.parseInt(parts[0]);
    String name = parts[1];
    return new MxRecordImpl(priority, name);
  };

  /**
   * Decodes SOA (start of authority) resource records.
   */
  public static final Function<DohResourceRecord, StartOfAuthorityRecord> SOA = dohResourceRecord -> {
    String[] parts = dohResourceRecord.getData().split(" ");
    String mName = parts[0];
    String rName = parts[1];
    long serial = Long.parseLong(parts[2]);
    int refresh = Integer.parseInt(parts[3]);
    int retry = Integer.parseInt(parts[4]);
    int expire = Integer.parseInt(parts[5]);
    long minimum = Long.parseLong(parts[6]);

    return new StartOfAuthorityRecord(mName, rName, serial, refresh, retry, expire, minimum);
  };


  /**
   * Decodes SRV (service) resource records.
   */
  public static final Function<DohResourceRecord, SrvRecordImpl> SRV = dohResourceRecord -> {
    String[] parts = dohResourceRecord.getData().split(" ");
    int priority = Integer.parseInt(parts[0]);
    int weight = Integer.parseInt(parts[1]);
    int port = Integer.parseInt(parts[2]);
    String name = parts[3];
    String protocol = parts[4];
    String service = parts[5];
    String target = parts[6];

    return new SrvRecordImpl(priority, weight, port, name, protocol, service, target);
  };

  /**
   * Decodes default resource records to extract and return the data property.
   */
  public static final Function<DohResourceRecord, String> DEFAULT = DohResourceRecord::getData;
  private static final Map<DnsRecordType, Function<DohResourceRecord, ?>> decoders = new HashMap<>();

  static {
    decoders.put(DnsRecordType.MX, DohRecordDecoder.MX);
    decoders.put(DnsRecordType.SRV, DohRecordDecoder.SRV);
    decoders.put(DnsRecordType.SOA, DohRecordDecoder.SOA);

    decoders.put(DnsRecordType.A, DohRecordDecoder.DEFAULT);
    decoders.put(DnsRecordType.AAAA, DohRecordDecoder.DEFAULT);
    decoders.put(DnsRecordType.TXT, DohRecordDecoder.DEFAULT);
    decoders.put(DnsRecordType.NS, DohRecordDecoder.DEFAULT);
    decoders.put(DnsRecordType.CNAME, DohRecordDecoder.DEFAULT);
    decoders.put(DnsRecordType.PTR, DohRecordDecoder.DEFAULT);
  }

  /**
   * Decodes a resource record and returns the result.
   *
   * @param record
   * @return the decoded resource record
   */

  @SuppressWarnings("unchecked")
  public static <T> T decode(DohResourceRecord record) {
    DnsRecordType type = DnsRecordType.valueOf(record.getType());
    Function<DohResourceRecord, ?> decoder = decoders.get(type);
    if (decoder == null) {
      throw new DecoderException("DNS record decoding error occurred: Unsupported resource record type [id: " + type + "].");
    }
    T result = null;
    try {
      result = (T) decoder.apply(record);
    } catch (Exception e) {
      log.error(e.getMessage(), e.getCause());
    }
    return result;
  }

}
