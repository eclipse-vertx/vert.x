package org.vertx.java.platform.impl.resolver;/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

public class ModuleIdentifier {

  public final String group;
  public final String name;
  public final String version;

  /*
  Module name should be in the form:
  <group (usually reverse domain name)>.<name>-v<version>
   */
  public ModuleIdentifier(String moduleIdentifier) {
    System.out.println("Mod identifier is:" + moduleIdentifier);
    int versionPos = moduleIdentifier.lastIndexOf("-v");
    if (versionPos == -1) {
      throw genModuleNameException(moduleIdentifier, "does not have a version suffix");
    }
    if (versionPos + 2 >= moduleIdentifier.length()) {
      throw genModuleNameException(moduleIdentifier, "missing version");
    }
    version = moduleIdentifier.substring(2 + versionPos);
    String withoutVers = moduleIdentifier.substring(0, versionPos);
    int namePos = withoutVers.lastIndexOf('.');
    if (namePos == -1) {
      throw genModuleNameException(moduleIdentifier, "must contain a group and a name");
    }
    name = withoutVers.substring(namePos + 1);
    if (namePos == 0) {
      throw genModuleNameException(moduleIdentifier, "missing group");
    }
    group = withoutVers.substring(0, namePos);

    System.out.println("group is:" + group);
    System.out.println("name is:" + name);
    System.out.println("version is:" + version);
  }

  private IllegalArgumentException genModuleNameException(String moduleName, String msg) {
    return new IllegalArgumentException("Module name " + moduleName + " " + msg);
  }

}
