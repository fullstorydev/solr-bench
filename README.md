# Solr Bench

A comprehensive Solr performance benchmark and stress test framework.

Benchmarking & stress test for standard operations (indexing, querying) for a Solr build
 
## Running

### Prerequisites

In the coordinator VM, check out the solr-benchmarking repo.

The VM should have the following:
* Maven and other tools for building `apt install wget unzip zip ant ivy lsof git netcat make openjdk-11-jdk jq` (For yum/dnf based systems, similar packages need to be installed)
* terraform. `wget https://releases.hashicorp.com/terraform/0.12.26/terraform_0.12.26_linux_amd64.zip; sudo unzip terraform_0.12.26_linux_amd64.zip -d /usr/local/bin`

### Running the suite

     1. mvn clean compile assembly:single
     2. ./start.sh <config-file>

(Example: config.json, config-local.json)

### Results

* Results are available after the benchmark in results.json file.

### Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory.