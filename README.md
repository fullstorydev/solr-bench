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

In the coordinator VM, check out this solr-bench repository.

     1. mvn clean compile assembly:single
     2. ./start.sh <config-file>

Example: config.json (GCP), config-local.json (Local mode). For GCP, you need to modify the "terraform-gcp-config" to provide a valid "project_id".

### Results

* Results are available after the benchmark in results-\<timestamp\>.json file.

### Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory team.
