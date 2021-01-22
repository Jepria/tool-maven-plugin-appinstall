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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Base64;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Goal which deploys war-archive to Tomcat.
 *
 */
@Mojo( name = "tomcat.undeploy", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.TEST )
public class TomcatUndeployMojo extends AbstractMojo
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
  private String domain, port;

  public void execute() throws MojoExecutionException
  {
    getLog().info("Application deletion started...");
    extractDeploymentPathComponents();
    checkingTomcatAvailability();
    executeMojo(
      plugin(
        groupId("org.apache.tomcat.maven"),
        artifactId("tomcat7-maven-plugin"),
        version("2.2")
      ),
      goal("undeploy"),
      configuration(
        element(name("ignorePackaging"), "true"),
        element(name("url"), deploymentPath),
        element(name("username"), username),
        element(name("password"), password),
        element(name("path"), contextPath)
      ),
      executionEnvironment(
        mavenProject,
        mavenSession,
        pluginManager
      )
    );
  }

  private void extractDeploymentPathComponents() {
    domain = PluginUtil.extractSubStrByPattern(deploymentPath, "^(?:https?://)?([^:/]+)", 0, "http://localhost");
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

}
