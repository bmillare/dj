# dj 2.0alpha

## Motivation

Dj takes the cacaphony of java, git, clojure, clojurescript and build tools and mixes it into something harmonious; a clojure distribution. Clojure is a dynamic language: shouldn't managing our projects be too? Dj supports the goal of never having to close your REPL.

### In depth

Again, the purpose of dj is to provide a more **dynamic** and **integrated** development environment. As a start, dj accomplishes this by defining a simple directory structure for managing your clojure projects: `dj/usr/src`, and `dj/tmp`. Having a standard layout enables dj and the developer to have some common ground for installing and managing projects. As you create new projects or clone projects in `dj/usr/src` you can then load these projects into your already running REPL by using `dj.dependencies/resolve-project`: dj will automatically install projects via git (if necessary), load dependencies, and set classpaths, as specified in the project's `project.clj` file. (Under the hood, `cemerick.pomegranate` is being used.)

If during development you want your projects to depend on each other and have dj recursively resolve the projects, you can specify source dependencies with the `:dj/dependencies` key in the project's `project.clj` file. Under the hood, dj implements its own `project.clj` parser and delegates to leiningen as necessary. (Note: In the future, this may better implemented as a leiningen plugin).

Dj also provides convenience utilities for clojurescript, and datomic. For clojurescript, dj makes it easy to depend on the development version. There are also utilities for starting and stopping a browser environment for the browser connected cljs REPL. Dj supports the model used by [tools.namespace](https://github.com/clojure/tools.namespace), and defines a protocol `dj.repl/Lifecycle` that is used to start and stop systems. For datomic, there is a utility to install datomic into the local maven repository from the zip file. (Under the hood, `cemerick.pomegranate.aether/install` is being used).

Dj uses jgit under the hood. This is what dj uses to install outside source projects. Also, this means, dj can update itself.

## Installation Notes

1: Install [leiningen](https://github.com/technomancy/leiningen) by downloading and running the script

Note: This means download the `lein` script and run `lein self-install`. Windows users will also have to download wget (as explained on leinigen's page). It is optional to install git but highly recommended. Windows users can use github supported app.

2: Install dj by cloning it in git.

If you didn't install git, you can download and extract an old dj snapshot in the downloads section, and use `(dj.git/pull dj/system-root)` to update to the most recent version.

3: (Recommended) Create a launching script that cd's into the dj's directory and runs lein repl, this allows you to run dj anywhere.

   Something simple like:
   `~/bin/dj-repl`

```sh
#!/bin/sh
cd ~/dj/
lein repl
```

**NOTE:** For a detailed walkthrough for new clojure users, see [walkthrough](https://github.com/bmillare/dj/wiki/Walkthrough)

Also, do **check out** the [wiki](https://github.com/bmillare/dj/wiki) for more information.

## Basic Usage

 * Install by cloning. Depends on leiningen version `>=2.0preview10`

 * `cd` into the dj directory and run `lein repl`

 * Define your projects (with the project.clj file) in `dj/usr/src`. So something like `dj/usr/src/com.foo`

 * You can use leiningen to create a new project. `lein new fooproject --to-dir usr/src/fooproject`

 * `(dj.dependencies/resolve-project "project-name")` to dynamically load a project. It will recursively resolve the project's dependencies.

 * If you want to depend on other projects add `:dj/dependencies ["foo" "bar"]` where `"foo"` and `"bar"` are projects in `dj/usr/src`.

 * In addition, instead of project names, you can use git urls. Like `"git://github.com/bmillare/dj.peg"`

## Useful namespaces

 * dj: core-like utility functions, check out `system-root` for relative paths

 * dj.io: file utils, see `file`, `poop`, and `eat`

 * dj.classloader: can reset native paths in runtime `reset-native-paths!`, grab resources as strings in the classpath `resource-as-str`, and reload class files (betcha didn't know you can do that) `reload-class-file`

 * dj.dependencies: `resolve-project`

 * dj.cljs: clojurescript utilities, see `cljs-repl`

 * dj.git: basic operations are supported, not a robust replacement to cgit, but useful none the less.

## Version

 * dj is listed as alpha software. It is highly experimental and changes on a whim. Stability will come naturally in time.

## Discussion

 * Profiles were found not necessary for native dependencies because of native-path handling of dj. However, it may be useful if you depend on a newer version of clojure.

## Author

Brent Millare
brent.millare@gmail.com

## License

Copyright (c) Brent Millare. All rights reserved. The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license. You must not remove this notice, or any other, from this software.