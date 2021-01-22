package org.jepria.tools.mavenplugin.appinstall.version;

import static org.jepria.tools.mavenplugin.appinstall.util.PluginConstant.HTTP_PROTOCOL;
import static org.jepria.tools.mavenplugin.appinstall.util.PluginConstant.PATH_SEPARATOR;
import static org.jepria.tools.mavenplugin.appinstall.util.PluginUtil.checkParameter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.jepria.tools.mavenplugin.appinstall.util.PluginUtil;

// Ввиду нехватки времени код позаимствован из JepRiaToolkit.
// К сожалению, не удалось задействовать в исходном виде, так как оригинал завязан на ant таски.
public abstract class AppInstall {

  // атрибуты таска
  protected String port, deploymentPath, loadOperatorId, skipSaveInstallInfo, appInstallResultId;

  /**
   * Значение флага, означающего пропустить установку.
   */
  private final static String SKIP_SAVE_INSTALL_INFO = "1";
  
  /**
   * VesionServlet url <strong>без</strong> GET.
   */
  private String versionServletUrl;
  
  /**
   * Возвращает VesionServlet url <strong>без</strong> GET.
   * @return the versionServletUrl
   */
  protected String getVersionServletUrl() {
    return versionServletUrl;
  }

  /**
   * Устанавливает VesionServlet url.
   * @param versionServletUrl the versionServletUrl to set
   */
  private void setVersionServletUrl(String versionServletUrl) {
    this.versionServletUrl = versionServletUrl;
  }

  /**
   * Закодированный логин.
   */
  private String encodedLogin;
  
  /**
   * Получает закодированный логин.
   * @return Закодированный логин.
   */
  protected String getEncodedLogin() {
    return encodedLogin;
  }

  /**
   * Устанавливает закодированный логин.
   * @param encodedLogin Закодированный логин.
   */
  private void setLogin(String encodedLogin) {
    this.encodedLogin = encodedLogin;
  }
  
  /**
   * Закодированный пароль.
   */
  private String encodedPassword;

  /**
   * Получает закодированный пароль.
   * @return Закодированный пароль.
   */
  protected String getEncodedPassword() {
    return encodedPassword;
  }

  /**
   * Устанавливает закодированный пароль.
   * @param encodedPassword Закодированный пароль.
   */
  protected void setEncodedPassword(String encodedPassword) {
    this.encodedPassword = encodedPassword;
  }

  /**
   * Подготавливает URL с GET-параметрами для вызова VersionServlet. <br/>
   * Проверяет входные данные.
   * 
   * @return URL
   * @throws MojoExecutionException 
   * @throws MalformedURLException 
   * @throws UnsupportedEncodingException 
   */
  abstract protected URL prepareUrl() throws IOException, MojoExecutionException;
  
  /**
   * Callback-функция. Вызывается после окончания работы VersionServlet.
   * @param versionServletConnection
   */
  protected void afterVersionServletCall(HttpURLConnection versionServletConnection) {};

  /**
   * Основной метод. Вызывает VersionServlet
   */
  public void callVersionServlet() throws MojoExecutionException {

    // если параметр skipSaveInstallInfo принимает значение 1, то версию не пишем
    if (SKIP_SAVE_INSTALL_INFO.equals(skipSaveInstallInfo)) {
      return;
    }

    checkParameter(deploymentPath, "Incorrect parameter: DEPLOYMENT_PATH!");
    checkParameter(loadOperatorId, "Incorrect parameter: LOAD_OPERATORID!");

    try {
      
      loadOperatorId = loadOperatorId.replaceAll("\\\\", PATH_SEPARATOR);
      String[] loginAndPass = loadOperatorId.split(PATH_SEPARATOR);
      setLogin(PluginUtil.encode(loginAndPass[0]));
  
      // если пароль не указан через / используем в качестве него - логин
      String password = loginAndPass.length > 1 
          ? loadOperatorId.substring(loadOperatorId.indexOf(PATH_SEPARATOR) + 1) : loginAndPass[0];
      setEncodedPassword(PluginUtil.encode(password));
      
      String serverName = getServerName(deploymentPath);
      setVersionServletUrl(
          PluginUtil.prepareServerUrl(HTTP_PROTOCOL, serverName, port) + "/ModuleInfo/versionServlet");
      
      HttpURLConnection versionServletConnection = getVersionServletConnection(prepareUrl());
      int responseCode = versionServletConnection.getResponseCode();
      
      if (responseCode != HttpURLConnection.HTTP_OK) {
        
        String lineEnd = ". " + System.getProperty("line.separator");
        throw new MojoExecutionException("VersionServlet HTTP Status Code: " + responseCode + lineEnd + 
            "Response message: " + versionServletConnection.getResponseMessage() + lineEnd +
            "Check DEPLOYMENT_PATH & PORT parameters!");
      }
      
      afterVersionServletCall(versionServletConnection);
      versionServletConnection.disconnect();
      
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Connection refused. Check DEPLOYMENT_PATH & PORT parameters! Details: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  
  /**
   * Обращение к сервлету версионности
   * 
   * @return соединение с сервлетом
   * @throws Exception
   */
  private HttpURLConnection getVersionServletConnection(URL url) throws IOException {
    if (url == null) 
      return null;
    
    System.setProperty("java.net.useSystemProxies", "true");
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
    urlConnection.connect();
    
    return urlConnection;
  }
  
  /**
   * Получение имени сервера из DEPLOYMENT_PATH <br/>
   * TODO: use named-capturing group in regex after jdk > 6.
   * 
   * @param deployPath Путь развертывания приложения
   * @return имя сервера
   * @throws IllegalArgumentException
   */
  private String getServerName(String deployPath) {
    Pattern p = Pattern.compile("^https?://([^:]+):");
    Matcher m = p.matcher(deployPath);
    if(m.find() == false) {
      throw new IllegalArgumentException("Invalid DEPLOYMENT_PATH.");
    }
    return m.group(1);
  }

  public void setPort(String port){
    this.port = port;
  }

  public void setDeploymentPath(String deploymentPath) {
    this.deploymentPath = deploymentPath;
  }

  public void setLoadOperatorId(String loadOperatorId) {
    this.loadOperatorId = loadOperatorId;
  }
  
  public void setSkipSaveInstallInfo(String skipSaveInstallInfo) {
    this.skipSaveInstallInfo = skipSaveInstallInfo;
  }
  
  public void setAppInstallResultId(String appInstallResultId) {
    this.appInstallResultId = appInstallResultId;
  }

  public String getAppInstallResultId() {
    return appInstallResultId;
  }

}