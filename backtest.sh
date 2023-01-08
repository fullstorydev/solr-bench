cd SolrNightlyBenchmarksWorkDirectory/Download/solr-repository
git reset --hard; git clean -fdx
git checkout branch_9x
git pull

#git log --since="25 August 2022" --until="29 October 2022" |head -n 25

for testname in "cluster-test.json" "stress-facets-local.json"
do
    for commit in `git log --since="1 September 2022" --until="1 December 2024" --pretty=format:"%H"| shuf`;
    do echo; echo "Running $commit"

    if [ -f "/home/ishan/code/solr-bench/suites/results/results-$testname-$commit.json" ]; then
        echo "Result file already exists for $commit"
    else
        echo "Trying commit: $commit"
        /home/ishan/code/solr-bench/cleanup.sh
        /home/ishan/code/solr-bench/stress.sh -c $commit /home/ishan/code/solr-bench/suites/$testname
        cd -; python createGraph.py --test $testname && cp $testname.html /var/www/html/$testname.html; cd -
    fi

    done

done

cd -

