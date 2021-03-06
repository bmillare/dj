(ns dj.cljs
  (:refer-clojure :exclude [load-file])
  (:require [dj]
            [dj.io]
            [dj.cljs.install]
            [cljs.repl]
            [cljs.repl.browser]
            [cljs.repl.server]
            [cljs.analyzer :as ca]))

(defn ->cljs-browser-env
  "port: for repl/server
working-dir: path/file to generated js (dj/system-root relative)

Creates a browser connected evaluation environment object and returns
it. This object wraps a repl-env

To connect to server, run cljs in browser
 (clojure.browser.repl/connect \"http://localhost:<port>/repl\")
Make sure advanced optimizations is not activated. Simple works though.

Use load-file or load-namespace to do dynamic development"
  [opts]
  (let [{:keys [port working-dir]} opts]
    (dj.io/rm (dj.io/file dj/system-root
                          "out"))
    (dj.io/rm (dj.io/file dj/system-root
                          working-dir))
    (let [repl-env (cljs.repl.browser/repl-env :port port
                                               :working-dir working-dir)]
      (reify
        dj.repl/Lifecycle
        (start [this]
          (doto repl-env
            cljs.repl/-setup))
        (stop [this]
          (cljs.repl/-tear-down repl-env))
        clojure.lang.IDeref
        (deref [this]
          repl-env)))))

(defn cljs-eval
  "note this accepts the object returned from ->cljs-browser-env, not a repl-env"
  [cljs-browser-env form]
  (dj.io/capture-out-err
   (cljs.repl/evaluate-form @cljs-browser-env
                            {:context :statement :locals {}
                             :ns (ca/get-namespace ca/*cljs-ns*)}
                            "<dj.cljs/cljs-eval>"
                            form)))

(defn load-file [cljs-browser-env f]
  (cljs.repl/load-file @cljs-browser-env f))

(defn load-namespace [cljs-browser-env n]
  (cljs.repl/load-namespace @cljs-browser-env n))
