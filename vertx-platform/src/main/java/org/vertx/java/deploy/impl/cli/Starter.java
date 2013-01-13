/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.impl.VertxConfig;
import org.vertx.java.core.impl.VertxConfigFactory;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.StringUtils;
import org.vertx.java.core.utils.SystemUtils;
import org.vertx.java.deploy.ModuleRepository;
import org.vertx.java.deploy.impl.DefaultModuleRepository;
import org.vertx.java.deploy.impl.ModuleConfig;
import org.vertx.java.deploy.impl.VerticleManager;
import org.vertx.java.deploy.impl.VertxModule;

/**
 * Command line starter
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class Starter {

	private static final Logger log = LoggerFactory.getLogger(Starter.class);

	private static final String CP_SEPARATOR = SystemUtils.getPathSeparator();

	public static void main(String[] args) {
		try {
			Starter starter = new Starter();
			if (starter.run(args) == false) {
				starter.displaySyntax();
			}
		} catch (Throwable ex) {
			log.error(ex);
		}
	}
	
	/**
	 * Constructor
	 */
	public Starter() {
	}

	/**
	 * Extension Point: subclass to handle additional parameter
	 * 
	 * @param sargs
	 * @return
	 * @throws Exception
	 */
	protected boolean run(final String[] sargs) throws Exception {
		if (sargs.length < 1) {
			return false;
		}

		String command = sargs[0].toLowerCase();
		if ("version".equals(command)) {
			doVersion();
			return true;
		}

		if (sargs.length < 2) {
			return false;
		}
		CommandLineArgs args = new CommandLineArgs(sargs);
		String operand = getOperand(sargs);
		switch (command) {
		case "run":
			runVerticle(false, operand, args);
			return true;
		case "runmod":
			runVerticle(true, operand, args);
			return true;
		case "install":
			installModule(operand, args);
			return true;
		case "uninstall":
			uninstallModule(operand);
			return true;
		}
		return false;
	}

	protected String getOperand(final String[] args) {
		return args[1];
	}
	
	protected void doVersion() {
		System.out.println(getVersion());
	}
	
	/**
	 * Install a module from the repository
	 */
	protected final void installModule(final String modName, final CommandLineArgs args) {
		try (ExtendedDefaultVertx vertx = newExtendedDefaultVertx()) {
			String repo = getParamRepository(args, null);
			if (repo != null) {
				ModuleRepository r = new DefaultModuleRepository(vertx, repo);
				vertx.moduleManager(null).moduleRepositories().add(0, r);
			}
			if (doInstall(vertx, modName) == null) {
				log.error("Timed out while waiting for module to install");
			}
		}
	}

	protected String getParamRepository(final CommandLineArgs args, VertxConfig config) {
		if (config == null) {
			config = getVertxConfig(args);
		}
		return args.get("-repo", config.cmdLineConfig("repository").asText());
	}
	
	protected VertxConfig getVertxConfig(final CommandLineArgs args) {
		VertxConfigFactory fac = new VertxConfigFactory();
		String fname = args.get("--vertx.config");
		if (fname != null) {
			return fac.load(new File(fname), true, false);
		}
		return fac.load();
	}
	
	/**
	 * Extension point
	 */
	protected AsyncResult<Void> doInstall(ExtendedDefaultVertx vertx, String modName) {
		return vertx.moduleManager(null).install(modName, null).get(30, TimeUnit.SECONDS);
	}
	
	protected final void uninstallModule(String modName) {
		try (ExtendedDefaultVertx vertx = newExtendedDefaultVertx()) {
			doUninstall(vertx, modName);
		}
	}

	/**
	 * Extension point
	 */
	protected void doUninstall(ExtendedDefaultVertx vertx, String modName) {
		vertx.moduleManager(null).uninstall(modName);
	}

	protected final void runVerticle(final boolean bModule, final String main, final CommandLineArgs args)
			throws Exception {
		
		boolean clustered = false;
		String clusterHost = null;
		int clusterPort = 25500;
		VertxConfig config = getVertxConfig(args);
		if (config.cmdLineConfig("cluster").asBoolean(false) == true) {
			clustered = true;
			clusterHost = config.cmdLineConfig("cluster-host").asText();
			clusterPort = config.cmdLineConfig("cluster-port").asInt(clusterPort);
		}
		
		if (args.present("-cluster")) {
			clustered = true;
			clusterPort = args.getInt("-cluster-port", clusterPort, clusterPort);
			clusterHost = args.get("-cluster-host", clusterHost);
		}

		if (clustered) {
			log.info("Starting clustering...");
			if (clusterHost == null) {
				clusterHost = getDefaultAddress();
				if (clusterHost == null) {
					log.error("Unable to find a default network interface for clustering. Please specify one using -cluster-host");
					return;
				} else {
					log.info("No cluster-host specified so using address " + clusterHost);
				}
			}
		}

		try (ExtendedDefaultVertx vertx = newExtendedDefaultVertx(clusterHost, clusterPort)) {

			String repo = getParamRepository(args, config);
			if (repo != null) {
				ModuleRepository r = new DefaultModuleRepository(vertx, repo);
				vertx.moduleManager(null).moduleRepositories().add(0, r);
			}

			boolean worker = getParamWorker(args);

			String cp = getParamClasspath(args, config);
			List<URI> urls = classpath(cp);
			
			int instances = getParamInstances(args);
			if (instances < 1) {
				log.error("Invalid number of instances");
				displaySyntax();
				return;
			}

			ModuleConfig conf = null;
			String configFile = getParamVerticleConfigFile(args, config);
			if (configFile != null) {
				conf = new ModuleConfig(new File(configFile));
			}

			final VerticleManager verticleManager = vertx.verticleManager();
			VertxModule module = new VertxModule(vertx.moduleManager(null), null);
			if (conf != null) {
				module.config(conf);
			}
			// Allow to take main from the config
			if (main != null && main.trim().length() > 0) {
				module.config().main(main);
			}
			module.config().worker(worker);
			module.classPath(urls, false);

			// TODO This is the only remaining difference between Verticle and Module??
			AsyncResult<String> res;
			if (bModule) {
				res = doRun(vertx, module, instances);
			} else {
				String includes = getParamIncludes(args, config);
				if (includes != null) {
					module.config().includes(includes);
				}
				res = doRun(vertx, module, instances);
			}

			if (res == null) {
				log.error("Timeout: while deploying verticle or module");
			} else if (res.failed()) {
				log.error("Error while deploying verticle or module: " + res.exception.getMessage());
			} else {
				verticleManager.block();
			}
		}
	}

	protected String getParamIncludes(final CommandLineArgs args, VertxConfig config) {
		return args.get("-includes", config.cmdLineConfig("includes").asText());
	}

	protected int getParamInstances(final CommandLineArgs args) {
		return args.getInt("-instances", 1, -1);
	}

	protected boolean getParamWorker(final CommandLineArgs args) {
		return args.present("-worker");
	}

	protected String getParamVerticleConfigFile(final CommandLineArgs args, VertxConfig config) {
		return args.get("-conf", config.cmdLineConfig("conf").asText());
	}

	protected String getParamClasspath(final CommandLineArgs args, VertxConfig config) {
		return StringUtils.join(CP_SEPARATOR, args.get("-cp", "."), config.cmdLineConfig("classpath").asText());
	}

	/**
	 * Extension point
	 */
	protected AsyncResult<String> doRun(ExtendedDefaultVertx vertx, VertxModule module, int instances) {
		return vertx.verticleManager().deploy(null, module, instances, null, null).get(30, TimeUnit.SECONDS);
	}

	private List<URI> classpath(String cp) {
		String[] parts = StringUtils.split(CP_SEPARATOR, cp);
		List<URI> urls = new ArrayList<>();
		for (String part : parts) {
			if (part != null && part.trim().length() > 0) {
				URI url = new File(part).toURI();
				urls.add(url);
			}
		}
		return urls;
	}

	/**
	 * Get default interface to use since the user hasn't specified one
	 */
	private String getDefaultAddress() {
		Enumeration<NetworkInterface> nets;
		try {
			nets = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			return null;
		}

		while (nets.hasMoreElements()) {
			NetworkInterface netinf = nets.nextElement();
			Enumeration<InetAddress> addresses = netinf.getInetAddresses();

			while (addresses.hasMoreElements()) {
				InetAddress address = addresses.nextElement();
				if (!address.isAnyLocalAddress() && !address.isMulticastAddress() && !(address instanceof Inet6Address)) {
					return address.getHostAddress();
				}
			}
		}
		return null;
	}

	protected final ExtendedDefaultVertx newExtendedDefaultVertx() {
		return newExtendedDefaultVertx(null, 0);
	}

	protected ExtendedDefaultVertx newExtendedDefaultVertx(String hostname, int port) {
		return new ExtendedDefaultVertx(hostname, port);
	}
	
	public final String getVersion() {
		String className = Starter.class.getSimpleName() + ".class";
		String classPath = this.getClass().getResource(className).toString();
		if (!classPath.startsWith("jar")) {
		  // Class not from JAR
		  return "<unknown> (not a jar)";
		}
		String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + 
		    "/META-INF/MANIFEST.MF";
		Manifest manifest;
		try {
			manifest = new Manifest(new URL(manifestPath).openStream());
		} catch (IOException ex) {
		  return "<unknown> (" + ex.getMessage() + ")";
		}
		Attributes attr = manifest.getMainAttributes();
		return attr.getValue("Vertx-Version");
	}
	
	/**
	 * Prints the help text
	 */
	protected void displaySyntax(final PrintWriter out) {
		try (InputStream in = Starter.class.getResourceAsStream("help.txt");
				InputStreamReader rin = new InputStreamReader(in);
				BufferedReader bin = new BufferedReader(rin)) {
			String line;
			while (null != (line = bin.readLine())) {
				out.println(line);
				out.flush();
			}
		} catch (IOException ex) {
			log.error("Help text not found !?!");
		}
	}
	
	private final void displaySyntax() {
		displaySyntax(new PrintWriter(new OutputStreamWriter(System.out)));
	}
}
