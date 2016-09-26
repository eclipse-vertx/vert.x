package io.vertx.test.core;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.HeadersAdaptor;
import org.junit.Before;

public class HeadersAdapterTest extends MultiMapTest {

    @Before
    public void setUp() {
        mmap = createMap();
    }

    protected MultiMap createMap() {
        return new HeadersAdaptor(new DefaultHttpHeaders(false));
    }
}


