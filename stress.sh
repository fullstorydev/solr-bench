#!/bin/bash

echo_blue() {
   BLUE='\033[0;34m'
   NC='\033[0m' # No Color
   echo -e "${BLUE}$1${NC}"
}

SOLR_BENCH_VERSION="0.0.1-SNAPSHOT"

download() {
        file=$1
        if [[ $file == "https://"* ]] || [[ $file == "http://"* ]]
        then
		echo_blue "Downloading $file"
                wget -c $file
        elif [[ $file == "gs://"* ]]
        then
		echo_blue "Downloading $file"
                gsutil cp $file .
        fi
        # else, don't do anything
}

# parse the arguments
for i in $@; do :; done
CONFIGFILE=$i


# parse other params
while getopts "c:j:" option; do
  case $option in
    "c")
      commitoverrides+=("$OPTARG")
      ;;
    "j")
      echo "${OPTARG} found for benchmarking jar"
      SOLR_BENCH_JAR=${OPTARG}
      ;;
    *)
      # any other arguments for future
      ;;
  esac
done

ORIG_WORKING_DIR=`pwd`
BASEDIR=$(realpath $(dirname "$0"))

# perhaps we should allow define test name in config file as an extra property, it should give better flexibility.
TEST_NAME="${CONFIGFILE%.*}"
TEST_NAME="${TEST_NAME##*/}"
CONFIGFILE=`realpath $CONFIGFILE`
CONFIGFILE_DIR=`dirname $CONFIGFILE`

#PATCHURL="https://patch-diff.githubusercontent.com/raw/apache/solr/pull/1169.diff"
PATCHURL2="https://github.com/apache/solr/commit/b33161d0cdd976fc0c3dc78c4afafceb4db671cf.diff"
#PATCHURL="https://termbin.com/tcpd"
PATCHURL="https://termbin.com/7r9v"

cd $BASEDIR

echo "Configfile: $CONFIGFILE"
echo "Configfile dir: $CONFIGFILE_DIR"

download $CONFIGFILE # download this file from GCS/HTTP, if necessary
# CONFIGFILE="${CONFIGFILE##*/}" nocommit: do this only for web/gcs downloaded files

mkdir -p SolrNightlyBenchmarksWorkDirectory/Download
mkdir -p SolrNightlyBenchmarksWorkDirectory/RunDirectory


COMMIT=${commitoverrides[0]}

if [[ "null" != `jq -r '.["repositories"]' $CONFIGFILE` ]];
then
     while read i; do
         if [[ "" == $COMMIT ]]
         then
             COMMIT=`echo $i | jq -r '."commit-id"'`
         fi
         _LOCALREPO=$BASEDIR/SolrNightlyBenchmarksWorkDirectory/Download/`echo $i | jq -r '."name"'`
         _REPOSRC=`echo $i | jq -r '."url"'`
         _LOCALREPO_VC_DIR=$_LOCALREPO/.git

         if [ -d "$_LOCALREPO_VC_DIR" ]
         then
               cd $_LOCALREPO
               echo "Fetching from $_LOCALREPO"
               GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git fetch
         else
             echo "Cloning from $_REPOSRC to $_LOCALREPO"
             GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git clone --recurse-submodules $_REPOSRC $_LOCALREPO
             cd $_LOCALREPO
         fi

         if [[ `git cat-file -t $COMMIT` == "commit" || `git cat-file -t $COMMIT` == "tag" ]]
         then
             LOCALREPO=$_LOCALREPO
             REPOSRC=$_REPOSRC

             #for external mode we only checkout for git log history, do not load the rest
             if [ "external" != `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ]
             then
                  BUILDCOMMAND=`echo $i | jq -r '."build-command"'`
                  PACKAGE_DIR=`echo $i | jq -r '."package-subdir"'`
                  LOCALREPO_VC_DIR=$_LOCALREPO/.git
             fi
             break
         fi
     done <<< "$(jq -c '.["repositories"][]' $CONFIGFILE)"

     if [[ "" == $REPOSRC ]]
     then
         echo "$COMMIT not found in any configured repositories."
         exit 1
     fi
fi

cd $BASEDIR

GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"

export SOLR_TARBALL_NAME="solr-$COMMIT.tgz"
export SOLR_TARBALL_PATH="SolrNightlyBenchmarksWorkDirectory/Download/$SOLR_TARBALL_NAME"

if [[ "null" != `jq -r '.["solr-package"]' $CONFIGFILE` ]]
then
     solrpackageurl=`jq -r '.["solr-package"]' $CONFIGFILE`
     download $solrpackageurl
     export SOLR_TARBALL_NAME="${solrpackageurl##*/}"
     export SOLR_TARBALL_PATH=$SOLR_TARBALL_NAME
fi

terraform-gcp-provisioner() {
     echo_blue "Using Terraform provisioner"

     chmod +x start*sh

     # Generate the Terraform JSON file
     jq '.["cluster"]["terraform-gcp-config"]' $CONFIGFILE > terraform/terraform.tfvars.json

     # Generate temporary ssh keys
     rm terraform/id_rsa*
     ssh-keygen -f terraform/id_rsa -N ""

     # Provision instances using Terraform
     cd $BASEDIR/terraform
     terraform init 
     terraform apply --auto-approve

     # Wait 10 seconds for GCP to sync the SSH keys we need to login, needed esp. when using SSH
     # agent forwarding
     sleep 30

     # Start ZK & Solr on provisioned instances
     cd $BASEDIR
     export ZK_NODE=`terraform output -state=terraform/terraform.tfstate -json zookeeper_details|jq '.[] | .name'`
     export ZK_NODE=${ZK_NODE//\"/}
     export ZK_TARBALL_NAME="apache-zookeeper-3.6.4-bin.tar.gz"
     export ZK_TARBALL_PATH="$BASEDIR/apache-zookeeper-3.6.4-bin.tar.gz"
     export JDK_TARBALL=`jq -r '."cluster"."jdk-tarball"' $CONFIGFILE`
     export BENCH_USER=`jq -r '."cluster"."terraform-gcp-config"."user"' $CONFIGFILE`
     export BENCH_KEY="terraform/id_rsa"
     ./startzk.sh

     NODE_COUNTER=0
     for line in `terraform output -state=terraform/terraform.tfstate -json solr_node_details|jq '.[] | .name'`
     do
          export SOLR_STARTUP_PARAMS=`jq -r '."cluster"."startup-params"' $CONFIGFILE`
          OVERRIDE=`jq -r ".\\"cluster\\".\\"startup-params-overrides\\"[$NODE_COUNTER]" $CONFIGFILE`
          if [[ "null" == $OVERRIDE ]] ; then echo "No startup param override"; else export SOLR_STARTUP_PARAMS=$OVERRIDE; fi
          SOLR_NODE=${line//\"/}
          echo_blue "Starting Solr on $SOLR_NODE ($NODE_COUNTER), override=$OVERRIDE"
          ./startsolr.sh $SOLR_NODE
          let NODE_COUNTER=NODE_COUNTER+1
     done
}

buildsolr() {
     echo_blue "Building Solr package for $COMMIT"
     if [ ! -d $LOCALREPO_VC_DIR ]
     then
          GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git clone --recurse-submodules $REPOSRC $LOCALREPO
          cd $LOCALREPO
     else
          cd $LOCALREPO
          GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git fetch
     fi

     # remove local changes, if any
     GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git reset --hard
     GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git clean -fdx

     # checkout to the commit point
     GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git checkout $COMMIT
     if [[ "0" != "$?" ]]; then echo "Failed to checkout $COMMIT..."; exit 1; fi
     GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git submodule init 
     GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git submodule update


     if [[ "$PATCHURL" != "" ]];
     then
          echo "Applying patch"
          curl $PATCHURL | git apply -v --index
          if [[ "0" != "$?" ]]; then echo "Failed to apply patch."; else echo "Applied patch"; fi
          curl $PATCHURL2 | git apply -v --index
          if [[ "0" != "$?" ]]; then echo "Failed to apply patch 2."; else echo "Applied patch 2"; fi
     fi

     # Build Solr package
     bash -c "$BUILDCOMMAND"
     cd $LOCALREPO
     PACKAGE_PATH=`pwd`/`find . -name "solr*tgz" | grep -v src|head -1`
     echo_blue "Package found here: $PACKAGE_PATH"
     cp $PACKAGE_PATH $BASEDIR/SolrNightlyBenchmarksWorkDirectory/Download/solr-$COMMIT.tgz
}

generate_meta() {
     local meta_file_path="${BASEDIR}/suites/results/$TEST_NAME/meta-${COMMIT}.prop"
     echo_blue "Generating Meta data file by reading info from $LOCALREPO"
     cd $LOCALREPO


     local branches=""
     while IFS= read -r branch
     do
       branch=${branch#"origin/"}
       if [ -z "$branches" ]
       then
         branches="$branch"
       else
         branches="${branches},${branch}"
       fi
     done <<< "$(git branch -r --contains $COMMIT 2> /dev/null | sed -e 's/* \(.*\)/\1/' | tr -d ' ')"

     echo "branches=$branches" > $meta_file_path
     local committed_ts=`git show -s --format=%ct $COMMIT`
     echo "committed_date=$committed_ts" >> $meta_file_path
     local committed_name=`git show -s --format=%cN $COMMIT`
     echo "committer=$committed_name" >> $meta_file_path
     local note=`git show -s --format=%s $COMMIT`
     echo "message=$note" >> $meta_file_path

     echo_blue "Meta file $meta_file_path contents:"
     cat $meta_file_path
}

# Download the pre-requisites
download `jq -r '."cluster"."jdk-url"' $CONFIGFILE`
for i in `jq -r '."pre-download" | .[]' $CONFIGFILE`; do cd $CONFIGFILE_DIR; download $i; cd $BASEDIR; done

if [ "external" != `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ]
then
  wget -c https://downloads.apache.org/zookeeper/zookeeper-3.6.4/apache-zookeeper-3.6.4-bin.tar.gz
  # Clone/checkout the git repository and build Solr
  if [[ "null" == `jq -r '.["solr-package"]' $CONFIGFILE` ]] && [ ! -f $BASEDIR/SolrNightlyBenchmarksWorkDirectory/Download/solr-$COMMIT.tgz ]
  then
       buildsolr
  fi
fi

cd $BASEDIR

if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     terraform-gcp-provisioner
fi

# Run the benchmarking suite
cd $BASEDIR
echo_blue "Running Stress suite from working directory: $BASEDIR"
if [ -z "$SOLR_BENCH_JAR" ] #then no explicit jar provided
then
  java -Xmx12g -cp $BASEDIR/target/org.apache.solr.benchmarks-${SOLR_BENCH_VERSION}-jar-with-dependencies.jar:. \
   StressMain -f $CONFIGFILE -c $COMMIT
else
  java -Xmx12g -cp ${SOLR_BENCH_JAR}:. StressMain -f $CONFIGFILE -c $COMMIT
fi


# Grab GC logs
NOW=`date +"%Y-%d-%m_%H.%M.%S"`
if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     echo_blue "Pulling logs"
     cd $BASEDIR
     for line in `terraform output -state=terraform/terraform.tfstate -json solr_node_details|jq '.[] | .name'`
     do
        SOLR_NODE=${line//\"/}
        SOLR_DIR=`tar --exclude='*/*/*' -tf ${SOLR_TARBALL_PATH} | head -1| cut -d '/' -f 1`
	ssh -i terraform/id_rsa -oStrictHostKeyChecking=no  $BENCH_USER@$SOLR_NODE "tar -cf solrlogs-${SOLR_NODE}.tar $SOLR_DIR/server/logs"
	scp -i terraform/id_rsa -oStrictHostKeyChecking=no  $BENCH_USER@$SOLR_NODE:solrlogs-${SOLR_NODE}.tar .
        zip logs-${NOW}.zip solrlogs*tar
     done

     echo_blue "Removing the hostname entry from ~/.ssh/known_hosts, so that another run can be possible afterwards"
     cd $BASEDIR
     for line in `terraform output -state=terraform/terraform.tfstate -json solr_node_details|jq '.[] | .name'`
     do
        SOLR_NODE=${line//\"/}
        ssh-keygen -R "$SOLR_NODE"
     done
     ZK_NODE=`terraform output -state=terraform/terraform.tfstate -json zookeeper_details|jq '.[] | .name'`
     ssh-keygen -R "$ZK_NODE"
fi
if [ "local" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     echo_blue "Collecting logs"
     mkdir -p $BASEDIR/suites/results/logs
     zip -j $BASEDIR/suites/results/logs/logs-${CONFIGFILE##*/}-$COMMIT.zip $BASEDIR/SolrNightlyBenchmarksWorkDirectory/RunDirectory/logs-*tar
     rm -rf $BASEDIR/SolrNightlyBenchmarksWorkDirectory/RunDirectory/logs-*tar
fi

# Results upload (results.json), if needed
#cd $BASEDIR
#if [[ "null" != `jq -r '.["results-upload-location"]' $CONFIGFILE` ]]
#then
#     # Results uploading only supported for GCS buckets for now
#     mv results.json results-${NOW}.json
#     gsutil cp results-${NOW}.json `jq -r '.["results-upload-location"]' $CONFIGFILE`
#     gsutil cp logs-${NOW}.zip `jq -r '.["results-upload-location"]' $CONFIGFILE`
#fi

# Rename the result files for local test
if [ "local" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ] || [ "external" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     result_dir="${BASEDIR}/suites/results/${TEST_NAME}"
     mkdir -p $result_dir
     if [[ "null" != `jq -r '.["repositories"]' $CONFIGFILE` ]];
     then
          generate_meta
     fi
     cp $CONFIGFILE $result_dir/configs-$COMMIT.json
     cp $BASEDIR/results.json $result_dir/results-$COMMIT.json
     cp $BASEDIR/metrics.json $result_dir/metrics-$COMMIT.json
     rm $BASEDIR/results.json $BASEDIR/metrics.json

     echo_blue "Result can be found in $result_dir"
fi

# Cleanup
if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     cd $BASEDIR/terraform
     terraform destroy --auto-approve
     rm id_rsa*
fi

