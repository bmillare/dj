#!/bin/bash
echo "Assumes java, git are installed"
echo "Installing dj"
git clone git://github.com/bmillare/dj.git
cd dj
mkdir -p usr/src usr/bin etc sbin opt tmp/dj var lib
echo "Installing clojure"
cd opt
wget http://github.com/downloads/clojure/clojure/clojure-1.2.0.zip
unzip clojure-1.2.0.zip
cd ../lib
ln -s ../opt/clojure-1.2.0/clojure.jar clojure.jar

echo "==================== IMPORTANT ===================="
echo "Symlink dj/bin/run-dj-tool.sh to your path"
echo "example: cd ~/; ln -s dj/bin/run-dj-tool.sh dj"