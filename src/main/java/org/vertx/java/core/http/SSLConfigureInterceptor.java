package org.vertx.java.core.http;

import javax.net.ssl.SSLEngine;

public interface SSLConfigureInterceptor {
	
	void configure( SSLEngine ssl ); 

}
