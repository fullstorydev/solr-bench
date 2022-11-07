for i in {50000..50050}; 
do 
   pid=`lsof -t -i:$i`
   if [[ "$pid" != "" ]]; then
      kill -9 $pid
   fi
done

pid=`lsof -t -i:2181`
if [[ "$pid" != "" ]]; then
  kill -9 $pid
fi

rm -rf /tmp/zookeeper; rm -rf SolrNightlyBenchmarksWorkDirectory/RunDirectory/*
