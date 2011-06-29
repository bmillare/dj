#!/bin/bash
echo "Assumes java, git, unzip, and wget are installed"
echo "Installing dj"
git clone git://github.com/bmillare/dj.git
cd dj
mkdir -p usr/src/clojure usr/bin etc sbin opt tmp/dj tmp/repository var lib
echo "Installing clojure"
cd opt
wget --no-check-certificate 'http://repo1.maven.org/maven2/org/clojure/clojure/1.3.0-beta1/clojure-1.3.0-beta1.zip'
unzip clojure-1.3.0-beta1.zip
rm clojure-1.3.0-beta1.zip
cd ../lib
ln -s ../opt/clojure-1.3.0-beta1/clojure-1.3.0-beta1.jar clojure.jar

echo "==================== IMPORTANT ===================="
echo "Symlink dj/bin/run-dj-tool.sh to your path"
echo "example: cd ~/; ln -s dj/bin/run-dj-tool.sh dj"
