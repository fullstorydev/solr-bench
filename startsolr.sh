#!/bin/bash

echo_blue() {
   BLUE='\033[0;34m'
   NC='\033[0m' # No Color
   echo -e "${BLUE}$1${NC}"
}


SOLR_NODE=$1

echo_blue "Starting Solr from $SOLR_TARBALL_NAME in $SOLR_TARBALL_PATH..."
./wait-for-it.sh -t 0 $SOLR_NODE:22
./wait-for-it.sh -t 0 $ZK_NODE:2181

ssh -i terraform/id_rsa -oStrictHostKeyChecking=no solruser@$SOLR_NODE uname -a
ssh -i terraform/id_rsa -oStrictHostKeyChecking=no solruser@$SOLR_NODE rm -rf solr* 
ssh -i terraform/id_rsa -oStrictHostKeyChecking=no solruser@$SOLR_NODE sudo pkill -9 java
scp -i terraform/id_rsa -oStrictHostKeyChecking=no ${SOLR_TARBALL_PATH} solruser@$SOLR_NODE:
scp -i terraform/id_rsa -oStrictHostKeyChecking=no /usr/bin/lsof solruser@$SOLR_NODE:
ssh -i terraform/id_rsa -oStrictHostKeyChecking=no solruser@$SOLR_NODE sudo mv lsof /usr/bin/lsof
scp -i terraform/id_rsa -oStrictHostKeyChecking=no ${JDK_TARBALL} solruser@$SOLR_NODE:

ssh -i terraform/id_rsa -oStrictHostKeyChecking=no solruser@$SOLR_NODE "
        sudo mkdir -p /mnt/scratch; sudo mkfs.ext4 /dev/nvme0n1; sudo mount /dev/nvme0n1 /mnt/scratch; sudo chmod 777 /mnt/scratch;
	export JDK_TARBALL=$JDK_TARBALL;
	tar -xf $JDK_TARBALL; 
	export JDK_DIR=\`tar tf $JDK_TARBALL | head -1| cut -d '/' -f 1\`;
	export JAVA_HOME=\`pwd\`/\$JDK_DIR; 
	export PATH=\$JAVA_HOME/bin:\$PATH; 

	export SOLR_DIR=\`pwd\`/\`tar --exclude='*/*/*' -tf $SOLR_TARBALL_NAME | head -1| cut -d '/' -f 1\`;
	tar -xf $SOLR_TARBALL_NAME;

	cd \$SOLR_DIR;
	bin/solr -V -c $SOLR_STARTUP_PARAMS -z $ZK_NODE:2181 -Dsolr.host=$SOLR_NODE
"
