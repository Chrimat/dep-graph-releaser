[![Download](https://api.bintray.com/packages/loewenfels/oss/dep-graph-releaser/images/download.svg) ](https://bintray.com/loewenfels/oss/dep-graph-releaser/_latestVersion)
[![EUPL](https://img.shields.io/badge/license-EUPL%201.2-brightgreen.svg)](https://joinup.ec.europa.eu/collection/eupl/eupl-text-11-12)
[![Build Status Travis](https://travis-ci.org/loewenfels/dep-graph-releaser.svg?tag=v1.2.0)](https://travis-ci.org/loewenfels/dep-graph-releaser/branches)
[![Build status AppVeyor](https://ci.appveyor.com/api/projects/status/2cw4dnwkmlpb7fnn/branch/master?svg=true)](https://ci.appveyor.com/project/loewenfels/dep-graph-releaser/branch/master)
[![Coverage](https://codecov.io/github/loewenfels/dep-graph-releaser/coverage.svg?tag=v1.2.0)](https://codecov.io/github/loewenfels/dep-graph-releaser?tag=v1.2.0)
[![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=depgraph&metric=alert_status)](https://sonarcloud.io/dashboard?id=depgraph)


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

Have a look at the [Wiki](https://github.com/loewenfels/dep-graph-releaser/wiki) 
for more information and try out the [online example](https://loewenfels.github.io/dep-graph-releaser/#./release.json).

# Key Features
- Release pipeline based on dependencies (no need to adopt your pipeline if a dependency changes)
- Dry Run included
- The flexible process design lets you use Dependent Graph Releaser as supportive tool which does not constrict you.
- Interact with the process to fix failing releases before the release process as a whole has ended.
- Integration of multiple jenkins instances possible (e.g. eases the migration from Jenkins 1.x to 2.x)
   
# Design Decisions   
- In case a dependency has specified `${project.version}` as `<version>` then it will be replaced with the new version.
   
# Known Limitations

Outlined in the Wiki page [Known Limitations](https://github.com/loewenfels/dep-graph-releaser/wiki/Known-Limitations).

# License
Dependent Graph Releaser is published under [EUPL 1.2](https://joinup.ec.europa.eu/collection/eupl/eupl-text-11-12).

# Contributors

We thank the following contributors for their support (inspired by [all-contributors](https://github.com/kentcdodds/all-contributors#emoji-key)).

| [<img src="https://avatars0.githubusercontent.com/u/5557885?s=460&v=4" width="100px;"/><br /><sub><b>Robert Stoll</b></sub>](https://tutteli.ch)<br/> [💻](#code "Code") [🤔](#ideas "Ideas & Planing") [💬](#questions "Answering Questions") [👀](#review "Review Pull Requests")<br/> [📖](#doc "Documentation") [🚇](#infrastructure "Infrastructure") [🔧](#tools "Tools") [🐛](#bug "Bug Reports") | [<img src="https://avatars1.githubusercontent.com/u/39348110?s=460&v=4" width="100px;"/><br /><sub><b>Adrian Scherer</b></sub>](https://github.com/schereradi)<br/> [💻](#code "Code") | [<img src="https://avatars1.githubusercontent.com/u/22729797?s=460&v=4" width="100px;"/><br /><sub><b>Mario Eberl</b></sub>](https://github.com/hubmop)<br/> [💻](#code "Code") [🤔](#ideas "Ideas & Planing") | [<img src="https://avatars3.githubusercontent.com/u/14939597?s=460&v=4" width="100px"/><br /><sub><b>Chrimat</b></sub>](https://github.com/Chrimat)<br/> [💻](#code "Code") [🐛](#bug "Bug Reports") | [<img src="https://avatars2.githubusercontent.com/u/5530915?s=460&v=4" width="100px"/><br /><sub><b>Edimasta</b></sub>](https://github.com/Edimasta)<br/> [💻](#code "Code") | [<img src="https://avatars0.githubusercontent.com/u/37987805?s=460&v=4" width="100px"/><br /><sub><b>kayth1</b></sub>](https://github.com/kayth1)<br/> [🐛](#bug "Bug Reports") |
| :---: | :---: | :---: | :---: | :---: | :---: |
| [<img src="https://avatars3.githubusercontent.com/u/36969748?s=460&v=4" width="100px;"/><br /><sub><b>Wladimir Babitzki</b></sub>](https://github.com/wbabitzki)<br/> [🐛](#bug "Bug Reports") | [<img src="https://avatars0.githubusercontent.com/u/20090741?s=460&v=4" width="100px;"/><br /><sub><b>Simon</b></sub>](https://github.com/sniederb)<br/> [🐛](#bug "Bug Reports") | [<img src="https://avatars2.githubusercontent.com/u/2034531?s=460&v=4" width="100px;"/><br /><sub><b>Adi König</b></sub>](https://github.com/Anchialas)<br/> [🐛](#bug "Bug Reports") [🤔](#ideas "Ideas & Planing") |