cd SolrNightlyBenchmarksWorkDirectory/Download/solr-repository
git checkout branch_9x
git pull

#git log --since="1 June 2022" --until="29 October 2022" |head -n 25

for commit in `git log --since="1 June 2022" --until="29 October 2022" --pretty=format:"%H" --reverse`;
do echo "running $commit"

/home/ishan/code/solr-bench/cleanup.sh

if [ -f "$/home/ishan/code/solr-bench/suites/results/results-cluster-test.json-$commit.json" ]; then
    echo "Result file already exists for $commit"
else
    /home/ishan/code/solr-bench/stress.sh -c $commit /home/ishan/code/solr-bench/suites/cluster-test.json
fi

done

cd -
