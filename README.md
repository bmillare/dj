# dj 2.0.0

## Motivation

"dj takes the cacaphony of java, git, clojure, clojurescript and build
tools and mixes it into something harmonious."

In a nutshell, dj is an attempt to make a clojure distribution. To be
more accessible, dj now uses leiningen as a base for creating a
distribution.

Lots of changes in the new branch, more to come.

## Basic Usage

 * Install by cloning. Depends on leiningen 2.0 > preview10

 * `cd` into the dj directory and run `lein repl`

 * (dj.dependencies/resolve-project "project-name") to dynamically
   load a project.

## Discussion

 * Recommends creating a script that cd's into the dj's directory and
   runs lein repl, this allows you to run dj anywhere.

 * Profiles were found not necessary for native dependencies because of
   native-path handling of dj. However, it may be useful if you depend
   on a newer version of clojure.

## Author

Brent Millare
brent.millare@gmail.com

## License

Copyright (c) Brent Millare. All rights reserved. The use and
distribution terms for this software are covered by the Eclipse Public
License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
be found in the file epl-v10.html at the root of this distribution. By
using this software in any fashion, you are agreeing to be bound by
the terms of this license. You must not remove this notice, or any
other, from this software.