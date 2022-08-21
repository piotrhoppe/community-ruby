# Community Ruby

The Community Ruby is a plugin for NetBeans that provides an integrated 
development environment for building, running, testing, and debugging Ruby and 
Ruby on Rails applications.  
Is based on the original [(c) Oracle Corporation plugin](http://hg.netbeans.org/community-ruby)
which has been donated to the [Apache NetBeans](https://netbeans.org) part of
[Apache Software Fundation](https://www.apache.org).

## Plugin features
* Editing
* Refactoring
* Projects
* Ruby on Rails
  * support version 2.3.x
* Debugging
* Unit Testing
* Code Coverage Support
* Rake Runner
* Live Code Templates
* Quickfixes and Hints
* Keyboard Shortcuts
* Additional Plugins
* Ruby Gems Manager
* Ruby Options
* Integrated JRuby

## Requirements
- NetBeans >= 14.0

## Documentation
* [Old Ruby Plugin Wiki](https://web.archive.org/web/20111030214144/http://wiki.netbeans.org/Ruby)

## Installation
### Community Ruby plugin
- go to [releases](https://github.com/piotrhoppe/community-ruby/releases) and
download latest release.
- unpack downloaded file
- run NetBeans and go to "Tools|Plugins > Downloaded" tab
- click on "Add Plugins..." button and go to the directory where you put the 
unpacked files and select all *.nbm files and click on the "Open" button
- when all plugins are selected click on the "Install" button and next follow
the instructions


> [!NOTE]  
> **Original Ruby plugin**  
> For versions prior to NetBeans 14.0 you can use the old original plugin from
the (c) Oracle Corporation. It is still available, please follow below steps:
> - download plugin from [https://web.archive.org](https://web.archive.org/web/20180505141239/http://plugins.netbeans.org/plugin/38549/ruby-and-rails)
> - unpack downloaded zip file to new directory
> - run NetBeans and go to "Tools|Plugins > Downloaded" tab
> - click on "Add Plugins..." button and go to the directory where you put the 
> unpacked files and select all *.nbm files and click on the "Open" button
> - click again on the "Add Plugins..." button and change "Files of Type:" into 
"OSGI Bundle files (*.jar)" next select the "org-jruby-jruby.jar" file and click 
on the "Open" button
> - when all plugins are selected click on the "Install" button and next follow
the instructions

## Limitations
* delivered the JRuby with plugin works only with java 1.8.x
* delivered parser with plugin suports Ruby in version 2.3.x or olders, but can
also work with newer versions although in some cases can show wrong hints

## Licence
[Apache license 2.0](LICENSE)
