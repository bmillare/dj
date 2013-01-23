(ns dj.datomic
  (:require [cemerick.pomegranate :as pom]
            [dj]
            [dj.io]))

(defn download []
  "goto http://downloads.datomic.com/free.html and download the newest version of datomic")

(defn install [datomic-zip-file]
  "installs the downloaded version of datomic in local maven repository"
  (let [tmp-dir (dj.io/file dj/system-root "tmp")
        folder-name (dj/substring (dj.io/get-name datomic-zip-file)
                                  0
                                  -4)
        content-folder (dj.io/file tmp-dir folder-name)
        [_ name version] (re-matches #"(datomic-free)-(.+)"
                                     folder-name)
        pom-file (dj.io/file content-folder "pom.xml")]
    (dj.io/unzip datomic-zip-file
                 tmp-dir)
    (cemerick.pomegranate.aether/install :coordinates ['com.datomic/datomic-free version]
                                         :jar-file (dj.io/file content-folder (str folder-name ".jar"))
                                         :pom-file pom-file)
    #_ (let [transactor-pom-file (dj.io/file content-folder "transactor-pom.xml")]
         (dj.io/poop transactor-pom-file
                     (dj/replace-map (dj.io/eat pom-file)
                                     {"datomic-free" "datomic-free-transactor"}))
         (cemerick.pomegranate.aether/install :coordinates ['com.datomic/datomic-free-transactor version]
                                              :jar-file (dj.io/file content-folder (str name "-transactor-" version ".jar"))
                                              :pom-file transactor-pom-file))))

