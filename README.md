[![Download](https://api.bintray.com/packages/loewenfels/oss/dep-graph-releaser/images/download.svg) ](https://bintray.com/loewenfels/oss/dep-graph-releaser/_latestVersion)
[![EUPL](https://img.shields.io/badge/license-EUPL%201.2-brightgreen.svg)](https://joinup.ec.europa.eu/collection/eupl/eupl-text-11-12)
[![Build Status](https://travis-ci.org/loewenfels/dep-graph-releaser.svg?branch=master)](https://travis-ci.org/loewenfels/dep-graph-releaser/branches)
[![Coverage](https://codecov.io/github/loewenfels/dep-graph-releaser/coverage.svg?branch=master)](https://codecov.io/github/loewenfels/dep-graph-releaser?branch=master)

# Dependent Graph Releaser
Dependent Graph Releaser is a tool which helps you with releasing a project and its dependent projects.
Its aim is to simplify the process of using up-to-date internal dependencies.
 
A simple example: having project `A -> B -> C` we want to automate the following process:
- release C
- update the dependency in B
- release B
- update the dependency in A
- release A

It will start of with supporting only maven projects and requires Jenkins integration.

You can also merely use it to get an HTML which represents a pipeline showing you, 
how you would need to release the projects manually. 
It generates kind of a bottom up dependency graph, or in other words a dependents graph. 

# Local usage

## Analyse

Add the projects you want to analyse to the folder `repos` (in the project directory) and run the following gradle command:
````
gr html -PgId=your.group.id -PaId=the.artifact.id
````
This creates a `pipeline.html` in the folder `rootFolder/build/html`. 

Notice, the task is clever and does not regenerate the html if nothing has changed in the code 
(the gradle task is mainly there to ease development, 
using `repos` as input of the task takes too much time depending on the number of projects you have).
Thus, if you add another project to the `repos` folder and want to rerun the task, then call `cleanJson` first. 

You can use `gr server` to start a lightweight local server serving `pipeline.html`. 
This is necessary since the `pipeline.html` wants to include a javascript file and your browser forbids that to protect you from XSS attacks.  

## Release

You can also trigger the release locally as long as the jenkins server is set up to allow CORS accordingly.

See section [Jenkins - Release](#jenkins-release) for how to setup the Jenkins jobs.


# Jenkins

## Set Up
The following guide shows how you can integrate dep-graph-releaser with Jenkins.

1. Get the latest resources at [bintray](https://dl.bintray.com/loewenfels/oss/ch/loewenfels/dep-graph-releaser-runner/)
   1. click on the latest version (or chose the one you want to use)
   2. download the ...-resources.jar
   3. extract it and delete the folder META-INF
2. Connect to your jenkins master (e.g., via ssh)
   1. change to the directory JENKINS_HOME/userContent (typically `cd /home/jenkins/userContent`)
   2. `mkdir dep-graph-releaser`
3. Copy the extracted resources to JENKINS_HOME/userContent/dep-graph-releaser
   1. Visit http(s)://JENKINS_URL/userContent/dep-graph-releaser/pipeline.html
   2. Most probably you will only see `Loading...` and nothing happens. That is due 
      [security measurements](https://wiki.jenkins.io/display/JENKINS/Configuring+Content+Security+Policy), see next step
4. If Loading failed in the previous step (which is the default behaviour) 
   then you need to modify the following system property: `hudson.model.DirectoryBrowserSupport.CSP`
   1. Open up the Jenkins Script Console and run: 
      ```groovy
      System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "sandbox allow-same-origin allow-scripts; script-src 'self'");
      ```
      This will allow that scripts of the same domain (same protocol and port) can be executed.
   2. The `Loading...` should now disappear (force reload if it does not, could also be caching)
   3. Modify the JENKINS_JAVA_OPTIONS in the Jenkins config (typically at `/etc/sysconfig/jenkins`) and add the system property.
      For instance, you could modify it as follows:
      ```
      # Original before modification for dep-graph-releaser
      # JENKINS_JAVA_OPTIONS="-Djava.awt.headless=true -server -Xmx2g"
      #
      # Modified version to allow dep-graph-releaser to execute its javascripts
      JENKINS_JAVA_OPTIONS="-Djava.awt.headless=true -server -Xmx2g -Dhudson.model.DirectoryBrowserSupport.CSP=\"sandbox allow-same-origin allow-scripts; script-src 'self'\""
      ```
5. Create a job which runs the Main with command json
    - an example will follow using maven. 
      In short, you can use the [jenkins.pom](https://github.com/loewenfels/dep-graph-releaser/tree/master/dep-graph-releaser-runner/src/jenkins.pom)
      if you use mvn (to retrieve dependencies).
    - if you want to run Main directly then you can use the [zip](https://dl.bintray.com/loewenfels/oss/ch/loewenfels/dep-graph-releaser-runner/) (chose version and then *.zip)
      containing all necessary libraries as well as a `.bat`

<a name="jenkins-release"></a>  

## Release

Explanation will follow
   
# Design Decisions   
- In case a dependency has specified `${project.version}` as `<version>` then it will be replaced with the new version.
   
# Known Limitations

The project does currently not support (pull requests are more than welcome):
- recovery from ongoing processes. If you navigate away from the pipeline in the middle of the process (or you browser crashes),
  then the state is likely to be wrong if you return to the page.
- exclusions are not yet taken into account. As consequence, dep-graph-releaser might detect cycles where there are not any.
- version managed in a property which itself refers to a property: `<properties><a>${b}</a><b>1.0.0</b></properties>`.
- version which is partly static and partly a property: `<version>1.0.0-${BUILD_NUMBER}</version>`.
- version managed in a property which is defined elsewhere and there it is not used in a dependency.
- dependencies only defined in profiles (not yet tested, might be that it already works).
- simple regex replacement is used to update dependencies: if you have files containing malformed xml in comments, then updating dependencies might be erroneous.
- disabling a submodule does not work with the `-disableRegex` option, you have to disable the multi-module instead
- remote releaser extracts build number from HTML in case the job is queued before we get the chance to retrieve the queued item id 
  -> in such a case the newest run is considered to be the triggered one. 
  This is not necessarily the case. We would have to check parameters to be sure but we omit that at the moment.                

# License
Dependent Graph Releaser is published under [EUPL 1.2](https://joinup.ec.europa.eu/collection/eupl/eupl-text-11-12).
