package org.jepria.tools.mavenplugin.appinstall.version;

import static org.jepria.tools.mavenplugin.appinstall.util.PluginConstant.END_OF_LINE;
import static org.jepria.tools.mavenplugin.appinstall.util.PluginConstant.TAB;
import static org.jepria.tools.mavenplugin.appinstall.util.PluginUtil.checkParameter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.jepria.tools.mavenplugin.appinstall.util.PluginUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

//Ввиду нехватки времени код позаимствован из JepRiaToolkit.
//К сожалению, не удалось задействовать в исходном виде, так как оригинал завязан на ant таски.
public class StartAppInstall extends AppInstall {

  // атрибуты таска
  private String version, svnPath, svnVersionInfo, mapXmlPath;

  /**
   * {@inheritDoc}
   * @throws MalformedURLException
   */
  @Override
  public URL prepareUrl() throws MalformedURLException, MojoExecutionException {

    checkParameter(version, "Incorrect parameter: VERSION!");
    checkParameter(svnPath, "Incorrect argument: svnPath!");
    checkParameter(svnVersionInfo, "Incorrect argument: svnVersionInfo!");

    String errorMsg = "Error parsing map.xml!";
    String lineEnd = System.getProperty("line.separator");
    
    MapXml mapXml = null;
    String installVersion;

    try { //TODO: use Multiple Exception Types in catch after jdk > 6
      mapXml = parseMapXml();
      installVersion = getInstallVersion(version); //устанавливаемая версия модуля, взятая из пути модуля
    } catch (MojoExecutionException e) {
      throw new MojoExecutionException(e.getMessage() + lineEnd + errorMsg, e);
    } catch (ParserConfigurationException e) {
      throw new MojoExecutionException(errorMsg, e);
    } catch (SAXException e) {
      throw new MojoExecutionException(errorMsg, e);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException(
        PluginUtil.multipleConcat("Invalid parameter 'Version' = ", version, "!") + lineEnd + "Error parsing installVersion.", e);
    }

    return new URL(MessageFormat.format("{0}?action=startAppInstall&svnRoot={1}&initPath={2}"
        + "&modVersion={3}&instVersion={4}&deployPath={5}&svnPath={6}&svnVersionInfo={7}"
        + "&login={8}&password={9}",
        getVersionServletUrl(),
        mapXml.getSvnRoot(),
        mapXml.getInitialPath(),
        mapXml.getModuleVersion(),
        installVersion,
        deploymentPath,
        svnPath,
        svnVersionInfo,
        getEncodedLogin(),
        getEncodedPassword()));
  }

  /**
   * Получение служебной информации из map.xml <br/>
   * TODO: Необходим рефакторинг, - обработка исключений (переменная stage), перенести функцию в класс MapXml.
   * @return Описание map.xml
   * @throws ParserConfigurationException
   * @throws SAXException
   * @throws IOException
   */
  private MapXml parseMapXml() throws ParserConfigurationException, SAXException, MojoExecutionException {

    Document mapXmlDocument = null;
    try{
      mapXmlDocument = PluginUtil.getDOM(PluginUtil.isEmpty(mapXmlPath) ? "../Doc/map.xml" : mapXmlPath);
    } catch(IOException io) {
      throw new MojoExecutionException(
        PluginUtil.multipleConcat("Error parsing map.xml : ", END_OF_LINE, TAB, io.getMessage()), io);
    }

    Integer stage = 1;
    String svnRoot = null;
    String initPath = null;
    String moduleVersion = null;
    MapXml mapXml = null;
    try {
      XPathFactory factory = XPathFactory.newInstance();
      XPath xpath = factory.newXPath();

      XPathExpression expr = xpath.compile("//map/version/text()");
      //версия модуля, взятая из map.xml
      moduleVersion = (String) expr.evaluate(mapXmlDocument, XPathConstants.STRING);
      stage++;

      xpath.reset();
      expr = xpath.compile("//map/path/text()");
      //корень модуля, взятый из map.xml
      svnRoot = ((Node) expr.evaluate(mapXmlDocument, XPathConstants.NODE)).getTextContent();
      stage++;

      xpath.reset();
      expr = xpath.compile("//map/initialPath/text()");
      //начальный путь модуля, взятый из map.xml
      initPath = ((Node) expr.evaluate(mapXmlDocument, XPathConstants.NODE)).getTextContent();
      stage++;

      if (PluginUtil.isEmpty(moduleVersion)){
        throw new MojoExecutionException("Invalid parameter 'Module Version', check map.xml!");
      }

      mapXml = new MapXml(svnRoot, initPath, moduleVersion);

    } catch (MojoExecutionException e) {
      throw e;
    } catch(Exception e) {
      String errorMessageName = new String();
      switch(stage) {
        case 2 : errorMessageName = PluginUtil.multipleConcat("Error getting attribute 'svnRoot' (value - ", svnRoot, ") during parsing map.xml. Please check this file!"); break;
        case 3 : errorMessageName = PluginUtil.multipleConcat("Error getting attribute 'initialPath' (value - ", initPath, ") during parsing map.xml. Please check this file!"); break;
        case 1 :
        default : errorMessageName = PluginUtil.multipleConcat("Error getting attribute 'moduleVersion' (value - ", moduleVersion, ") during parsing map.xml. Please check this file!"); break;
      }
      throw new MojoExecutionException(errorMessageName, e);
    }

    return mapXml;
  }

  /**
   * Валидация версии
   *
   * @param version  проверяемая версия
   * @return флаг валидности
   * @throws IllegalArgumentException
   */
  private boolean checkVersion(String version) {
    Pattern pattern = Pattern.compile("^[\\d]+([\\.][\\d]+)*(_[\\w]*)?$");
    boolean flag = !PluginUtil.isEmpty(version) && pattern.matcher(version).matches();
    if (!flag) {
      throw new IllegalArgumentException("Not correct version (value="+version+")");
    }
    return flag;
  }

  /**
   * Получение версии из строки.
   *
   * @param version Строка.
   * @return Версия в формате цифр, разделенных точками.
   * @throws IllegalArgumentException
   */
  private String getInstallVersion(String version) {
    if (PluginUtil.isEmpty(version)) {
      return null;
    } else {
      if (version.contains("\\"))
        version = version.split("\\")[1];
      if (checkVersion(version))
        return version;
    }
    return null;
  }

  public void setVersion(String version){
    this.version = version;
  }

  public void setSvnVersionInfo(String svnVersionInfo) {
    this.svnVersionInfo = svnVersionInfo;
  }

  public void setSvnPath(String svnPath) {
    this.svnPath = svnPath;
  }
  
  public void setMapXmlPath(String mapXmlPath) {
    this.mapXmlPath = mapXmlPath;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void afterVersionServletCall(HttpURLConnection versionServletConnection) {
    String resultId = versionServletConnection.getHeaderField("appInstallResultId");
    setAppInstallResultId(resultId);
  }
}