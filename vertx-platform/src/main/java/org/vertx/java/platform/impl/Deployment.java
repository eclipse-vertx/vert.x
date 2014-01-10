/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl;

import org.vertx.java.core.json.JsonObject;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Deployment {
  public final String name;
  public final String main;
  public final ModuleIdentifier modID;
  public final int instances;
  public final JsonObject config;
  public final URL[] classpath;
  public final URL[] includedClasspath;
  public final File modDir;
  public final List<VerticleHolder> verticles = new CopyOnWriteArrayList<>();
  public final List<String> childDeployments = new CopyOnWriteArrayList<>();
  public final String parentDeploymentName;
  public final ModuleReference moduleReference;
  public final boolean autoRedeploy;
  public final boolean ha;
  public final boolean loadFromModuleFirst;

  public Deployment(String name, String main, ModuleIdentifier modID, int instances, JsonObject config,
                    URL[] classpath, URL[] includedClasspath, File modDir, String parentDeploymentName,
                    ModuleReference moduleReference,
                    boolean autoRedeploy, boolean ha,
                    boolean loadFromModuleFirst) {
    this.name = name;
    this.main = main;
    this.modID = modID;
    this.instances = instances;
    this.config = config;
    this.classpath = classpath;
    this.includedClasspath = includedClasspath;
    this.modDir = modDir;
    this.parentDeploymentName = parentDeploymentName;
    this.moduleReference = moduleReference;
    this.autoRedeploy = autoRedeploy;
    this.ha = ha;
    this.loadFromModuleFirst = loadFromModuleFirst;
  }
}