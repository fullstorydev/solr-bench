ORIGINAL_DIR=`pwd`
cd SolrNightlyBenchmarksWorkDirectory/Download/solr-repository
git reset --hard; git clean -fdx
git checkout branch_9x
git pull

for commit in `git log --since="1 July 2021" --until="1 January 2029" --pretty=format:"%H"`;
do echo; echo "Running $commit"
  for testnamefile in "cluster-test.json" "stress-facets-local.json" "prs-vs-nonprs.json"
  do
    cd $ORIGINAL_DIR
    testname=`echo $testnamefile|cut -d "." -f 1`
    if [ -f "suites/results/$testname/results-$commit.json" ]; then
        echo "Result file already exists for $commit"
    else
        echo "Trying commit: $commit"
        ./cleanup.sh
        ./stress.sh -c $commit -v suites/$testnamefile
	python3 graph-scripts/generate_graph_json.py -r suites/results/cluster-test -r suites/results/prs-vs-nonprs -r suites/results/stress-facets-local -b branch_9x...branch_9_1
	mv graph/* /var/www/html
    fi

  done
done

cd $ORIGINAL_DIR

