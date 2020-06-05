cd ./SolrNightlyBenchmarksWorkDirectory/Download/git-repository


for i in {0..90}
do
	mydate=`date '+%d %b %Y' --date="$i days ago"`
	#echo $mydate
	git rev-list -1 --before="$mydate" master
done

cd -
