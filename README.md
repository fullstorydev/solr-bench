# Solr Bench

A comprehensive Solr performance benchmark and stress test framework.

Benchmarking & stress test for standard operations (indexing, querying) for a specified Solr build.

## Benchmarking

### Prerequisites

If running on GCP, spin up a coordinator VM where this suite will run from. Make sure to use a service account that has permissions to create other VMs.

The VM should have the following:
* Maven and other tools for building `apt install wget unzip zip ant ivy lsof git netcat make openjdk-11-jdk maven jq` (For yum/dnf based systems, similar packages need to be installed)
* Terraform. `wget https://releases.hashicorp.com/terraform/0.12.26/terraform_0.12.26_linux_amd64.zip; sudo unzip terraform_0.12.26_linux_amd64.zip -d /usr/local/bin`

### Running the suite

In the coordinator VM, check out this solr-bench repository.

     1. mvn clean compile assembly:single
     2. ./cleanup.sh && ./start.sh <config-file>

Example: config.json (GCP), config-local.json (Local mode). For GCP, you need to modify the "terraform-gcp-config" to provide a valid "project_id".

### Results

* Results are available after the benchmark in results-\<timestamp\>.json file.

### Datasets

* One can use either TSV files or JSONL files for indexing. Use "tsv" or "json" for the "file-format" section.
* The configset should be zipped, and "index-benchmarks" section should have the name of the file (without the .zip) as "configset".
* The query file should have GET parameters that will be queried against /select.

## Stress tests

In the coordinator VM (GCP) or local machine (local mode), check out this solr-bench repository.

     1. mvn clean compile assembly:single
     2. ./cleanup.sh && ./stress.sh <config-file>

### Examples

* rolling-83.json: Local mode, for test of rolling restarts
* stress-ecommerce-local.json: Local mode, for time series events dataset. Please download the dataset from http://185.14.187.116/ecommerce-events.json.gz before running the test.
* delay-local.json: Local mode, for Airlines delays dataset. Please download the dataset from http://185.14.187.116/delay.json.gz before running the test.

### Visualization (WIP)

* `python2.7 -m SimpleHTTPServer`
* Open http://localhost:8000/plot-stress.html (to view the graph of the metrics of last performed test)


## Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory.
