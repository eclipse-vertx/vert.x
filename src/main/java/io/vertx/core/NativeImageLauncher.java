/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

/**
 * A {@code main()} class that can be used to create Vert.x instance and deploy a verticle when running as a native image.
 *
 * @author Paulo Lopes <a href="mailto:plopes@redhat.com">plopes@redhat.com</a>
 */
public class NativeImageLauncher extends Launcher {

  public static void main(String[] args) {
    // disable async DNS as it fails on native
    System.setProperty("vertx.disableDnsResolver", "true");
    final Launcher launcher = new NativeImageLauncher();
    // remove commands which are not meant to run on native
    launcher.unregister("bare");
    launcher.unregister("list");
    launcher.unregister("start");
    launcher.unregister("stop");
    // delegate to the main class
    launcher.dispatch(args);
  }
}
