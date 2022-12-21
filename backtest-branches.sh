CONFIGFILE="cluster-test.json"
BASEDIR=$(realpath $(dirname "$0"))

while read i; do
    _LOCALREPO=$BASEDIR/SolrNightlyBenchmarksWorkDirectory/Download/`echo $i | jq -r '."name"'`
    _REPOSRC=`echo $i | jq -r '."url"'`
    _LOCALREPO_VC_DIR=$REPO/.git
    _BUILDCOMMAND=`echo $i | jq -r '."build-command"'`
    
    cd $_LOCALREPO
    git fetch
    
    if [[ $_BUILDCOMMAND == *"gradlew"*  ]]; then
        PREFIX="releases/solr/9"
    else
        PREFIX="releases/lucene-solr/8"
    fi
    for tag in `git tag|grep "$PREFIX"`; do    
        echo "Tag: $tag"
        COMMIT=`git show-ref $tag| cut -f 1 -d ' '`

        git reset --hard; git clean -fdx
        git checkout $COMMIT

        if [ -f "$BASEDIR/suites/results/results-$CONFIGFILE-$COMMIT.json" ]; then
            echo "Result file already exists for $COMMIT"
        else
            $BASEDIR/cleanup.sh
            $BASEDIR/stress.sh -c $COMMIT $BASEDIR/suites/$CONFIGFILE
            cd $BASEDIR; python createGraphBranches.py && cp $CONFIGFILE.html /var/www/html
        fi
    done
done <<< "$(jq -c '.["repositories"][]' suites/$CONFIGFILE)"

