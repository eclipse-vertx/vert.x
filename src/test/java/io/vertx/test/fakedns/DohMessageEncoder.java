package io.vertx.test.fakedns;

import io.vertx.core.dns.MxRecord;
import io.vertx.core.dns.dnsrecord.DohResourceRecord;
import io.vertx.core.dns.impl.MxRecordImpl;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">imz87</a>
 */
public class DohMessageEncoder {
  /**
   * the log for this class
   */
  private static final Logger log = LoggerFactory.getLogger(DohMessageEncoder.class);

  private static abstract class RecordEncoder {
    protected abstract DohResourceRecord encode(DohResourceRecord dohResourceRecord, ResourceRecord resourceRecord);

    public DohResourceRecord myEncode(ResourceRecord resourceRecord) {
      DohResourceRecord dohResourceRecord = new DohResourceRecord();
      dohResourceRecord.setTtl(resourceRecord.getTimeToLive());
      dohResourceRecord.setName(resourceRecord.getDomainName());
      dohResourceRecord.setData(resourceRecord.toString());
      dohResourceRecord.setType(resourceRecord.getRecordType().convert());

      return encode(dohResourceRecord, resourceRecord);
    }
  }

  private static final RecordEncoder DEFAULT = new RecordEncoder() {
    @Override
    protected DohResourceRecord encode(DohResourceRecord dohResourceRecord, ResourceRecord resourceRecord) {
      dohResourceRecord.setData(resourceRecord.get(DnsAttribute.DOMAIN_NAME));
      return dohResourceRecord;
    }
  };
  private static final RecordEncoder OTHER = new RecordEncoder() {
    @Override
    protected DohResourceRecord encode(DohResourceRecord dohResourceRecord, ResourceRecord resourceRecord) {
      dohResourceRecord.setData(resourceRecord.get(DnsAttribute.IP_ADDRESS));
      return dohResourceRecord;
    }
  };

  private static final RecordEncoder TXT = new RecordEncoder() {
    @Override
    protected DohResourceRecord encode(DohResourceRecord dohResourceRecord, ResourceRecord resourceRecord) {
      dohResourceRecord.setData(resourceRecord.get(DnsAttribute.CHARACTER_STRING));
      return dohResourceRecord;
    }
  };

  private static final RecordEncoder AAAA = new RecordEncoder() {
    @Override
    protected DohResourceRecord encode(DohResourceRecord dohResourceRecord, ResourceRecord resourceRecord) {
      if (resourceRecord.get(DnsAttribute.IP_ADDRESS).equals("::1")) {
        dohResourceRecord.setData("0:0:0:0:0:0:0:1");
      } else {
        dohResourceRecord.setData(resourceRecord.get(DnsAttribute.IP_ADDRESS));
      }
      return dohResourceRecord;
    }
  };

  private static final RecordEncoder MX = new RecordEncoder() {
    @Override
    protected DohResourceRecord encode(DohResourceRecord dohResourceRecord, ResourceRecord resourceRecord) {
      MxRecord mxRecord = new MxRecordImpl(Integer.parseInt(resourceRecord.get(DnsAttribute.MX_PREFERENCE)),
        resourceRecord.get(DnsAttribute.DOMAIN_NAME));
      dohResourceRecord.setData(String.format("[%s]", mxRecord));
      return dohResourceRecord;
    }
  };

  private static final RecordEncoder SRV = new RecordEncoder() {
    @Override
    protected DohResourceRecord encode(DohResourceRecord dohResourceRecord, ResourceRecord resourceRecord) {
      dohResourceRecord.setTtl(resourceRecord.getTimeToLive());
      dohResourceRecord.setName(resourceRecord.getDomainName());
      dohResourceRecord.setType(resourceRecord.getRecordType().convert());

      int priority = Integer.parseInt(resourceRecord.get(DnsAttribute.SERVICE_PRIORITY));
      int weight = Integer.parseInt(resourceRecord.get(DnsAttribute.SERVICE_WEIGHT));
      int port1 = Integer.parseInt(resourceRecord.get(DnsAttribute.SERVICE_PORT));
      String name = "io.";
      String protocol = "vertx";
      String service = "dns";
      String target = "vertx.io";

      dohResourceRecord.setData(String.join(" ", String.valueOf(priority),
        String.valueOf(weight), String.valueOf(port1), name, protocol,
        service, target));

      return dohResourceRecord;
    }
  };


  /**
   * A Hashed Adapter mapping record types to their encoders.
   */
  private static final Map<RecordType, RecordEncoder> DEFAULT_ENCODERS;

  static {
    Map<RecordType, RecordEncoder> map = new HashMap<>();

    map.put(RecordType.SOA, DohMessageEncoder.OTHER);
    map.put(RecordType.A, DohMessageEncoder.OTHER);
    map.put(RecordType.AAAA, DohMessageEncoder.AAAA);
    map.put(RecordType.NS, DohMessageEncoder.DEFAULT);
    map.put(RecordType.CNAME, DohMessageEncoder.DEFAULT);
    map.put(RecordType.PTR, DohMessageEncoder.DEFAULT);
    map.put(RecordType.MX, DohMessageEncoder.MX);
    map.put(RecordType.SRV, DohMessageEncoder.SRV);
    map.put(RecordType.TXT, DohMessageEncoder.TXT);
    map.put(RecordType.DNAME, DohMessageEncoder.OTHER);

    DEFAULT_ENCODERS = Collections.unmodifiableMap(map);
  }

  public static DohResourceRecord encode(ResourceRecord resourceRecord) {
    return DEFAULT_ENCODERS.get(resourceRecord.getRecordType()).myEncode(resourceRecord);
  }

}
