ORIGINAL_DIR=`pwd`
cd SolrNightlyBenchmarksWorkDirectory/Download/solr-repository
git reset --hard; git clean -fdx
git checkout branch_9x
git pull

for commit in `git log --since="1 July 2021" --until="1 January 2029" --pretty=format:"%H"`;
do echo; echo "Running $commit"
  for testnamefile in "prs-vs-nonprs.json" "cluster-test.json" "stress-facets-local.json"
  do
    cd $ORIGINAL_DIR
    testname=`echo $testnamefile|cut -d "." -f 1`
    if [ -f "suites/results/$testname/results-$commit.json" ]; then
        echo "Result file already exists for $commit"
    else
        echo "Trying commit: $commit"
        ./cleanup.sh
        ./stress.sh -c $commit -v suites/$testnamefile
	python createBranchGraph.py -b branch_9x
	mv branch_9x.html /var/www/html
    fi

  done
done

cd $ORIGINAL_DIR

