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
                curl -O $file
        elif [[ $file == "gs://"* ]]
        then
		echo_blue "Downloading $file"
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
     echo_blue "Using Terraform provisioner"

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
     export ZK_TARBALL_NAME="apache-zookeeper-3.6.1-bin.tar.gz"
     export ZK_TARBALL_PATH="$ORIG_WORKING_DIR/apache-zookeeper-3.6.1-bin.tar.gz"
     export JDK_TARBALL=`jq -r '."cluster"."jdk-tarball"' $CONFIGFILE`
     export BENCH_USER="solruser"
     export BENCH_KEY="terraform/id_rsa"
     ./startzk.sh

     for line in `terraform output -state=terraform/terraform.tfstate -json solr_node_details|jq '.[] | .name'`
     do
          SOLR_NODE=${line//\"/}
          echo_blue "Starting Solr on $SOLR_NODE"
          ./startsolr.sh $SOLR_NODE
     done
}

vagrant-provisioner() {
     echo_blue "Using Vagrant provisioner"

     cd $ORIG_WORKING_DIR

     chmod +x start*sh

     # Generate the Terraform JSON file
     export SOLR_NODE_COUNT=`jq '.["cluster"]["vagrant-config"]["solr_node_count"]' $CONFIGFILE`
     export ZK_NODE_COUNT=1

     export JDK_TARBALL=`jq -r '."cluster"."jdk-tarball"' $CONFIGFILE`
     export ZK_TARBALL_NAME="apache-zookeeper-3.6.1-bin.tar.gz"

     echo_blue "Solr servers: $SOLR_NODE_COUNT"
     bash -c 'printf "["; for ((i=1; i<$SOLR_NODE_COUNT; i++)); do printf "$i,"; done; printf $SOLR_NODE_COUNT; printf "]"' | jq 'map({"name":("solr-"+(.|tostring)), "ip": ("192.168.5."+((.+100)|tostring))}) | map( {(.name): .ip}  )|add' > vagrant/solr-servers.json
     bash -c 'printf "["; for ((i=1; i<$ZK_NODE_COUNT; i++));   do printf "$i,"; done; printf $ZK_NODE_COUNT;   printf "]"' | jq 'map({"name":("zk-"+(.|tostring)), "ip": ("192.168.5."+((.+ 50)|tostring))}) | map( {(.name): .ip}  )|add' > vagrant/zk-servers.json

     # Generate temporary ssh keys
     rm vagrant/id_rsa*
     ssh-keygen -f vagrant/id_rsa -N ""

     # Copy JDK and ZK downloads to vagrant/files
     cp $JDK_TARBALL vagrant/files
     cp $ZK_TARBALL_NAME vagrant/files

     # Provision instances using Terraform
     cd $ORIG_WORKING_DIR/vagrant
     vagrant destroy -f
     vagrant up --provider virtualbox 

     echo_blue "NOCOMMIT this should work till here"
     # Start Solr on provisioned instances
     cd $ORIG_WORKING_DIR

     export SOLR_STARTUP_PARAMS=`jq -r '."cluster"."startup-params"' $CONFIGFILE`
     export ZK_NODE=`jq -r ".[]" vagrant/zk-servers.json`
     export ZK_TARBALL_PATH="$ORIG_WORKING_DIR/apache-zookeeper-3.6.1-bin.tar.gz"
     export BENCH_USER="vagrant"
     export BENCH_KEY="vagrant/id_rsa"

     # ./startzk.sh

     for SOLR_NODE in `jq -r ".[]" vagrant/solr-servers.json`
     do
          echo_blue "Starting Solr on $SOLR_NODE"
          ./startsolr.sh $SOLR_NODE
     done
}


# Download the pre-requisites
wget -c `jq -r '."cluster"."jdk-url"' $CONFIGFILE`
wget -c https://downloads.apache.org/zookeeper/zookeeper-3.6.1/apache-zookeeper-3.6.1-bin.tar.gz
for i in `jq -r '."pre-download" | .[]' $CONFIGFILE`; do download $i; done

# Clone/checkout the git repository and build Solr

if [[ "null" == `jq -r '.["solr-package"]' $CONFIGFILE` ]] && [ ! -f $ORIG_WORKING_DIR/SolrNightlyBenchmarksWorkDirectory/Download/solr-$COMMIT.tgz ]
then
     echo_blue "Building Solr package for $COMMIT"
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
     echo_blue "Package found here: $PACKAGE_PATH"
     cp $PACKAGE_PATH $ORIG_WORKING_DIR/SolrNightlyBenchmarksWorkDirectory/Download/solr-$COMMIT.tgz
fi

cd $ORIG_WORKING_DIR

if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     terraform-gcp-provisioner
elif [ "vagrant" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     vagrant-provisioner
fi

# Run the benchmarking suite
cd $ORIG_WORKING_DIR
echo_blue "Running suite from working directory: $ORIG_WORKING_DIR"
java -cp org.apache.solr.benchmarks-${SOLR_BENCH_VERSION}-jar-with-dependencies.jar:target/org.apache.solr.benchmarks-${SOLR_BENCH_VERSION}-jar-with-dependencies.jar:. \
   org.apache.solr.benchmarks.BenchmarksMain $CONFIGFILE

# Grab GC logs
NOW=`date +"%Y-%d-%m_%H.%M.%S"`
if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     echo_blue "Pulling logs"
     for line in `terraform output -state=terraform/terraform.tfstate -json solr_node_details|jq '.[] | .name'`
     do
        SOLR_NODE=${line//\"/}
        SOLR_DIR=`tar --exclude='*/*/*' -tf ${SOLR_TARBALL_NAME} | head -1| cut -d '/' -f 1`
	ssh -i terraform/id_rsa -oStrictHostKeyChecking=no  solruser@$SOLR_NODE "tar -cf solrlogs-${SOLR_NODE}.tar $SOLR_DIR/server/logs"
	scp -i terraform/id_rsa -oStrictHostKeyChecking=no  solruser@$SOLR_NODE:solrlogs-${SOLR_NODE}.tar .
        zip logs-${NOW}.zip solrlogs*tar
     done

     echo_blue "Removing the hostname entry from ~/.ssh/known_hosts, so that another run can be possible afterwards"
     cd $ORIG_WORKING_DIR
     for line in `terraform output -state=terraform/terraform.tfstate -json solr_node_details|jq '.[] | .name'`
     do
        SOLR_NODE=${line//\"/}
        ssh-keygen -R "$SOLR_NODE"
     done
     ZK_NODE=`terraform output -state=terraform/terraform.tfstate -json zookeeper_details|jq '.[] | .name'`
     ssh-keygen -R "$ZK_NODE"
fi

# Results upload (results.json), if needed
cd $ORIG_WORKING_DIR
if [[ "null" != `jq -r '.["results-upload-location"]' $CONFIGFILE` ]]
then
     # Results uploading only supported for GCS buckets for now
     mv results.json results-${NOW}.json
     gsutil cp results-${NOW}.json `jq -r '.["results-upload-location"]' $CONFIGFILE`
     gsutil cp logs-${NOW}.zip `jq -r '.["results-upload-location"]' $CONFIGFILE`
fi

# Cleanup
if [ "terraform-gcp" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     cd $ORIG_WORKING_DIR/terraform
     terraform destroy --auto-approve
     rm id_rsa*
fi
if [ "vagrant" == `jq -r '.["cluster"]["provisioning-method"]' $CONFIGFILE` ];
then
     cd $ORIG_WORKING_DIR/vagrant
     vagrant destroy -f
     rm id_rsa*
fi
