# tool-maven-plugin-appinstall
Maven plugin for application deployment to Tomcat with saving installation information (through application "ModuleInfo")

# Особенности на текущий момент:
- Работает с приложениями из `SVN` репозитория. Предполагается классическая структура проекта приложения (разнесение содержимого по подпапкам `App`, `DB`, `Doc` и т.п.).
- Путь локальной папки с приложением должен содержать версию приложения в предпоследнем элементе пути. Например:
```
c:\work\workspace\JepRiaShowcase\Tag\10.11.0\App\
```
- Подпапка `Doc` проекта должна содержать файл `map.xml` с описанием `DB` структуры. Например:
```
c:\work\workspace\JepRiaShowcase\Tag\10.11.0\Doc\map.xml
```
- Предварительно на целевом экземпляре `Tomcat` необходимо развернуть приложение `ModuleInfo`.

# Использование (тестовое):
- Разместить в подпапке `App` вспомогательный `pom.xml` с настройками плагина `tool-maven-plugin-appinstall`. Примерное содержание:
```
<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>org.jepria.jepriashowcase</groupId>
  <artifactId>JepRiaShowcase</artifactId>
  <version>12.0.0</version>
  <packaging>pom</packaging>

  <name>JepRiaShowcase</name>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
    <project.build.finalName>JepRiaShowcase</project.build.finalName>
  </properties>
  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <artifactId>maven-clean-plugin</artifactId>
          <version>3.1.0</version>
        </plugin>
        <plugin>
          <artifactId>maven-resources-plugin</artifactId>
          <version>3.1.0</version>
        </plugin>
        <plugin>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>3.8.0</version>
        </plugin>
        <plugin>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>2.22.1</version>
        </plugin>
        <plugin>
          <artifactId>maven-jar-plugin</artifactId>
          <version>3.0.2</version>
        </plugin>
        <plugin>
          <artifactId>maven-install-plugin</artifactId>
          <version>2.5.2</version>
        </plugin>
        <plugin>
          <artifactId>maven-deploy-plugin</artifactId>
          <version>2.8.2</version>
        </plugin>
        <plugin>
          <artifactId>maven-site-plugin</artifactId>
          <version>3.7.1</version>
        </plugin>
        <plugin>
          <artifactId>maven-project-info-reports-plugin</artifactId>
          <version>3.0.0</version>
        </plugin>
      </plugins>
    </pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.jepria.tools.mavenplugin</groupId>
        <artifactId>appinstall-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <inherited>false</inherited>
        <configuration>
          <testMessage>Test</testMessage>
          <deploymentPath>http://localhost:8080/manager/text</deploymentPath>
          <contextPath>/JepRiaShowcase</contextPath>
          <username>username</username>
          <password>password</password>
          <loadOperatorId>nagornyys/123</loadOperatorId>
          <warFile>${project.basedir}/lib/JepRiaShowcase.war</warFile>
          <skipSaveInstallInfo>false</skipSaveInstallInfo>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```
- Воспользоваться командой `mvn appinstall:tomcat.deploy` для установки приложения.
- Воспользоваться командой `mvn appinstall:tomcat.undeploy` для удаления приложения.
