/*
 * Copyright 1998-2015 John Caron and University Corporation for Atmospheric Research/Unidata
 *
 *  Portions of this software were developed by the Unidata Program at the
 *  University Corporation for Atmospheric Research.
 *
 *  Access and use of this software shall impose the following obligations
 *  and understandings on the user. The user is granted the right, without
 *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  this software, and any derivative works thereof, and its supporting
 *  documentation for any purpose whatsoever, provided that this entire
 *  notice appears in all copies of the software, derivative works and
 *  supporting documentation.  Further, UCAR requests that the user credit
 *  UCAR/Unidata in any publications that result from the use of this
 *  software or in any product that includes this software. The names UCAR
 *  and/or Unidata, however, may not be used in any advertising or publicity
 *  to endorse or promote any products or commercial entity unless specific
 *  written permission is obtained from UCAR/Unidata. The user also
 *  understands that UCAR/Unidata is not obligated to provide the user with
 *  any support, consulting, training or assistance of any kind with regard
 *  to the use, operation and performance of this software nor to provide
 *  the user with any updates, revisions, new versions or "bug fixes."
 *
 *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package thredds.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.util.Log4jWebConfigurer;
import thredds.featurecollection.InvDatasetFeatureCollection;
import thredds.util.filesource.*;
import ucar.nc2.util.IO;
import ucar.unidata.util.StringUtil2;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * TDScontext implements ServletContextAware so it gets a ServletContext and performs most initial THREDDS set up:
 * - checks version
 * - check for latest stable and development release versions
 * - sets the content directory
 * - reads persistent user defined params and runs ThreddsConfig.init
 * - creates, if don't exist, log and public dirs in content directory
 * - Get default and jsp dispatchers from servletContext
 * - Creates and initializes the TdsConfigMapper
 *
 * LOOK would be nice to make Immutable
 * @author edavis
 * @since 4.0
 */
@Component("TdsContext")
public final class TdsContext implements ServletContextAware, InitializingBean, DisposableBean {
  private final Logger logServerStartup = LoggerFactory.getLogger("serverStartup");

  /////////////////
  // Properties from tds.properties

  // The values for these properties all come from WEB-INF/classes/thredds/server/tds.properties except for
  // "tds.content.root.path", which must be defined on the command line.

  @Value("${tds.version}")
  private String tdsVersion;

  @Value("${tds.version.builddate}")
  private String tdsVersionBuildDate;

  @Value("${tds.content.path}")
  private String contentPathProperty;

  @Value("${tds.content.startup.path}")
  private String contentStartupPathProperty;

  @Value("${tds.config.file}")
  private String configFileProperty;

  @Value("${tds.content.root.path}")
  private String contentRootPathProperty; // wants a trailing slash

  private ServletContext servletContext;

  ///////////////////////////////////////////

  private String contextPath;
  private String webappDisplayName;

  private File servletRootDirectory;       // servletContext.getRealPath("/")
  private File contentRootDir;             // ${tds.content.root.path}
  private File threddsDirectory;           // ${tds.content.root.path}/thredds
  private File publicContentDirectory;
  private File startupContentDirectory;
  private File tomcatLogDir;

  private FileSource publicContentDirSource;
  // private FileSource catalogRootDirSource;  // look for catalog files at this(ese) root(s)

  private RequestDispatcher defaultRequestDispatcher;

  //////////// called by Spring lifecycle management
  @Override
  public void setServletContext(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  public ServletContext getServletContext() {
    return servletContext;
  }

  @Override
  public void destroy() {
    logServerStartup.info("TdsContext: shutdownLogging()");
    Log4jWebConfigurer.shutdownLogging(servletContext); // probably not needed anymore with log4j-web in classpath
  }

  @Override
  public void afterPropertiesSet() {
    if (servletContext == null)
      throw new IllegalArgumentException("ServletContext must not be null.");

    // Set the webapp name. display-name from web.xml
    this.webappDisplayName = servletContext.getServletContextName();

    // Set the context path, eg "thredds"
    contextPath = servletContext.getContextPath();
    InvDatasetFeatureCollection.setContextName(contextPath);

    // Set the root directory and source.
    String rootPath = servletContext.getRealPath("/");
    if (rootPath == null) {
      String msg = "Webapp [" + this.webappDisplayName + "] must run with exploded deployment directory (not from .war).";
      logServerStartup.error("TdsContext.init(): " + msg);
      throw new IllegalStateException(msg);
    }
    this.servletRootDirectory = new File(rootPath);

    // Set the startup (initial install) content directory and source.
    this.startupContentDirectory = new File(this.servletRootDirectory, this.contentStartupPathProperty);
    DescendantFileSource startupContentDirSource = new BasicDescendantFileSource(this.startupContentDirectory);
    this.startupContentDirectory = startupContentDirSource.getRootDirectory();

    // set the tomcat logging directory
    try {
      String base = System.getProperty("catalina.base");
      if (base != null) {
        this.tomcatLogDir = new File(base, "logs").getCanonicalFile();
        if (!this.tomcatLogDir.exists()) {
          String msg = "'catalina.base' directory not found: " + this.tomcatLogDir;
          logServerStartup.error("TdsContext.init(): " + msg);
        }
      } else {
        String msg = "'catalina.base' property not found - probably not a tomcat server";
        logServerStartup.warn("TdsContext.init(): " + msg);
      }

    } catch (IOException e) {
      String msg = "tomcatLogDir could not be created";
      logServerStartup.error("TdsContext.init(): " + msg);
    }

    ////////////////////////////////
    String contentRootPathKey = "tds.content.root.path";

    // In applicationContext-tdsConfig.xml, we have ignoreUnresolvablePlaceholders set to "true".
    // As a result, when properties aren't defined, they will keep their placeholder String.
    // In this case, that's "${tds.content.root.path}".
    if (this.contentRootPathProperty.equals("${tds.content.root.path}")) {
      String message = String.format("\"%s\" property isn't defined.", contentRootPathKey);
      logServerStartup.error(message);
      throw new IllegalStateException(message);
    }

    contentRootPathProperty = StringUtil2.replace(contentRootPathProperty, "\\", "/");
    if (!contentRootPathProperty.endsWith("/"))
      contentRootPathProperty += "/";

    // Set the content directory and source.
    this.contentRootDir = new File(this.contentRootPathProperty);
    if (!contentRootDir.isAbsolute())
      this.threddsDirectory = new File(new File(this.servletRootDirectory, this.contentRootPathProperty), this.contentPathProperty);
    else {
      if (contentRootDir.isDirectory())
        this.threddsDirectory = new File(contentRootDir, this.contentPathProperty);
      else {
        String msg = "Content root directory [" + this.contentRootPathProperty + "] not a directory.";
        logServerStartup.error("TdsContext.init(): " + msg);
        throw new IllegalStateException(msg);
      }
    }

    // If content directory exists, make sure it is a directory.
    DescendantFileSource contentDirSource;
    if (this.threddsDirectory.isDirectory()) {
      contentDirSource = new BasicDescendantFileSource(StringUtils.cleanPath(this.threddsDirectory.getAbsolutePath()));
      this.threddsDirectory = contentDirSource.getRootDirectory();
    } else {
      String message = String.format(
              "TdsContext.init(): Content directory is not a directory: %s", this.threddsDirectory.getAbsolutePath());
      logServerStartup.error(message);
      throw new IllegalStateException(message);
    }

    // public content
    this.publicContentDirectory = new File(this.threddsDirectory, "public");
    if (!publicContentDirectory.exists()) {
      if (!publicContentDirectory.mkdirs()) {
        String msg = "Couldn't create TDS public directory [" + publicContentDirectory.getPath() + "].";
        logServerStartup.error("TdsContext.init(): " + msg);
        throw new IllegalStateException(msg);
      }
    }
    this.publicContentDirSource = new BasicDescendantFileSource(this.publicContentDirectory);

    /* places to look for catalogs ??
    List<DescendantFileSource> chain = new ArrayList<>();
    DescendantFileSource contentMinusPublicSource =
            new BasicWithExclusionsDescendantFileSource(this.threddsDirectory, Collections.singletonList("public"));
    chain.add(contentMinusPublicSource);
    this.catalogRootDirSource = new ChainedFileSource(chain); */

    //jspRequestDispatcher = servletContext.getNamedDispatcher("jsp");
    defaultRequestDispatcher = servletContext.getNamedDispatcher("default");

    //////////////////////////////////// Copy default startup files, if necessary ////////////////////////////////////

    try {
      File catalogFile = new File(threddsDirectory, "catalog.xml");
      if (!catalogFile.exists()) {
        File defaultCatalogFile = new File(startupContentDirectory, "catalog.xml");
        logServerStartup.info("TdsContext.init(): Copying default catalog file from {}.", defaultCatalogFile);
        IO.copyFile(defaultCatalogFile, catalogFile);

        File enhancedCatalogFile = new File(threddsDirectory, "enhancedCatalog.xml");
        File defaultEnhancedCatalogFile = new File(startupContentDirectory, "enhancedCatalog.xml");
        logServerStartup.info("TdsContext.init(): Copying default enhanced catalog file from {}.", defaultEnhancedCatalogFile);
        IO.copyFile(defaultEnhancedCatalogFile, enhancedCatalogFile);

        File dataDir = new File(new File(threddsDirectory, "public"), "testdata");
        File defaultDataDir = new File(new File(startupContentDirectory, "public"), "testdata");
        logServerStartup.info("TdsContext.init(): Copying default testdata directory from {}.", defaultDataDir);
        IO.copyDirTree(defaultDataDir.getCanonicalPath(), dataDir.getCanonicalPath());
      }

      File threddsConfigFile = new File(threddsDirectory, "threddsConfig.xml");
      if (!threddsConfigFile.exists()) {
        File defaultThreddsConfigFile = new File(startupContentDirectory, "threddsConfig.xml");
        logServerStartup.info("TdsContext.init(): Copying default THREDDS config file from {}.", defaultThreddsConfigFile);
        IO.copyFile(defaultThreddsConfigFile, threddsConfigFile);
      }

      File wmsConfigXmlFile = new File(threddsDirectory, "wmsConfig.xml");
      if (!wmsConfigXmlFile.exists()) {
        File defaultWmsConfigXmlFile = new File(startupContentDirectory, "wmsConfig.xml");
        logServerStartup.info("TdsContext.init(): Copying default WMS config file from {}.", defaultWmsConfigXmlFile);
        IO.copyFile(defaultWmsConfigXmlFile, wmsConfigXmlFile);

        File wmsConfigDtdFile = new File(threddsDirectory, "wmsConfig.dtd");
        File defaultWmsConfigDtdFile = new File(startupContentDirectory, "wmsConfig.dtd");
        logServerStartup.info("TdsContext.init(): Copying default WMS config DTD from {}.", defaultWmsConfigDtdFile);
        IO.copyFile(defaultWmsConfigDtdFile, wmsConfigDtdFile);
      }
    } catch (IOException e) {
      String message = String.format("Could not copy default startup files to %s.", threddsDirectory);
      logServerStartup.error("TdsContext.init(): " + message);
      throw new IllegalStateException(message, e);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // logging

    File logDir = new File(this.threddsDirectory, "logs");
    if (!logDir.exists()) {
      if (!logDir.mkdirs()) {
        String msg = "Couldn't create TDS log directory [" + logDir.getPath() + "].";
        logServerStartup.error("TdsContext.init(): " + msg);
        throw new IllegalStateException(msg);
      }
    }
    String loggingDirectory = StringUtil2.substitute(logDir.getPath(), "\\", "/");
    System.setProperty("tds.log.dir", loggingDirectory); // variable substitution

    // LOOK Remove log4j init JC 6/13/2012
    // which is used in log4j.xml file loaded here.
    // LOOK Remove Log4jWebConfigurer,initLogging - depends on log4g v1, we are using v2 JC 9/2/2013
    // Log4jWebConfigurer.initLogging( servletContext );
    logServerStartup.info("TdsContext version= " + getVersionInfo());
    logServerStartup.info("TdsContext intialized logging in " + logDir.getPath());
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("TdsContext{");
    sb.append("\n  contextPath='").append(contextPath).append('\'');
    sb.append("\n  webappName='").append(webappDisplayName).append('\'');
    sb.append("\n  webappVersion='").append(tdsVersion).append('\'');
    sb.append("\n  webappVersionBuildDate='").append(tdsVersionBuildDate).append('\'');
    sb.append("\n");
    sb.append("\n  contentPath= '").append(contentPathProperty).append('\'');
    sb.append("\n  contentRootPath= '").append(contentRootPathProperty).append('\'');
    sb.append("\n  contentStartupPath= '").append(contentStartupPathProperty).append('\'');
    sb.append("\n  configFile= '").append(configFileProperty).append('\'');
    sb.append("\n");
    sb.append("\n  servletRootDir=   ").append(servletRootDirectory);
    sb.append("\n  contentRootDir=   ").append(contentRootDir);
    sb.append("\n  threddsDirectory= ").append(threddsDirectory);
    sb.append("\n  publicContentDir= ").append(publicContentDirectory);
    sb.append("\n  startupContentDir=").append(startupContentDirectory);
    sb.append("\n  tomcatLogDir=     ").append(tomcatLogDir);
    sb.append("\n");
    sb.append("\n  publicContentDirSource= ").append(publicContentDirSource);
    // sb.append("\n  catalogRootDirSource=").append(catalogRootDirSource);
    sb.append('}');
    return sb.toString();
  }

  ////////////////////////////////////////////////////
  // public getters

  /**
   * Return the context path under which this web app is running (e.g., "/thredds").
   *
   * @return the context path.
   */
  public String getContextPath() {
    return contextPath;
  }

  /**
   * Return the full version string (<major>.<minor>.<bug>.<build>)*
   * @return the full version string.
   */
  public String getWebappVersion() {
    return this.tdsVersion;
  }

  public String getTdsVersionBuildDate() {
    return this.tdsVersionBuildDate;
  }

  public String getVersionInfo() {
    Formatter f = new Formatter();
    f.format("%s", getWebappVersion());
    if (getTdsVersionBuildDate() != null) {
      f.format(" - %s", getTdsVersionBuildDate());
    }
    return f.toString();
  }

  /**
   * @return the name of the webapp as given by the display-name element in web.xml.
   */
  public String getWebappDisplayName() {
    return this.webappDisplayName;
  }

  /**
   * Return the web apps root directory (i.e., getRealPath( "/")).
   * Typically {tomcat}/webapps/thredds
   * @return the root directory for the web app.
   */
  public File getServletRootDirectory() {
    return servletRootDirectory;
  }

  public File getTomcatLogDirectory() {
    return tomcatLogDir;
  }

  /**
   * Return File for content directory (exists() may be false).
   *
   * @return a File to the content directory.
   */
  public File getThreddsDirectory() {
    return threddsDirectory;
  }

  public File getContentRootDir() {
    return contentRootDir;
  }

  /* public FileSource getCatalogRootDirSource() {
    return this.catalogRootDirSource;
  }  */

  // {tomcat}/content/thredds/public
  public FileSource getPublicContentDirSource() {
    return this.publicContentDirSource;
  }

  public RequestDispatcher getDefaultRequestDispatcher() {
    return this.defaultRequestDispatcher;
  }

  public String getContentRootPathProperty() {
    return this.contentRootPathProperty;
  }

  public String getConfigFileProperty() {
    return this.configFileProperty;
  }


  /////////////////////////////////////////////////////

  // used by MockTdsContextLoader
  public void setContentRootPathProperty(String contentRootPathProperty) {
    this.contentRootPathProperty = contentRootPathProperty;
  }

}
