# Motivation

"dj takes the cacaphony of java, git, clojure, and build tools and
mixes it into something harmonious."

In a nutshell, dj is an attempt to make a clojure distribution. Like
leiningen and maven, dj resolves dependencies, however, dj tries to be
more like debian's APT or gentoo's portage and provide tools to manage
your projects as if it were a part of a larger ecosystem of
repositories, configuration files, jar files, project sources, and
native dependencies.

# Quick Start

## Standalone script

1. Download wget --no-check-certificate 'http://github.com/bmillare/dj/raw/master/bin/install.sh'
2. Put somewhere in path, 'chmod +x install.sh' to make executable and run

## Manual Install

1. git clone git://github.com/bmillare/dj.git
2. cd dj; mkdir -p usr/src usr/bin etc sbin opt tmp/dj var lib
3. cd opt; wget --no-check-certificate 'http://github.com/downloads/clojure/clojure/clojure-1.2.0.zip'
4. unzip clojure-1.2.0.zip
5. cd ../lib
6. ln -s ../opt/clojure-1.2.0/clojure.jar clojure.jar
7. symlink dj/bin/run-dj-tool.sh to your path
   cd ~/; ln -s dj/bin/run-dj-tool.sh dj

The script assumes you have *nix environment with java, wget, unzip
and git installed.

If you are inclined, you can work through the TUTORIAL to get a feel
for how to install dj with your own version of clojure.

# Feature list

* Practically no dependencies, only needs clojure and java.

* Supports source dependencies, you can have your project depend on
  other projects you are developing.

* Everything is installed locally to a directory, no messing up
  existing configuration files and repositories. If you want, you can
  have multiple dj distributions on the same computer. This should
  simplfy deploying dj onto different systems.

* Classpath is computed during runtime. No forking of the jvm is
  required. No copying or symlinking of jar files.

* Supports native dependencies

* Extracts dependency information from project.clj file just as
  leiningen and cake do

* Easy to extend adding new dependency types or new tasks

# Short Term Todo

* dj repl [maven dependency form]

# Long Term Todo

* git versioning, git dependencies
* bootstrap everything (including java, gcc?) on any computer?

======================================================================

# Old Notes

dj leverages the advantages of having a standard
directory layout for configuration files, jar files, and project
directories by supply tools to integrate them all together.

# Informal Specifications

There should be support for managing dependencies for projects at the
system level. Tools should allow dependencies to be searched,
determined, downloaded to the repository, and removed from the
repository. This is in contrast to leiningen which manages
dependencies on an invididual project basis.

dj inherits certain principles from clojure and utilizes it in its
design. Repositories are dynamic, changing, and differing structures,
but the dependencies are considered immutable. This implies that name
a dependency named foo/bar-1.1.0-snapshot can never change. This is in
contrast to the way leiningen currently handles snapshot
versioning. Dependencies with the same version must be the same in any
repository. Repositories are best thought of as caches and there is a
local repository in the system. Dependencies are resolved lazily in
that poms, the files that contain the dependency information, are
obtained lazily, ie. there is no need to maintain a complete tree of
pom files. The catch is you need an internet connection to ensure pom
files are downloaded as necessary. It is conceivable to sync with
repositories that have many projects to permit offline usage.

Using Maven repositories for rapidly developing projects is difficult,
dj tries to rectify this by creating repositories for live projects
that are managed by versioning software such as git, svn, and dj
itself. There should be tools that support versioning by the commit
hash or svn number while still preserving the live, unversioned
code. Different projects should also be able to access different
versions concurrently.

# Limitations

Currently, loading dependencies dynamically, by adding to the
classpath during runtime, is possible by using clojure's
DynamicClassLoader. The advantage to doing this is there is little
code needed to do this. The problem is that classloader is loaded
during the startup of clojure, which means you cannot reload a new
version of clojure without restarting the jvm. Normally this is not a
problem, however, because you would be restarting your application
anyways and each application is tied to a single JVM
instance. However, this also means that the handling of which clojure
version loads is handled by the bash script instead of clojure
code. There needs to be a way to easily load the correct clojure
version automatically during startup regardless if this is done in
clojure code or in shell scripting. Perhaps integrate a clojure server
that starts new clojure applications where starting a new instance
really just simply connects to that server and displays an
interface. Startup time would decrease dramatically but would require
a lot more work to setup. Nailgun comes to mind however having it
cooperate with clojure may prove difficult.

# File Directory Structure

* The design of the directory hierarchy is based of the design of linux's
 specifically, gentoo's
* combined with chroot, every time you create a directory with these
 subdirectories, you create a new system, thus you can have many
 clojure systems on one computer
* the only way to run concurrently different systems is (under unix)
 to create new bash environments each time

bin/
 -dj binaries and scripts to manage system
src/
 -source code for dj
lib/
 -symlinks of system dependencies 

-the remaining directories are ignored by git (listed in .gitignore)

usr/
 -local (non-system) content
usr/src
 -repository for live projects
usr/bin
 -native dep binaries
usr/maven
 -maven repository
usr/native
 -native repository
etc/
 -system configuration files
sbin/
-this directory is required only if the local operating
 system/distribution does not provide the dependencies
 -contains system dependency binaries, binaries necessary to run clojure
  and dj, this is designed to be minimal
 -pretty much should contain the jdk and git
opt/
 -location of system and native system dependencies that need to persist
 -the install directory for java
 -installation of applications that don't fit the dj model
tmp/
 -contains transient files
tmp/dj
 -workspace for building system and native dependencies
tmp/repository
 -workspace for downloading/resolving dependencies before moving into
  the repository
var/
 -logs, crash reports

# Script descriptions

setup-directories.sh
* create directories
boostrap-system-deps.sh
* download install java
* download, build, install git
* not normal way, not necessary if you can bootstrap system deps from
 the host distribution/OS

# dj concepts

system
* a checkout/snapshot of the dj project with its dependencies
 fulfilled

live dependency
* a dependency on the current state (live) of some project's directory
* should just be a path
* to deal with directory name collisions, append directory with a slot,
 which is just a counter, e.g. leiningen-1, leinigen-2
* dj should be able to wrap generating folder names for you, dj will
 just increment the largest value, names are arbitrary, and a name
 without a counter is considered the 0 directory
* the code you work on in a typical leiningen project is considered
 live dependencies, which have the property that if you change the
 files, reloading the file should change the definition

checkout dependency
* a dependency on project directory with a particular commit
* dj should automatically download project and switch to version/commit

# dj ideas for cli

* update, updates system dependencies
* dj swank, launch a swank server
* dj jar, make a jar of project
* dj install, install in local repository
* dj upload, install in remote repository

# adding tasks

To implement additional tasks, define a file with the same name as the
task in a dj/cli/ directory in the classpath. The namespace doc-string
is the task summary and you must have a main function that accepts
arbitrary arguments. The main function's doc-string is the detailed
documentation for the task. In practice this means adding tasks to the
the src/dj/cli folder.

# Author

Brent Millare
brent.millare@gmail.com