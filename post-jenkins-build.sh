cd /home/ishan/code/solr-bench
python createGraph.py
cp cluster-test.json.html /var/www/html 

#BASEDIR=$(realpath $(dirname "$0"))
BASEDIR="/home/ishan/code/solr-bench"
rm $BASEDIR/SolrNightlyBenchmarksWorkDirectory/Download/solr-*.tgz
