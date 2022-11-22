cd SolrNightlyBenchmarksWorkDirectory/Download/solr-repository
git reset --hard; git clean -fdx
git checkout branch_9x
git pull

#git log --since="25 August 2022" --until="29 October 2022" |head -n 25

for commit in `git log --since="10 October 2022" --until="22 November 2022" --pretty=format:"%H"`;
do echo; echo "Running $commit"

if [ -f "/home/ishan/code/solr-bench/suites/results/results-cluster-test.json-$commit.json" ]; then
    echo "Result file already exists for $commit"
else
    /home/ishan/code/solr-bench/cleanup.sh
    /home/ishan/code/solr-bench/stress.sh -c $commit /home/ishan/code/solr-bench/suites/cluster-test.json
    cd -; python createGraph.py && cp cluster-test.json.html /var/www/html/cluster-test-heap.html; cd -
fi

done

cd -

