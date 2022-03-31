SOLR_NODE=$1
USER=$2

echo "Restarting $SOLR_NODE"
echo "USERNAME is $USER"
echo "SOLR_STARTUP_PARAMS is $SOLR_STARTUP_PARAMS"

ssh -oStrictHostKeyChecking=no $USER@$SOLR_NODE "
        cd /opt/solr;
        bin/solr stop;
        bin/solr -V -c $SOLR_STARTUP_PARAMS
"