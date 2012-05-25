# This script installs Rhino from their github repo without requiring a github client
# or apache ant to be installed
# Just needs curl, and tar
# This script is only necessary for a short period, until Rhino1.7.R4 is released
mkdir inst_tmp
cd inst_tmp
curl http://www.apache.org/dist/ant/binaries/apache-ant-1.8.4-bin.tar.gz > ant.tar.gz
tar -zxf ant.tar.gz
curl https://nodeload.github.com/mozilla/rhino/tarball/master > rhino.tar.gz
tar -zxf rhino.tar.gz
mv mozilla-rhino-* mozilla-rhino
cd mozilla-rhino
../apache-ant-1.8.4/bin/ant jar
cp build/rhino1_7R4*/js.jar ../../lib/jars
cd ../..
rm -rf inst_tmp
