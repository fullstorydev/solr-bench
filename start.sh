#!/bin/bash

SOLR_BENCH_VERSION="0.0.1-SNAPSHOT"

download() {
        file=$1
        if [[ $file == "https://"* ]] || [[ $file == "http://"* ]]
        then
		echo "Downloading $file"
                curl -O $file
        elif [[ $file == "gs://"* ]]
        then
		echo "Downloading $file"
                gsutil cp $file .
        fi
        # else, don't do anything
}

ORIG_WORKING_DIR=`pwd`
CONFIGFILE=$1

download $CONFIGFILE # download this file from GCS/HTTP, if necessary
CONFIGFILE="${CONFIGFILE##*/}"

mkdir -p SolrNightlyBenchmarksWorkDirectory/Download

COMMIT=`jq -r '."repository"."commit-id"' $CONFIGFILE`
REPOSRC=`jq -r '."repository"."url"' $CONFIGFILE`
LOCALREPO=`pwd`/SolrNightlyBenchmarksWorkDirectory/Download/`jq -r '."repository"."name"' $CONFIGFILE`
BUILDCOMMAND=`jq -r '."repository"."build-command"' $CONFIGFILE`
PACKAGE_DIR=`jq -r '."repository"."package-subdir"' $CONFIGFILE`
LOCALREPO_VC_DIR=$LOCALREPO/.git
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
     echo "Using Terraform provisioner"

     chmod +x start*sh

     # Generate the Terraform JSON file
     jq '.["cluster"]["terraform-gcp-config"]' $CONFIGFILE > terraform/terraform.tfvars.json

     # Generate temporary ssh keys
     rm terraform/id_rsa*
     ssh-keygen -f terraform/id_rsa -N ""

     # Provision instances using Terraform
     cd $ORIG_WORKING_DIR/terraform
     terraform init 
     terraform apply --auto-approve

     # Start Solr on provisioned instances
     cd $ORIG_WORKING_DIR
     export SOLR_STARTUP_PARAMS=`jq -r '."cluster"."startup-params"' $CONFIGFILE`
     export ZK_NODE=`terraform output -state=terraform/terraform.tfstate -json zookeeper_details|jq '.[] | .name'`
     export ZK_NODE=${ZK_NODE//\"/}
     export ZK_TARBALL_NAME="apache-zookeeper-3.5.6-bin.tar.gz"
     export ZK_TARBALL_PATH="$ORIG_WORKING_DIR/apache-zookeeper-3.5.6-bin.tar.gz"
     export JDK_TARBALL=`jq -r '."cluster"."jdk-tarball"' $CONFIGFILE`

     ./startzk.sh

     for line in `terraform output -state=terraform/terraform.tfstate -json solr_node_details|jq '.[] | .name'`
     do
          SOLR_NODE=${line//\"/}
          echo "Starting Solr on $SOLR_NODE"
          ./startsolr.sh $SOLR_NODE
     done
}

# Download the pre-requisites
wget -c `jq -r '."cluster"."jdk-url"' $CONFIGFILE`
wget -c https://archive.apache.org/dist/zookeeper/zookeeper-3.5.6/apache-zookeeper-3.5.6-bin.tar.gz
for i in `jq -r '."pre-download" | .[]' $CONFIGFILE`; do echo "Downloading $i"; download $i; done

# Some housekeeping
chmod +x wait-for-it.sh
rm ~/.ssh/known_hosts

# Clone/checkout the git repository and build Solr

if [[ "null" == `jq -r '.["solr-package"]' $CONFIGFILE` ]] && [ ! -f $ORIG_WORKING_DIR/SolrNightlyBenchmarksWorkDirectory/Download/solr-$COMMIT.tgz ]
then
     echo "Building Solr package for $COMMIT"
     if [ ! -d $LOCALREPO_VC_DIR ]
     then
          GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git clone --recurse-submodules $REPOSRC $LOCALREPO
          cd $LOCALREPO
     else
          cd $LOCALREPO
          GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git fetch
     fi
     GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git checkout $COMMIT
     GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git submodule init 
     GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" git submodule update

     # Build Solr package
     bash -c "$BUILDCOMMAND"
     cd $LOCALREPO
     PACKAGE_PATH=`find . -name "solr*tgz" | grep -v src`
     echo "Package found here: $PACKAGE_PATH"
     cp $PACKAGE_PATH $ORIG_WORKING_DIR/SolrNightlyBenchmarksWorkDirectory/Download/solr-$COMMIT.tgz
fi

cd $ORIG_WORKING_DIR

if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     terraform-gcp-provisioner
fi

# Run the benchmarking suite
cd $ORIG_WORKING_DIR
echo "Running suite from working directory: $ORIG_WORKING_DIR"
java -cp org.apache.solr.benchmarks-${SOLR_BENCH_VERSION}-jar-with-dependencies.jar:target/org.apache.solr.benchmarks-${SOLR_BENCH_VERSION}-jar-with-dependencies.jar:. \
   org.apache.solr.benchmarks.BenchmarksMain $CONFIGFILE

# Grab GC logs

if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     echo "Pulling logs"
     for line in `terraform output -state=terraform/terraform.tfstate -json solr_node_details|jq '.[] | .name'`
     do
        SOLR_NODE=${line//\"/}
        SOLR_DIR=`tar --exclude='*/*/*' -tf ${SOLR_TARBALL_NAME} | head -1| cut -d '/' -f 1`
        #cmd="scp -i terraform/id_rsa -oStrictHostKeyChecking=no  solruser@$SOLR_NODE:$SOLR_DIR/server/logs/solr_gc.log.0.current ${SOLR_NODE}_gc.log"
	ssh -i terraform/id_rsa -oStrictHostKeyChecking=no  solruser@$SOLR_NODE "tar -cf solrlogs-${SOLR_NODE}.tar $SOLR_DIR/server/logs"
	scp -i terraform/id_rsa -oStrictHostKeyChecking=no  solruser@$SOLR_NODE:solrlogs-${SOLR_NODE}.tar .
        zip logs.zip solrlogs*tar

        echo "Running $cmd"
        $cmd
     done
fi

# Results upload (results.json), if needed
cd $ORIG_WORKING_DIR
if [[ "null" != `jq -r '.["results-upload-location"]' $CONFIGFILE` ]]
then
     # Results uploading only supported for GCS buckets for now
     gsutil cp results.json `jq -r '.["results-upload-location"]' $CONFIGFILE`
     gsutil cp logs.zip `jq -r '.["results-upload-location"]' $CONFIGFILE`
fi

# Cleanup
if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     cd $ORIG_WORKING_DIR/terraform
     terraform destroy --auto-approve
     rm id_rsa*
fi

