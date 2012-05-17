/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.jmx;

import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * @author pidster
 *
 */
public class JMXUtil {
	
	private static final Logger log = LoggerFactory.getLogger(JMXUtil.class);
	
	private static MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

	public static ObjectName newObjectName(String name, Object... args) {
		try {
			String objName = String.format(name, args);
			return new ObjectName(objName);
		} catch (MalformedObjectNameException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void register(Object mbean, String name, Object... args) {
		ObjectName objectName = newObjectName(name, args);
		register(mbean, objectName);
	}
	
	public static void register(Object mbean, String name) {
		ObjectName objectName = newObjectName(name);
		register(mbean, objectName);
	}
	
	public static void register(Object mbean, ObjectName name) {

		try {
			mbeanServer.registerMBean(mbean, name);

		} catch (InstanceAlreadyExistsException e) {
			log.error(e.getMessage(), e);

		} catch (MBeanRegistrationException e) {
			log.error(e.getMessage(), e);

		} catch (NotCompliantMBeanException e) {
			log.error(e.getMessage(), e);
		}		
	}

	public static void unregister(String name) {
		ObjectName objectName = newObjectName(name);
		unregister(objectName);
	}
	
	public static void unregister(ObjectName name) {
		
		try {
			mbeanServer.unregisterMBean(name);

		} catch (InstanceNotFoundException e) {
			log.error(e.getMessage(), e);

		} catch (MBeanRegistrationException e) {
			log.error(e.getMessage(), e);
		}		
	}
}
