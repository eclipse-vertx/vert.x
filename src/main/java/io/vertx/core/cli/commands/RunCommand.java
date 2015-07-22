package io.vertx.core.cli.commands;

import io.vertx.core.*;
import io.vertx.core.cli.*;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.VertxMetricsFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The vert.x run command that let you execute verticles or start a bare instance.
 */
@Summary("Runs a verticle called <main> in its own instance of vert.x.")
public class RunCommand extends DefaultCommand {

  public static final String VERTX_OPTIONS_PROP_PREFIX = "vertx.options.";
  public static final String DEPLOYMENT_OPTIONS_PROP_PREFIX = "vertx.deployment.options.";
  public static final String METRICS_OPTIONS_PROP_PREFIX = "vertx.metrics.options.";

  private static final String PATH_SEP = System.getProperty("path.separator");
  private static final Logger log = LoggerFactory.getLogger(RunCommand.class);

  protected Vertx vertx;
  protected VertxOptions options;
  protected DeploymentOptions deploymentOptions;

  private List<String> classpath;

  private boolean cluster;
  private int clusterPort;
  private String clusterHost;

  private int instances;
  private String config;
  private boolean worker;

  private boolean ha;
  private int quorum;
  private String haGroup;

  private String mainVerticle;


  @Option(shortName = "cp", longName = "classpath", name = "classpath")
  @DefaultValue(".")
  @Description("Provides an extra classpath to be used for the verticle deployment.")
  public void setClasspath(String classpath) {
    this.classpath = Arrays.asList(classpath.split(PATH_SEP));
  }

  @Option(longName = "ha", acceptValue = false)
  @Description("If specified the verticle will be deployed as a high availability (HA) deployment. This means it can " +
      "fail over to any other nodes in the cluster started with the same HA group.")
  public void setHighAvailability(boolean ha) {
    this.ha = ha;
  }

  @Option(longName = "quorum", name = "q")
  @Description("Used in conjunction with -ha this specifies the minimum number of nodes in the cluster for any HA " +
      "deploymentIDs to be active. Defaults to 1.")
  @DefaultValue("-1")
  public void setQuorum(int quorum) {
    this.quorum = quorum;
  }

  @Option(longName = "hagroup", name = "group")
  @Description("used in conjunction with -ha this specifies the HA group this node will join. There can be multiple " +
      "HA groups in a cluster. Nodes will only failover to other nodes in the same group. Defaults to '__DEFAULT__'.")
  @DefaultValue("__DEFAULT__")
  public void setHAGroup(String group) {
    this.haGroup = group;
  }

  @Option(longName = "cluster", acceptValue = false)
  @Description("If specified then the vert.x instance will form a cluster with any other vert.x instances on the " +
      "network.")
  public void setCluster(boolean cluster) {
    this.cluster = cluster;
  }

  @Option(longName = "cluster-port", name = "port")
  @Description("Port to use for cluster communication. Default is 0 which means choose a spare random port.")
  @DefaultValue("0")
  public void setClusterPort(int port) {
    this.clusterPort = port;
  }

  @Option(longName = "cluster-host", name = "host")
  @Description("host to bind to for cluster communication. If this is not specified vert.x will attempt to choose one" +
      " from the available interfaces.")
  public void setClusterHost(String host) {
    this.clusterHost = host;
  }

  @Option(longName = "worker")
  @Description("If specified then the verticle is a worker verticle.")
  public void setWorker(boolean worker) {
    this.worker = worker;
  }

  @Option(longName = "instances")
  @DefaultValue("1")
  @Description("Specifies how many instances of the verticle will be deployed. Defaults to 1.")
  public void setInstances(int instances) {
    this.instances = instances;
  }

  @Option(longName = "conf", name = "config")
  @Description("Specifies configuration that should be provided to the verticle. <config> should reference either a " +
      "text file containing a valid JSON object which represents the configuration OR be a JSON string.")
  public void setConfig(String configuration) {
    this.config = configuration;
  }

  @Argument(index = 0, name = "main", required = false)
  public void setMainVerticle(String verticle) {
    this.mainVerticle = verticle;
  }

  /**
   * Validates the command line parameters.
   *
   * @throws CommandLineException - validation failed
   */
  @Override
  public void setup() throws CommandLineException {
    super.setup();

    // If cluster-host and / or port is set, cluster need to have been explicitly set
    if ((! isClustered()) &&
        (executionContext.getCommandLine().hasBeenSet("cluster-host") || executionContext.getCommandLine().hasBeenSet("cluster-port"))) {
      throw new CommandLineException("The option -cluster-host and -cluster-port requires -cluster to be enabled");
    }

    // If quorum and / or ha-group, ha need to have been explicitly set
    if (!ha &&
        (executionContext.getCommandLine().hasBeenSet("hagroup") || executionContext.getCommandLine().hasBeenSet("quorum"))) {
      throw new CommandLineException("The option -hagroup and -quorum requires -ha to be enabled");
    }
  }

  public boolean isClustered() {
    return cluster || ha;
  }


  @Override
  public String name() {
    return "run";
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    Vertx vertx = startVertx();
    if (vertx == null) {
      // Throwable should have been logged at this point
      return;
    }

    if (isBare()) {
      return;
    }

    JsonObject conf = getConfiguration();

    String message = (worker) ? "deploying worker verticle" : "deploying verticle";
    deploymentOptions = new DeploymentOptions();
    configureFromSystemProperties(deploymentOptions, DEPLOYMENT_OPTIONS_PROP_PREFIX);

    deploymentOptions.setConfig(conf).setWorker(worker).setHa(ha).setInstances(instances);

    beforeDeployingVerticle(deploymentOptions);

    vertx.deployVerticle(mainVerticle, deploymentOptions, createLoggingHandler(message, res -> {
      if (res.failed()) {
        // Failed to deploy
        handleDeployFailed(res.cause(), vertx, mainVerticle, deploymentOptions);
      }
    }));

  }

  private <T> AsyncResultHandler<T> createLoggingHandler(final String message, final Handler<AsyncResult<T>> completionHandler) {
    return res -> {
      if (res.failed()) {
        Throwable cause = res.cause();
        if (cause instanceof VertxException) {
          VertxException ve = (VertxException) cause;
          log.error(ve.getMessage());
          if (ve.getCause() != null) {
            log.error(ve.getCause());
          }
        } else {
          log.error("Failed in " + message, cause);
        }
      } else {
        log.info("Succeeded in " + message);
      }
      if (completionHandler != null) {
        completionHandler.handle(res);
      }
    };
  }

  private JsonObject getConfiguration() {
    JsonObject conf = null;
    if (config != null) {
      try (Scanner scanner = new Scanner(new File(config)).useDelimiter("\\A")) {
        String sconf = scanner.next();
        try {
          conf = new JsonObject(sconf);
        } catch (DecodeException e) {
          log.error("Configuration file " + sconf + " does not contain a valid JSON object");
          return null;
        }
      } catch (FileNotFoundException e) {
        try {
          conf = new JsonObject(config);
        } catch (DecodeException e2) {
          log.error("-conf option does not point to a file and is not valid JSON: " + config);
          return null;
        }
      }
    } else {
      conf = null;
    }
    return conf;
  }

  private Vertx startVertx() {
    MetricsOptions metricsOptions = getMetricsOptions();
    options = new VertxOptions().setMetricsOptions(metricsOptions);
    configureFromSystemProperties(options, VERTX_OPTIONS_PROP_PREFIX);

    if (isClustered()) {
      log.info("Starting clustering...");
      if (clusterHost == null) {
        clusterHost = getDefaultAddress();
        if (clusterHost == null) {
          log.error("Unable to find a default network interface for clustering. Please specify one using -cluster-host");
          return null;
        } else {
          log.info("No cluster-host specified so using address " + clusterHost);
        }
      }
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<AsyncResult<Vertx>> result = new AtomicReference<>();

      options.setClusterHost(clusterHost).setClusterPort(clusterPort).setClustered(true);
      if (ha) {
        options.setHAEnabled(true);
        if (haGroup != null) {
          options.setHAGroup(haGroup);
        }
        if (quorum != -1) {
          options.setQuorumSize(quorum);
        }
      }


      beforeStartingVertx(options);
      Vertx.clusteredVertx(options, ar -> {
        result.set(ar);
        latch.countDown();
      });
      try {
        if (!latch.await(2, TimeUnit.MINUTES)) {
          log.error("Timed out in starting clustered Vert.x");
          return null;
        }
      } catch (InterruptedException e) {
        log.error("Thread interrupted in startup");
        return null;
      }
      if (result.get().failed()) {
        log.error("Failed to form cluster");
        result.get().cause().printStackTrace();
        return null;
      }
      vertx = result.get().result();
    } else {
      beforeStartingVertx(options);
      vertx = Vertx.vertx(options);
    }
    addShutdownHook();
    afterStartingVertx();
    return vertx;
  }


  private void afterStartingVertx() {
    final Object main = executionContext.main();
    if (main instanceof VertxLifeycleHooks) {
      ((VertxLifeycleHooks) main).afterStartingVertx(vertx);
    }
  }

  private void beforeStartingVertx(VertxOptions options) {
    final Object main = executionContext.main();
    if (main instanceof VertxLifeycleHooks) {
      ((VertxLifeycleHooks) main).beforeStartingVertx(options);
    }
  }

  private void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {
    final Object main = executionContext.main();
    if (main instanceof VertxLifeycleHooks) {
      ((VertxLifeycleHooks) main).beforeDeployingVerticle(deploymentOptions);
    }
  }

  private void handleDeployFailed(Throwable cause, Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions) {
    final Object main = executionContext.main();
    if (main instanceof VertxLifeycleHooks) {
      ((VertxLifeycleHooks) main).handleDeployFailed(vertx, mainVerticle, deploymentOptions, cause);
    }
  }

  private MetricsOptions getMetricsOptions() {
    MetricsOptions metricsOptions;
    ServiceLoader<VertxMetricsFactory> factories = ServiceLoader.load(VertxMetricsFactory.class);
    if (factories.iterator().hasNext()) {
      VertxMetricsFactory factory = factories.iterator().next();
      metricsOptions = factory.newOptions();
    } else {
      metricsOptions = new MetricsOptions();
    }
    configureFromSystemProperties(metricsOptions, METRICS_OPTIONS_PROP_PREFIX);
    return metricsOptions;
  }

  private void configureFromSystemProperties(Object options, String prefix) {
    Properties props = System.getProperties();
    Enumeration e = props.propertyNames();
    // Uhh, properties suck
    while (e.hasMoreElements()) {
      String propName = (String) e.nextElement();
      String propVal = props.getProperty(propName);
      if (propName.startsWith(prefix)) {
        String fieldName = propName.substring(prefix.length());
        Method setter = getSetter(fieldName, options.getClass());
        if (setter == null) {
          log.warn("No such property to configure on options: " + options.getClass().getName() + "." + fieldName);
          continue;
        }
        Class<?> argType = setter.getParameterTypes()[0];
        Object arg;
        try {
          if (argType.equals(String.class)) {
            arg = propVal;
          } else if (argType.equals(int.class)) {
            arg = Integer.valueOf(propVal);
          } else if (argType.equals(long.class)) {
            arg = Long.valueOf(propVal);
          } else if (argType.equals(boolean.class)) {
            arg = Boolean.valueOf(propVal);
          } else {
            log.warn("Invalid type for setter: " + argType);
            continue;
          }
        } catch (IllegalArgumentException e2) {
          log.warn("Invalid argtype:" + argType + " on options: " + options.getClass().getName() + "." + fieldName);
          continue;
        }
        try {
          setter.invoke(options, arg);
        } catch (Exception ex) {
          throw new VertxException("Failed to invoke setter: " + setter, ex);
        }
      }
    }
  }

  private Method getSetter(String fieldName, Class<?> clazz) {
    Method[] meths = clazz.getDeclaredMethods();
    for (Method meth : meths) {
      if (("set" + fieldName).toLowerCase().equals(meth.getName().toLowerCase())) {
        return meth;
      }
    }
    return null;
  }

  private void addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        CountDownLatch latch = new CountDownLatch(1);
        vertx.close(ar -> {
          if (!ar.succeeded()) {
            log.error("Failure in stopping Vert.x", ar.cause());
          }
          latch.countDown();
        });
        try {
          if (!latch.await(2, TimeUnit.MINUTES)) {
            log.error("Timed out waiting to undeploy all");
          }
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
      }
    });
  }

  /*
  Get default interface to use since the user hasn't specified one
   */
  private String getDefaultAddress() {
    Enumeration<NetworkInterface> nets;
    try {
      nets = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException e) {
      return null;
    }
    NetworkInterface netinf;
    while (nets.hasMoreElements()) {
      netinf = nets.nextElement();

      Enumeration<InetAddress> addresses = netinf.getInetAddresses();

      while (addresses.hasMoreElements()) {
        InetAddress address = addresses.nextElement();
        if (!address.isAnyLocalAddress() && !address.isMulticastAddress()
            && !(address instanceof Inet6Address)) {
          return address.getHostAddress();
        }
      }
    }
    return null;
  }


  public boolean isBare() {
    return ha && mainVerticle == null;
  }
}
