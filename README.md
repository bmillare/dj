# dj 0.1.x

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

1. Download wget --no-check-certificate 'http://github.com/bmillare/dj/raw/master/bin/install.sh'
2. Put somewhere in path, 'chmod +x install.sh' to make executable and run
3. Symlink the executable dj/bin/run-dj-tool.sh to your path

# Usage

## Getting Help

For help,

    $ dj

or for help about a command

    $ dj help foo-command

## Set both classpath and obtain dependencies lazily

We can create a repl anytime and anywhere using:

    $ dj repl
    Clojure 1.3.0
    ;user=>

Let's add a simple hello.clj file:

    $ echo '(println "hello world")' > hello.clj
    
Let's add the classpath to the current directory and see if we can
require it:

```clojure
;user=> (dj.classloader/add-to-classpath! "/home/user/")
#<File /home/user>
;user=> (require '[hello])
hello world
nil
```

Let's load some dependencies, how about incanter?:

```clojure
;user=> (dj.classloader/add-dependencies! '[[incanter/incanter "1.3.0-SNAPSHOT"]])
resolving #dj.deps.maven.maven-dependency{.....
...
nil
;user=> (require '[incanter.core])
nil
```

## Starting a new project in the dj source repository

Lets make a hello world like project using dj:

    $ dj new hw

Notice that a directory named hello-world was created in ~/dj/usr/src/

In dj, the location of the projects sources are managed by dj, not the
user

    $ ls ~/dj/usr/src/hw

project.clj src

Edit the project.clj file to depend on a clojure contrib project on github

project.clj:

```clojure
(defproject hw "1.0.0" :src-dependencies ["clojure/core.logic"])
```

In the src/hw/ directory, create a clj file:

src/hw/core.clj:

```clojure
(ns hw.core (:use [clojure.core.logic.minikanren]))

(defn hello-world [] (print "hello world"))
(defrel man x)
```

## Advanced REPL

If we want the repl to include our project, we append the project name

    $ dj repl hw

then we can try it out:

```clojure
;user=> (require 'hw.core)
nil
;user=> (hw.core/hello-world)
hello worldnil
```

If you check ~/dj/usr/

    $ ls ~/dj/usr/
    bin maven src

There is a maven repository there. dj automatically downloaded the
dependencies for hw in the local repository and constructed the
classpath that includes the dependency on startup of hw.

Notice that we can run

    $ dj repl hw

anywhere we want.

    $ cd /
    $ dj repl hw

again, dj manages finding the project and setting the classpath for
you, similar to the way a *nix environment would know how to run the
java command for you. Note, dj/usr/src/clojure/* are for contrib
projects

You can organize dj/usr/src/ as a repository with subdirectories. To
run a project, give the path relative to the dj/usr/src directory.

So if we moved the hw project into the org folder,

    $ cd ~/dj/usr/src/
    $ mkdir org
    $ mv hw org

we would start the hw project repl using

    $ dj repl org/hw

# Developing with emacs, inferior-lisp
in the .emacs file, add:

```elisp
(setq inferior-lisp-program "~/dj/bin/run-dj-tool.sh repl")
```

Note: Also, I've included a rlwrap'd version of run-dj-tool.sh, so if rlwrap
is installed on your system, link to ~/dj/bin/run-dj-tool-rlwrap.sh
instead.

Optional: If you are using rlwrap, generate the completions.

    $ dj rlwrap make-completions

# Features

* Few dependencies, only needs clojure and java.

* Supports source dependencies, you can have your project depend on
  other projects you are developing.

* Supports clojure contrib git cloning as source dependencies

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

# Design

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

* Currently only uses 1.3.0

* Does not support starting different REPL versions

* Currently no support for building jars

* Limited support for all of leiningen's project.clj syntax

* Limited support for all of maven's configuration in pom.xml files

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

What seems to be the most significant factor in this case, however, is
the inability to dynamically change the native dependencies during
runtime without affecting multiple clojure instances. There is a lack
of scoping native dependencies at the jvm level.

# File Directory Structure

* The design of the directory hierarchy is based of the design of linux's
 specifically, gentoo's

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

# License

Distributed under the Eclipse Public License, the same as Clojure.