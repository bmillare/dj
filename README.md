# dj 2.0alpha

## Motivation

"dj takes the cacaphony of java, git, clojure, clojurescript and build tools and mixes it into something harmonious."

In a nutshell, dj is an attempt to make a clojure distribution. To be more accessible, dj now uses leiningen as a base for creating a distribution.

Lots of changes in the new branch, more to come.

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

## Basic Usage

 * Install by cloning. Depends on leiningen version `>=2.0preview10`

 * `cd` into the dj directory and run `lein repl`

 * Define your projects (with the project.clj file) in `dj/usr/src`. So something like `dj/usr/src/com.foo`

 * `(dj.dependencies/resolve-project "project-name")` to dynamically load a project. It will recursively resolve the project's dependencies.

 * If you want to depend on other projects add `:dj/dependencies ["foo" "bar"]` where `"foo"` and `"bar"` are projects in `dj/usr/src`.

 * In addition, instead of project names, you can use git urls. Like `"git://github.com/bmillare/dj.peg"`

## Useful namespaces

 * dj: core-like utility functions, check out `system-root` for relative paths

 * dj.io: file utils, see `file`, `poop`, and `eat`

 * dj.classloader: can reset native paths in runtime `reset-native-paths!`, grab resources as strings in the classpath `resource-as-str`, and reload class files (betcha didn't know you can do that) `reload-class-file`

 * dj.dependencies: `resolve-project`

 * dj.cljs: clojurescript utilities, see `cljs-repl`

 * dj.git: `clone` is useful. Not really a complete namespace

## Version

 * dj is listed as alpha software. It is highly experimental and changes on a whim. Stability will come naturally in time.

## Discussion

 * Profiles were found not necessary for native dependencies because of native-path handling of dj. However, it may be useful if you depend on a newer version of clojure.

## Author

Brent Millare
brent.millare@gmail.com

## License

Copyright (c) Brent Millare. All rights reserved. The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license. You must not remove this notice, or any other, from this software.