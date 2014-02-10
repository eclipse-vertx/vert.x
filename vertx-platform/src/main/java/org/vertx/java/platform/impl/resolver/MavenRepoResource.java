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

package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.ModuleIdentifier;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Scanner;

public final class MavenRepoResource {
  private static final Logger log = LoggerFactory.getLogger(MavenRepoResource.class);

  private static final DocumentBuilder documentBuilder;

  private final SimpleDateFormat lastUpdatedDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
  private final SimpleDateFormat timestampDateFormat = new SimpleDateFormat("yyyyMMdd.HHmmss");

  private final ModuleIdentifier moduleIdentifier;

  private final String contentRoot;
  private String buildNumber;
  private Date timestamp;
  private Date lastUpdated;

  static {
    try {
      documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
  }

  private MavenRepoResource(Builder builder) {
    moduleIdentifier = builder.moduleIdentifier;
    contentRoot = builder.contentRoot;
    buildNumber = builder.buildNumber;
    lastUpdated = builder.lastUpdated;
    timestamp = builder.timestamp;
    // implicit Objects.requireNonNull

    Objects.requireNonNull(moduleIdentifier);
    Objects.requireNonNull(contentRoot);
  }


  public ModuleIdentifier getModuleIdentifier() {
    return moduleIdentifier;
  }

  public Date getLastUpdated() {
    return lastUpdated;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public String getTimestampAsString() {
    return timestampDateFormat.format(timestamp);
  }

  public String getBuildNumber() {
    return buildNumber;
  }

  // Resource Path with leading /
  public String getResourceDir(boolean isRemote) {
    return PathUtils.getPath(isRemote, contentRoot, moduleIdentifier.getOwner().replace('.', '/'),
        moduleIdentifier.getName(), moduleIdentifier.getVersion());
  }

  public String generateURI(boolean isRemote, boolean modSuffix) {
    StringBuilder fileName = new StringBuilder(moduleIdentifier.getName()).append("-");
    if (timestamp == null) {
      fileName.append(moduleIdentifier.getVersion());
    } else {
      fileName.append(moduleIdentifier.getVersionWithoutSnapshot());
      if (timestamp != null && buildNumber != null) {
        // Timestamped SNAPSHOT
        fileName.append("-").append(getTimestampAsString()).append("-").append(buildNumber);
      }
    }
    fileName.append(modSuffix ? "-mod" : "").append(".zip");
    return PathUtils.getPath(isRemote, getResourceDir(isRemote), fileName.toString());
  }

  // Used to fill timestamp and build number for snapshot resolution
  public void updateWithMetadata(String xmlData) throws IOException {
    try {
      InputSource source = new InputSource(new StringReader(xmlData));
      Document document = documentBuilder.parse(source);

      XPathFactory xpathFactory = XPathFactory.newInstance();
      XPath xpath = xpathFactory.newXPath();

      String lastUpdatedStr = xpath.evaluate("/metadata/versioning/lastUpdated", document);
      this.lastUpdated = lastUpdatedDateFormat.parse(lastUpdatedStr);

      // timestamp and buildNumber are only present in remote based snapshot artifacts
      String timestampStr = xpath.evaluate("/metadata/versioning/snapshot/timestamp", document);
      this.timestamp = timestampStr == null || timestampStr.isEmpty() ? null : timestampDateFormat.parse(timestampStr);

      this.buildNumber = xpath.evaluate("/metadata/versioning/snapshot/buildNumber", document);
    } catch (SAXException | XPathExpressionException | ParseException e) {
      throw new IOException("Error parsing metadata: " + xmlData, e);
    }
  }

  public boolean updateWithMetadata(File metadataFile) {
    // Read metadata for snapshot resolution
    try (Scanner scanner = new Scanner(metadataFile).useDelimiter("\\A")) {
      String data = scanner.next();
      updateWithMetadata(data);
    } catch (IOException e) {
      return false;
    }
    return true;
  }


  public static final class Builder {
    private String buildNumber;
    public Date lastUpdated;
    private Date timestamp;
    private String contentRoot;
    private ModuleIdentifier moduleIdentifier;

    public Builder() {
      this.contentRoot = "/";
    }

    public Builder moduleIdentifier(ModuleIdentifier moduleIdentifier) {
      this.moduleIdentifier = moduleIdentifier;
      return this;
    }

    public Builder buildNumber(String buildNumber) {
      this.buildNumber = buildNumber;
      return this;
    }

    public Builder lastUpdated(Date timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder timestamp(Date timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder contentRoot(String contentRoot) {
      this.contentRoot = contentRoot;
      return this;
    }

    public MavenRepoResource build() {
      return new MavenRepoResource(this);
    }
  }
}
