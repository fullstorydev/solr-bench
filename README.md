# Solr Bench

A comprehensive Solr performance benchmark and stress test framework.

Benchmarking & stress test for standard operations (indexing, querying) for a specified Solr build.
 
## Running

### Prerequisites

If running on GCP, spin up a coordinator VM where this suite will run from. Make sure to use a service account that has permissions to create other VMs.

The VM should have the following:
* Maven and other tools for building `apt install wget unzip zip ant ivy lsof git netcat make openjdk-11-jdk maven jq` (For yum/dnf based systems, similar packages need to be installed)
* Terraform. `wget https://releases.hashicorp.com/terraform/0.12.26/terraform_0.12.26_linux_amd64.zip; sudo unzip terraform_0.12.26_linux_amd64.zip -d /usr/local/bin`

### Running the suite

#### Using a standard release of SolrJ client (8.8.0)

In the coordinator VM, check out this solr-bench repository.

     1. mvn clean compile assembly:single
     2. ./stress.sh <config-file>

Example: config.json (GCP), config-local.json (Local mode). For GCP, you need to modify the "terraform-gcp-config" to provide a valid "project_id".

#### Using a compiled version of SolrJ client (Example: reference_impl branch)

Compile the SolrJ and publish to local Maven:

    1. cd solr/solrj
    2. ../../gradlew publishJarsPublicationToBuildRepository
    3. mvn install:install-file -Dfile=./build/libs/solr-solrj-9.0.0-SNAPSHOT.jar -DgroupId=org.apache.solr -DartifactId=solr-solrj -Dversion=reference_impl -Dpackaging=jar -DgeneratePom=true

In the coordinator VM, check out this solr-bench repository.

     1. mvn -f pom-reference-branch.xml clean compile assembly:single
     2. ./stress.sh <config-file>

Example: config.json (GCP), config-local.json (Local mode). For GCP, you need to modify the "terraform-gcp-config" to provide a valid "project_id".

### Results

* Results are available after the benchmark in results-\<timestamp\>.json file.

### Datasets

* One can use either TSV files or JSONL files for indexing. Use "tsv" or "json" for the "file-format" section.
* The configset should be zipped, and "index-benchmarks" section should have the name of the file (without the .zip) as "configset".
* The query file should have GET parameters that will be queried against /select.

### Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory.
