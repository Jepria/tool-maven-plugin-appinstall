package org.jepria.tools.mavenplugin.appinstall.version;

import static org.jepria.tools.mavenplugin.appinstall.util.PluginUtil.checkParameter;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;

import org.apache.maven.plugin.MojoExecutionException;

//Ввиду нехватки времени код позаимствован из JepRiaToolkit.
//К сожалению, не удалось задействовать в исходном виде, так как оригинал завязан на ant таски.
public class FinishAppInstall extends AppInstall {

  // атрибуты таска
  private String statusCode, errorMessage;

  /**
   * {@inheritDoc}
   * @throws UnsupportedEncodingException 
   */
  @Override
  public URL prepareUrl() throws MalformedURLException, UnsupportedEncodingException, MojoExecutionException {

    checkParameter(appInstallResultId, "Incorrect argument: appInstallResultId!");

    return new URL(MessageFormat.format(
        "{0}?action=finishAppInstall&appInstallResultId={1}&statusCode={2}&errorMessage={3}&login={4}&password={5}",
        getVersionServletUrl(),
        appInstallResultId,
        statusCode,
        URLEncoder.encode(errorMessage, "UTF-8"),
        getEncodedLogin(),
        getEncodedPassword()));
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}