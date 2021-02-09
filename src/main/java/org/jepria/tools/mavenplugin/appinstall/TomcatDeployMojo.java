package org.jepria.tools.mavenplugin.appinstall;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

import org.jepria.tools.mavenplugin.appinstall.util.PluginUtil;
import org.jepria.tools.mavenplugin.appinstall.version.FinishAppInstall;
import org.jepria.tools.mavenplugin.appinstall.version.StartAppInstall;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Base64;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Goal which deploys war-archive to Tomcat.
 *
 */
@Mojo( name = "tomcat.deploy", defaultPhase = LifecyclePhase.NONE, requiresDependencyResolution = ResolutionScope.TEST )
public class TomcatDeployMojo extends AbstractMojo
{
  
  /**
   * Plugin configuration to use in the execution.
   */
  @Parameter
  private XmlPlexusConfiguration configuration;

  /**
   * Mojo input parameter, Tomcat deployment path.
   */
  @Parameter( property = "deploymentPath", required = true )
  private String deploymentPath;

  /**
   * Mojo input parameter, Tomcat context path.
   */
  @Parameter( property = "contextPath", required = true )
  private String contextPath;

  /**
   * Mojo input parameter, Tomcat user login.
   */
  @Parameter( property = "username", required = true )
  private String username;

  /**
   * Mojo input parameter, Tomcat user password.
   */
  @Parameter( property = "password", required = true )
  private String password;

  /**
   * Mojo input parameter, operator (in format "login/password") who loads information through ModuleInfo.
   */
  @Parameter( property = "loadOperatorId", required = true )
  private String loadOperatorId;

  /**
   * Mojo input parameter, path to war file for deployment.
   */
  @Parameter( property = "warFile", required = true )
  private String warFile;

  /**
   * Mojo input parameter, flag that skips saving installation information.
   */
  @Parameter( defaultValue = "false", property = "skipSaveInstallInfo" )
  private boolean skipSaveInstallInfo;

  /**
   * Mojo input parameter, path to map.xml.
   */
  @Parameter( property = "mapXmlPath" )
  private String mapXmlPath;

  /**
   * The project currently being build.
   */
  @Parameter( defaultValue = "${project}", readonly = true )
  private MavenProject mavenProject;

  /**
   * The current Maven session.
   */
  @Parameter( defaultValue = "${session}", readonly = true )
  private MavenSession mavenSession;

  /**
   * The Maven BuildPluginManager component.
   */
  @Component
  private BuildPluginManager pluginManager;

  /**
   * Auxiliary parameters.
   */
  private String domain, port, /*serverName,*/ appInstallResultId, svnPath, svnVersionInfo, version;

  public void execute() throws MojoExecutionException
  {
    String lineEnd = System.getProperty("line.separator");
    getLog().info("Application deployment started...");
    extractDeploymentPathComponents();
    checkingTomcatAvailability();
    if (skipSaveInstallInfo) {
      getLog().warn("Skipping saving installation information...");
    } else {
      checkingModuleInfoAvailability();
      extractSvnVersionInfo();
      getLog().info("Saving installation information before application deployment...");
      StartAppInstall startInstall= new StartAppInstall();
      startInstall.setPort(port);
      startInstall.setDeploymentPath(deploymentPath);
      startInstall.setLoadOperatorId(loadOperatorId);
      startInstall.setSkipSaveInstallInfo("0");
      startInstall.setVersion(version);
      startInstall.setSvnPath(svnPath);
      startInstall.setSvnVersionInfo(svnVersionInfo);
      startInstall.setMapXmlPath(mapXmlPath);
      startInstall.callVersionServlet();
      appInstallResultId = startInstall.getAppInstallResultId();
    }
    getLog().info("Deploying " + warFile + " to: " + deploymentPath);
    getLog().info("Deploy to Tomcat...");
    getLog().info("LOGIN: " + username);
    String statusCode = "0";
    String errorMessage = "";
    try {
      executeMojo(
        plugin(
          groupId("org.apache.tomcat.maven"),
          artifactId("tomcat7-maven-plugin"),
          version("2.2")
        ),
        goal("deploy-only"),
        configuration(
          element(name("ignorePackaging"), "true"),
          element(name("url"), deploymentPath),
          element(name("username"), username),
          element(name("password"), password),
          element(name("path"), contextPath),
          element(name("warFile"), warFile),
          element(name("update"), "true")
        ),
        executionEnvironment(
          mavenProject,
          mavenSession,
          pluginManager
        )
      );
    } catch(Exception e) {
      statusCode = "1";
      errorMessage = e.getMessage();
    }
    if ("1".equals(statusCode)) {
      getLog().error("Unsuccessful application deployment!" + lineEnd + errorMessage);
    } else {
      getLog().info("Successful deployment of the application!");
    }
    if (skipSaveInstallInfo) {
      getLog().warn("Skipping saving the deployment result...");
    } else {
      getLog().info("Saving the deployment result...");
      FinishAppInstall finishInstall= new FinishAppInstall();
      finishInstall.setPort(port);
      finishInstall.setDeploymentPath(deploymentPath);
      finishInstall.setLoadOperatorId(loadOperatorId);
      finishInstall.setAppInstallResultId(appInstallResultId);
      finishInstall.setStatusCode(statusCode);
      finishInstall.setErrorMessage(errorMessage);
      finishInstall.callVersionServlet();
    }
  }

  private String commandExecution(String... command) throws IOException {
    BufferedReader reader = null;
    StringBuilder sb = new StringBuilder();
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);
      Process process = builder.start();
      InputStream is = process.getInputStream();
      reader = new BufferedReader(new InputStreamReader(is));
      String line = null;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    } finally {
        if (reader != null) reader.close();
    }
    return sb.toString();
  }

  private void extractSvnVersionInfo() throws MojoExecutionException {
    getLog().info("Extracting SVN information...");
    String lineEnd = System.getProperty("line.separator");

    String svnVersionRaw = "";
    try {
      svnVersionRaw = commandExecution("svnversion");
    } catch (Exception e) {
        throw new MojoExecutionException("Error executing command \"svnversion\"!"+lineEnd+e.getMessage(), e);
    }
    svnVersionInfo = PluginUtil.extractSubStrByPattern(svnVersionRaw, "^(\\d+\\D*:?\\d+\\D*)$", 1, "");
    getLog().info("SVN Version Info: " + svnVersionInfo);

    String svnPathRaw = "";
    try {
      svnPathRaw = commandExecution("svn", "info", "--xml");
    } catch (Exception e) {
        throw new MojoExecutionException("Error executing command \"svn info --xml\"!"+lineEnd+e.getMessage(), e);
    }
    svnPath = PluginUtil.extractSubStrByPattern(svnPathRaw, "(?<=<url>).*?(?=</url>)", 0, "");
    getLog().info("SVN Path: " + svnPath);
    
    //version = Paths.get("").toAbsolutePath().getParent().getFileName().toString();
    version = PluginUtil.extractSubStrByPattern(svnPath, "/Tag/([\\d]+([\\.][\\d]+)*((-|_)[\\w]*)?)", 1, "");
    getLog().info("App Version: " + version);
  }
  

  private void extractDeploymentPathComponents() {
    domain = PluginUtil.extractSubStrByPattern(deploymentPath, "^(?:https?://)?([^:/]+)", 0, "http://localhost");
    //serverName = PluginUtil.extractSubStrByPattern(deploymentPath, "^(?:https?://)?([^:/]+)", 1, "localhost");
    port = PluginUtil.extractSubStrByPattern(deploymentPath, "(?<=:)[0-9]{2,5}", 0, "80");
  }

  private void checkingTomcatAvailability() throws MojoExecutionException {
    getLog().info("Checking Tomcat availability...");
    String lineEnd = System.getProperty("line.separator");
    try {
      String auth = username + ":" + password;
      String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
      URL url = new URL(domain + ":" + port + "/manager/html");
      HttpURLConnection tomcatConnection = (HttpURLConnection) url.openConnection();
      tomcatConnection.setRequestProperty("Authorization", authHeaderValue);
      int responseCode = tomcatConnection.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new MojoExecutionException("HTTP Status Code: " + responseCode +
          (!PluginUtil.isEmpty(tomcatConnection.getResponseMessage()) ? lineEnd + "Response message: " + tomcatConnection.getResponseMessage() : ""));
      }
      tomcatConnection.disconnect();
      getLog().info("HTTP Status Code: " + responseCode);
    } catch (IOException | MojoExecutionException e) {
        throw new MojoExecutionException(
          MessageFormat.format("Tomcat is not available on {0}:{1} OR incorrect LOGIN/PASSWORD!"+lineEnd+"{2}", domain, port, e.getMessage()), e);
    } catch (Exception e) {
        throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void checkingModuleInfoAvailability() throws MojoExecutionException {
    getLog().info("Checking app ModuleInfo availability...");
    String lineEnd = System.getProperty("line.separator");
    try {
      URL url = new URL(domain + ":" + port + "/ModuleInfo/versionServlet");
      HttpURLConnection moduleInfoConnection = (HttpURLConnection) url.openConnection();
      int responseCode = moduleInfoConnection.getResponseCode();
      if (responseCode != 400) {
        throw new MojoExecutionException("HTTP Status Code: " + responseCode +
          (!PluginUtil.isEmpty(moduleInfoConnection.getResponseMessage()) ? lineEnd + "Response message: " + moduleInfoConnection.getResponseMessage() : ""));
      }
      moduleInfoConnection.disconnect();
      getLog().info("HTTP Status Code: " + responseCode);
    } catch (IOException | MojoExecutionException e) {
        throw new MojoExecutionException(
          MessageFormat.format("App ModuleInfo is not available on {0}:{1}, please install it and run."+lineEnd+"{2}", domain, port, e.getMessage()), e);
    } catch (Exception e) {
        throw new MojoExecutionException(e.getMessage(), e);
    }
  }

}
