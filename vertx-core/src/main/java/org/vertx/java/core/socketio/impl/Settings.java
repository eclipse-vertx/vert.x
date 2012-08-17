package org.vertx.java.core.socketio.impl;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.socketio.impl.stores.MemoryStore;
import org.vertx.java.core.socketio.impl.stores.RedisStore;

import java.util.Arrays;
import java.util.List;

/**
 * @author Keesun Baik
 */
public class Settings {

	private String origins = "*.*";
	private boolean log = true;
	private Store store = new MemoryStore();
//	private Logger logger;
	private String Static = "static";
	private boolean heartbeats = true;
	private String namespace = "/socket.io";
	private String transports = "websocket,htmlfile,xhr-polling,jsonp-polling";
	private boolean authorization = false;
	private List<String> blacklist = Arrays.asList(new String[]{"disconnect"});

//	private int logLevel = 3; // TODO vert.x log level 설정 방법으로 변경.
//	private String logColors // 'log colors': tty.isatty(process.stdout.fd)

	private int closeTimeout = 60;
	private int heartbeatInterval = 25;
	private int heartbeatTimeout = 60;
	private int pollingDuration = 20;
	private boolean flashPolicyServer = true;
	private int flashPolicyPort = 10843;
	private boolean destroyUpgrade = true;
	private int destryBufferSize = 0x10E7;
	private boolean browserClient = true;
	private boolean browserClientCache = true;
	private boolean browserClientMinification = false;
	private boolean browserClientEtag = false;
	private int browserClientExpires = 315360000;
	private boolean browserClientGzip = false;
	private boolean browserClientHandler = false;
	private int clientStoreExpiration = 15;
	private boolean matchOriginProtocol = false;

	public Settings(JsonObject options) {
		this.origins = options.getString("origins", this.origins);
		this.log = options.getBoolean("log", this.log);
		String store = options.getString("store");
		if(store != null) {
			if(store.equals("redis")) this.store = new RedisStore();
		}
		this.Static = options.getString("Static", this.Static);
		this.heartbeats = options.getBoolean("heartbeats", this.heartbeats);
		this.namespace = options.getString("namespace", this.namespace);
		this.transports = options.getString("transports", this.transports);
		this.authorization = options.getBoolean("authorization", this.authorization);

		String blacklistString = options.getString("blacklist");
		if(blacklistString != null && !blacklistString.isEmpty()) {
			String[] blacklistArray = blacklistString.split(",");
			this.blacklist = Arrays.asList(blacklistArray);
		}

		this.closeTimeout = options.getNumber("closeTimeout", this.closeTimeout).intValue();
		this.heartbeatInterval = options.getNumber("heartbeatInterval", this.heartbeatInterval).intValue();
		this.heartbeatTimeout = options.getNumber("heartbeatTimeout", this.heartbeatTimeout).intValue();
		this.pollingDuration = options.getNumber("pollingDuration", this.pollingDuration).intValue();
		this.flashPolicyServer = options.getBoolean("flashPolicyServer", this.flashPolicyServer);
		this.flashPolicyPort = options.getNumber("flashPolicyPort", this.flashPolicyPort).intValue();
		this.destroyUpgrade = options.getBoolean("destroyUpgrade", this.destroyUpgrade);
		this.destryBufferSize = options.getNumber("destryBufferSize", this.destryBufferSize).intValue();
		this.browserClient = options.getBoolean("browserClient", this.browserClient);
		this.browserClientCache = options.getBoolean("browserClientCache", this.browserClientCache);
		this.browserClientMinification = options.getBoolean("browserClientMinification", this.browserClientMinification);
		this.browserClientEtag = options.getBoolean("browserClientEtag", this.browserClientEtag);
		this.browserClientExpires = options.getNumber("browserClientExpires", this.browserClientExpires).intValue();
		this.browserClientGzip = options.getBoolean("browserClientGzip", this.browserClientGzip);
		this.browserClientHandler = options.getBoolean("browserClientHandler", this.browserClientHandler);
		this.clientStoreExpiration = options.getNumber("clientStoreExpiration", this.clientStoreExpiration).intValue();
		this.matchOriginProtocol = options.getBoolean("matchOriginProtocol", this.matchOriginProtocol);
	}

	public String getOrigins() {
		return origins;
	}

	public boolean isLog() {
		return log;
	}

	public Store getStore() {
		return store;
	}

	public boolean isHeartbeats() {
		return heartbeats;
	}

	public String getNamespace() {
		return namespace;
	}

	public String getTransports() {
		return transports;
	}

	public boolean isAuthorization() {
		return authorization;
	}

	public int getCloseTimeout() {
		return closeTimeout;
	}

	public int getHeartbeatInterval() {
		return heartbeatInterval;
	}

	public int getPollingDuration() {
		return pollingDuration;
	}

	public boolean isFlashPolicyServer() {
		return flashPolicyServer;
	}

	public int getFlashPolicyPort() {
		return flashPolicyPort;
	}

	public boolean isDestroyUpgrade() {
		return destroyUpgrade;
	}

	public int getDestryBufferSize() {
		return destryBufferSize;
	}

	public boolean isBrowserClient() {
		return browserClient;
	}

	public boolean isBrowserClientCache() {
		return browserClientCache;
	}

	public boolean isBrowserClientMinification() {
		return browserClientMinification;
	}

	public boolean isBrowserClientEtag() {
		return browserClientEtag;
	}

	public int getBrowserClientExpires() {
		return browserClientExpires;
	}

	public boolean isBrowserClientGzip() {
		return browserClientGzip;
	}

	public boolean isBrowserClientHandler() {
		return browserClientHandler;
	}

	public int getClientStoreExpiration() {
		return clientStoreExpiration;
	}

	public boolean isMatchOriginProtocol() {
		return matchOriginProtocol;
	}

	public String getStatic() {
		return Static;
	}

	public int getHeartbeatTimeout() {
		return heartbeatTimeout;
	}

	public List<String> getBlacklist() {
		return blacklist;
	}
}
