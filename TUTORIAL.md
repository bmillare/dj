Examples of usage:

# Installation

$ wget 'https://github.com/bmillare/dj/blob/master/bin/install.sh'
$ chmod +x install.sh
$ ./install.sh
$ cd ~/bin
$ ln -s ~/dj/bin/run-dj-tool.sh dj

Note: Also, I've included a rlwrap'd version of run-dj-tool.sh, so if rlwrap
is installed on your system, link to ~/dj/bin/run-dj-tool-rlwrap.sh
instead.

Optional: If you are using rlwrap, generate the completions.

$ dj rlwrap make-completions

# Basic Usage

For help,

$ dj

or for help about a command

$ dj help foo-command

Lets make a hello world like project using dj:

$ dj new hw

Notice that a directory named hello-world was created in ~/dj/usr/src/

In dj, the location of the projects sources are managed by dj, not the
user

$ ls ~/dj/usr/src/hw
project.clj src

Edit the project.clj file to depend on a clojure contrib project on github

project.clj:
(defproject hw "1.0.0" :src-dependencies ["clojure/core.logic"])

In the src/hw/ directory, create a clj file:

src/hw/core.clj:
(ns hw.core (:use [clojure.core.logic.minikanren]))

(defn hello-world [] (print "hello world"))
(defrel man x)

We can create a repl at anytime using:

$ dj repl

but, if we want the repl to include our project, we append the project name

$ dj repl hw

then we can try it out:

user=> (require 'hw.core)
nil
user=> (hw.core/hello-world)
hello worldnil

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

(setq inferior-lisp-program "~/dj/bin/run-dj-tool.sh repl")