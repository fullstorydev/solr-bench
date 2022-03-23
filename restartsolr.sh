SOLR_NODE=$1
USER=$2
echo "Restarting $SOLR_NODE"
echo "JDK_TARBALL is $JDK_TARBALL"
echo "SOLR_TARBALL_NAME is $SOLR_TARBALL_NAME"
echo "ZK_NODE is $ZK_NODE"
echo "SOLR_STARTUP_PARAMS is $SOLR_STARTUP_PARAMS"

ssh -i terraform/id_rsa -oStrictHostKeyChecking=no $USER@$SOLR_NODE "
        export JDK_TARBALL=$JDK_TARBALL;
        tar -xf $JDK_TARBALL; 
        export JDK_DIR=\`tar tf $JDK_TARBALL | head -1| cut -d '/' -f 1\`;
        export JAVA_HOME=\`pwd\`/\$JDK_DIR; 
        export PATH=\$JAVA_HOME/bin:\$PATH; 

        export SOLR_DIR=\`pwd\`/\`tar --exclude='*/*/*' -tf $SOLR_TARBALL_NAME | head -1| cut -d '/' -f 1\`;
        tar -xf $SOLR_TARBALL_NAME;

        cd \$SOLR_DIR;
        bin/solr stop;
        bin/solr -V -c $SOLR_STARTUP_PARAMS -z $ZK_NODE:2181 -Dhost=$SOLR_NODE
"

