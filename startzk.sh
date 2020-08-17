#!/bin/bash

echo_blue() {
   BLUE='\033[0;34m'
   NC='\033[0m' # No Color
   echo -e "${BLUE}$1${NC}"
}

./wait-for-it.sh -t 0 $ZK_NODE:22
echo_blue "Starting Zookeeper on $ZK_NODE"
ssh -i $BENCH_KEY -oStrictHostKeyChecking=no $BENCH_USER@$ZK_NODE uptime
ssh -i $BENCH_KEY -oStrictHostKeyChecking=no $BENCH_USER@$ZK_NODE rm -rf *zook* 
ssh -i $BENCH_KEY -oStrictHostKeyChecking=no $BENCH_USER@$ZK_NODE rm -rf /tmp/zookeeper 
ssh -i $BENCH_KEY -oStrictHostKeyChecking=no $BENCH_USER@$ZK_NODE sudo pkill -9 java
scp -i $BENCH_KEY -oStrictHostKeyChecking=no $ZK_TARBALL_PATH $BENCH_USER@$ZK_NODE:
scp -i $BENCH_KEY -oStrictHostKeyChecking=no ${JDK_TARBALL} $BENCH_USER@$ZK_NODE:


ssh -i $BENCH_KEY -oStrictHostKeyChecking=no $BENCH_USER@$ZK_NODE "
	export JDK_TARBALL=$JDK_TARBALL;
	tar -xf $JDK_TARBALL; 
	export JDK_DIR=\`tar tf $JDK_TARBALL | head -1| cut -d '/' -f 1\`;
	export JAVA_HOME=\`pwd\`/\$JDK_DIR; 
	export PATH=\$JAVA_HOME/bin:\$PATH; 

	export ZK_DIR=\`pwd\`/\`tar --exclude='*/*/*' -tf $ZK_TARBALL_NAME | head -1| cut -d '/' -f 1\`;
	tar -xf $ZK_TARBALL_NAME;

	cd \$ZK_DIR;
	cp conf/zoo_sample.cfg conf/zoo.cfg;
    bin/zkServer.sh start
"
