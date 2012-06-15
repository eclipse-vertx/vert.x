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

import java.io.File;
import java.io.FileNotFoundException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.deploy.impl.VerticleManager;
import org.vertx.java.deploy.impl.Args;

/**
 * CLI interface. Entry point for standalone vert.x application.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO Change name to VertxApp. VertxMgr vs VertxManager is too confusing
public class VertxMgr {
	private static final Logger log = LoggerFactory.getLogger(VertxMgr.class);

	public final static boolean WIN_OS = System.getProperty("os.name")
			.startsWith("Windows");

	/**
	 * Main
	 * 
	 * @param args
	 */
	public static void main(final String[] args) {
		if (args.length == 0) {
			displaySyntax();
			System.exit(1);
		}

		VertxMgr app = null;
		try {
			app = new VertxMgr(args);
		}
		catch (CommandlineException ex) {
			System.err.println("ERROR: " + ex.getMessage());
			System.exit(1);
		}
		catch (Exception ex) {
			log.error(ex.getMessage());
			System.exit(1);
		}
		finally {
			// Undeploy all verticals
			if ((app != null) && (app.mgr != null)) {
				final CountDownLatch latch = new CountDownLatch(1);
				app.mgr.undeployAll(new SimpleHandler() {
					public void handle() {
						latch.countDown();
					}
				});

				waitForLatch(latch, 30,
						"Timeout while waiting for verticals to undeploy");
			}
		}
	}

	// The actual vert.x engine
	private VertxInternal vertx;

	// The vert.x vertical manager
	private VerticleManager mgr;

	/**
	 * Constructor
	 * 
	 * @param sargs
	 */
	private VertxMgr(final String[] sargs) {
		String scmd = sargs[0];
		String name = (sargs.length > 1 ? sargs[1] : null);
		Args args = new Args(sargs);
		int port = args.getInt("-port", SocketDeployer.DEFAULT_PORT);

		if (isOption(scmd, "version")) {
			// TODO get from MANIFEST, ANT or Maven properties
			System.out.println("vert.x 1.0.final");
		} else if (isOption(scmd, "start")) {
			startServer(args);
		} else if (isOption(scmd, "run")) {
			runApplication(name, args);
		} else if (isOption(scmd, "stop")) {
			log.info("Stopping vert.x server");
			sendCommand(port, new StopCommand());
		} else if (isOption(scmd, "deploy")) {
			sendCommand(port, createCommand(name, args, "deploy"));
		} else if (isOption(scmd, "undeploy")) {
			if (name == null) {
				throw new CommandlineException(
						"Vertical name missing. Please run 'vertx help' for more details");
			}
			sendCommand(port, new UndeployCommand(name));
		} else {
			displaySyntax();
		}
	}

	/**
	 * Compare (ignore case) with the first commandline argument
	 * 
	 * @param cmd
	 * @return
	 */
	private boolean isOption(final String arg, final String cmd) {
		return arg.equalsIgnoreCase(cmd);
	}

	/**
	 * Command: "run". Run a vertical application
	 * 
	 * @param main
	 * @param args
	 */
	private void runApplication(final String main, final Args args) {
		if (main == null) {
			throw new CommandlineException(
					"<main> missing. Please run 'vertx help' for more details");
		}

		initializeVertx(args);

		// Get the command line options
		DeployCommand dc = createCommand(main, args, "run");
		JsonObject jsonConf = null;
		if (dc.conf != null) {
			try {
				jsonConf = new JsonObject(dc.conf);
			}
			catch (DecodeException e) {
				throw new CommandlineException(
						"Configuration file does not contain a valid JSON object", e);
			}
		}

		mgr.deploy(dc.worker, dc.name, dc.main, jsonConf, dc.urls, dc.instances,
				null, null);

		// TODO better name: waitForManagerShutdown
		mgr.block();
	}

	/**
	 * Command: "start".
	 * 
	 * @param args
	 */
	private void startServer(final Args args) {

		initializeVertx(args);

		new SocketDeployer(vertx, mgr, args.getInt("-deploy-port",
				SocketDeployer.DEFAULT_PORT)).start();

		log.info("vert.x server started");

		// TODO better name: waitForManagerShutdown
		mgr.block();
	}

	/**
	 * 
	 * @param main
	 * @param args
	 * @param command
	 * @return will never be null.
	 */
	private DeployCommand createCommand(final String main, final Args args,
			final String command) {

		if (main == null) {
			throw new IllegalArgumentException("Parameter 'main' must not be null.");
		}

		boolean worker = args.hasOption("-worker");

		String name = args.get("-name");
		String cp = args.get("-cp", ".");
		URL[] urls = getClassPathUrls(cp);
		int instances = getInstanceCount(args);
		String conf = getConfigData(args);

		return new DeployCommand(worker, name, main, conf, urls, instances);
	}

	/**
	 * Load config data from config file
	 * 
	 * @param args
	 * @return
	 */
	private String getConfigData(final Args args) {
		String configFile = args.get("-conf");
		if (configFile != null) {
			try {
				return new Scanner(new File(configFile)).useDelimiter("\\A").next();
			}
			catch (FileNotFoundException e) {
				throw new CommandlineException("Config file " + configFile
						+ " not found", e);
			}
		}
		return null;
	}

	/**
	 * Get instance count
	 * 
	 * @param args
	 * @return
	 */
	private int getInstanceCount(final Args args) {
		int instances = 1;
		String sinstances = args.get("-instances");
		if (sinstances != null) {
			try {
				instances = Integer.parseInt(sinstances);
			}
			catch (NumberFormatException e) {
				// ignore
			}

			// TODO What is the meaning of -1?
			if (instances != -1 && instances < 1) {
				throw new CommandlineException(
						"Invalid number of instances. Must be > 0.");
			}
		}

		return instances;
	}

	/**
	 * Get classpath elements and convert path into an URL
	 * <p>
	 * Note: Function calls System.exit() in case of malformed path.
	 * 
	 * @param cp
	 * @return
	 */
	private URL[] getClassPathUrls(final String cp) {
		// Class path separator
		String cpSeparator = WIN_OS ? ";" : ":";

		// Split classpath into elements
		String[] parts = cp.split(cpSeparator);

		// Convert path elements to URL[]
		int index = 0;
		final URL[] urls = new URL[parts.length];
		for (String part : parts) {
			try {
				URL url = new File(part).toURI().toURL();
				urls[index++] = url;
			}
			catch (MalformedURLException e) {
				throw new CommandlineException("Invalid path " + part + " in cp " + cp,
						e);
			}
		}
		return urls;
	}

	/**
	 * Start vert.x in cluster mode if <code>-cluster</code> option has been
	 * provided. Else start standalone.
	 * 
	 * @param args
	 */
	private void initializeVertx(final Args args) {
		boolean clustered = args.hasOption("-cluster");
		if (clustered) {
			log.info("Starting in cluster mode...");
			int clusterPort = args.getInt("-cluster-port", 25500);
			String clusterHost = args.get("-cluster-host");
			if (clusterHost == null) {
				clusterHost = getDefaultAddress();
				if (clusterHost == null) {
					throw new CommandlineException(
							"Unable to find a default network interface for clustering. Please specify one using -cluster-host");
				} else {
					log.info("No cluster-host specified. Default to: " + clusterHost);
				}
			}
			vertx = new DefaultVertx(clusterPort, clusterHost);
		} else {
			vertx = new DefaultVertx();
		}

		mgr = new VerticleManager(vertx);

		log.info("...started");
	}

	/**
	 * Get default interface to use since the user hasn't specified one. Default
	 * will be the first non-local, non-multicast and non-ipv6 address found.
	 */
	private String getDefaultAddress() {
		Enumeration<NetworkInterface> nets;
		try {
			nets = NetworkInterface.getNetworkInterfaces();
		}
		catch (SocketException e) {
			throw new CommandlineException("Unable to determine network interfaces",
					e);
		}

		NetworkInterface netinf;
		while (nets.hasMoreElements()) {
			netinf = nets.nextElement();

			Enumeration<InetAddress> addresses = netinf.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress address = addresses.nextElement();
				if (!address.isAnyLocalAddress() && !address.isMulticastAddress()
						&& !(address instanceof Inet6Address)) {

					// Stop at first suitable address
					return address.getHostAddress();
				}
			}
		}
		return null;
	}

	/**
	 * Send the command
	 * 
	 * @param port
	 * @param command
	 * @return
	 */
	private String sendCommand(final int port, final VertxCommand command) {
		if (port < 1 || port >= (2 ^ 16)) {
			throw new CommandlineException("IP port number must be 0 < x < 2^16");
		}

		if (command == null) {
			throw new IllegalArgumentException(
					"Bug: Parameter 'command' must not be null");
		}

		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<String> result = new AtomicReference<>();

		final NetClient client = vertx.createNetClient();
		client.connect(port, "localhost", new Handler<NetSocket>() {
			public void handle(final NetSocket socket) {
				if (command.isBlock()) {
					socket.dataHandler(RecordParser.newDelimited("\n",
							new Handler<Buffer>() {
								public void handle(Buffer buff) {
									result.set(buff.toString());
									client.close();
									latch.countDown();
								}
							}));
					command.write(socket, null);
				} else {
					command.write(socket, new SimpleHandler() {
						public void handle() {
							// TODO Not writing anything??
							client.close();
							latch.countDown();
						}
					});
				}
			}
		});

		waitForLatch(latch, 10, "Timed out while sending command");

		String res = result.get();
		if (res != null) {
			log.info(res);
		}
		return res;
	}

	/**
	 * Little helper to wait for for a latch. Ignore InterruptedExceptions. If
	 * they occur, which might happen, than wait again.
	 * 
	 * @param latch
	 * @param secs
	 * @param msg
	 */
	private static void waitForLatch(final CountDownLatch latch, final int secs,
			final String msg) {

		while (true) {
			try {
				if (!latch.await(secs, TimeUnit.SECONDS)) {
					throw new IllegalStateException(msg);
				}
				break;
			}
			catch (InterruptedException e) {
				// Ignore
			}
		}
	}

	/**
	 * Display help message
	 */
	private static void displaySyntax() {
		String usage = "Usage: vertx [run|deploy|undeploy|start|stop|help] [main] [-options]\n\n"
				+ "    vertx run <main> [-options]\n"
				+ "        runs a verticle called <main> in its own instance of vert.x.\n"
				+ "        <main> can be a JavaScript script, a Ruby script, A Groovy script, or a\n"
				+ "        Java class.\n\n"
				+ "      valid options are:\n"
				+ "        -conf <config_file>    Specifies configuration that should be provided \n"
				+ "                               to the verticle. <config_file> should reference \n"
				+ "                               a text file containing a valid JSON object.\n"
				+ "        -cp <path>             specifies the path on which to search for <main>\n"
				+ "                               and any referenced resources.\n"
				+ "                               Defaults to '.' (current directory).\n"
				+ "        -instances <instances> specifies how many instances of the verticle will\n"
				+ "                               be deployed. Defaults to the number of available\n"
				+ "                               cores on the system.\n"
				+ "        -worker                if specified then the verticle is a worker\n"
				+ "                               verticle.\n"
				+ "        -cluster               if specified then the vert.x instance will form a\n"
				+ "                               cluster with any other vert.x instances on the\n"
				+ "                               network.\n"
				+ "        -cluster-port          port to use for cluster communication.\n"
				+ "                               Default is 25500.\n"
				+ "        -cluster-host          host to bind to for cluster communication.\n"
				+ "                               Default is 0.0.0.0 (all interfaces).\n\n\n"
				+ "    vertx deploy <main> [-options]\n"
				+ "        deploys a verticle called <main> to a standalone local vert.x server.\n"
				+ "        <main> can be a JavaScript script, a Ruby script, A Groovy script,\n"
				+ "        or a Java class.\n\n"
				+ "      valid options are:\n"
				+ "        -conf <config_file>    Specifies configuration that should be provided \n"
				+ "                               to the verticle. <config_file> should reference \n"
				+ "                               a text file containing a valid JSON object.\n"
				+ "        -cp <path>             specifies the path on which to search for <main>\n"
				+ "                               and any referenced resources.\n"
				+ "                               Defaults to '.' (current directory).\n"
				+ "        -name <name>           specifies a unique name to give the deployment.\n"
				+ "                               Used later if you want to undeploy. A name will\n"
				+ "                               be auto-generated if it is not specified.\n"
				+ "        -instances <instances> specifies how many instances of the verticle will\n"
				+ "                               be deployed. Default is 1.\n"
				+ "        -worker                if specified then the verticle is a worker\n"
				+ "                               verticle.\n"
				+ "        -port                  if specified then use the specified port for\n"
				+ "                               connecting to the server for deployment.\n"
				+ "                               Default is 25571.\n\n\n"
				+ "    vertx undeploy <name>\n"
				+ "        undeploys verticles from a standalone local vert.x server.\n"
				+ "        <name> is the unique name of the deployment specified when deploying.\n\n"
				+ "      valid options are:\n"
				+ "        -port                  if specified then use the specified port for\n"
				+ "                               connecting to the server for undeployment.\n"
				+ "                               Default is 25571.\n\n\n"
				+ "    vertx start\n"
				+ "        starts a standalone local vert.x server.\n\n"
				+ "      valid options are:\n"
				+ "        -port                  if specified then use the specified port for\n"
				+ "                               listening for deployments. Default is 25571.\n"
				+ "        -cluster               if specified then the vert.x instance will form a\n"
				+ "                               cluster with any other vert.x instances on the\n"
				+ "                               network.\n"
				+ "        -cluster-port          port to use for cluster communication.\n"
				+ "                               Default is 25500.\n"
				+ "        -cluster-host          host to bind to for cluster communication.\n"
				+ "                               Default is 0.0.0.0 (all interfaces).\n\n\n"
				+ "    vertx stop\n"
				+ "        stops a standalone local vert.x server.\n\n"
				+ "      valid options are:\n"
				+ "        -port                  if specified then connect to the server at the\n"
				+ "                               specified port to stop it. Default is 25571.\n\n\n"
				+ "    vertx version\n"
				+ "        displays the version\n\n\n"
				+ "    vertx help\n" + "        This help text.";

		System.out.println(usage);
	}

	public static class CommandlineException extends IllegalArgumentException {

		private static final long serialVersionUID = -979419612962203152L;

		public CommandlineException(String s) {
			super(s);
		}

		public CommandlineException(String message, Throwable cause) {
			super(message, cause);
		}

		public CommandlineException(Throwable cause) {
			super(cause);
		}
	}
}
