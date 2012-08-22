package org.vertx.java.core.socketio.impl.transports;

import org.vertx.java.core.socketio.impl.ClientData;
import org.vertx.java.core.socketio.impl.Manager;

/**
 * @author Keesun Baik
 */
public class FlashSocket extends WebSocketTransport {

	public FlashSocket(Manager manager, ClientData clientData) {
		super(manager, clientData);

		// Drop out immediately if the user has
		// disabled the flash policy server
		if(!manager.getSettings().isFlashPolicyServer()) {
			return;
		}

//		String policy = "";
//		policy = "<?xml version=\"1.0\"?>\n<!DOCTYPE cross-domain-policy SYSTEM";
//		policy += "\"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\">\n<cross-domain-policy>\n";
//		policy += "<site-control permitted-cross-domain-policies=\"master-only\"/>";
//		policy += "<allow-access-from domain=\"*\" to-ports=\"*\"/>\n";
//		policy += "</cross-domain-policy>\n";
//
//		NetServer policyServer = manager.getVertx().createNetServer();
//		final String finalPolicy = policy;
//		policyServer.connectHandler(new Handler<NetSocket>() {
//			public void handle(NetSocket socket) {
//				socket.write(finalPolicy + "\\0");
//
//				socket.dataHandler(new Handler<Buffer>() {
//					public void handle(Buffer data) {
//						//TODO FlashSocket.init
//						System.out.println("FlashSocket data");
//						System.out.println(data.toString());
//					}
//				});
//			}
//		});
//		policyServer.listen(manager.getSettings().getFlashPolicyPort());
	}

	@Override
	protected String getName() {
		return "flashsocket";
	}
}
