# Solr Bench

A comprehensive Solr performance benchmark and stress test framework.

Benchmarking & stress test for standard operations (indexing, querying) for a specified Solr build.
 
## Running

### Prerequisites

If running on GCP, spin up a coordinator VM where this suite will run from. Make sure to use a service account that has permissions to create other VMs.

The VM should have the following:
* Maven and other tools for building `apt install wget unzip zip ant ivy lsof git netcat make openjdk-11-jdk maven jq` (for Ubuntu/Debian) or `sudo yum install wget unzip zip ant ivy lsof git nc make java-11-openjdk-devel maven jq` (for Redhat/CentOS/Fedora)
* Terraform. `wget https://releases.hashicorp.com/terraform/0.12.26/terraform_0.12.26_linux_amd64.zip; sudo unzip terraform_0.12.26_linux_amd64.zip -d /usr/local/bin`
* Vagrant. Ensure that virtualization is enabled in your BIOS. Install the pre-requisites: `sudo yum install VirtualBox vagrant ansible`. Run `VBoxManage --version` to make sure VirtualBox is installed properly (it may prompt you to install kernel drivers, just follow as suggested there). To adjust the instance memory, edit the vagrant/Vagrant file.

### Running the suite

In the coordinator VM, check out this solr-bench repository.

     1. mvn clean compile assembly:single
     2. ./start.sh <config-file>

Example: config.json (GCP), config-local.json (Local mode, it builds Solr from source), config-prebuilt (Local mode, uses provided tgz file). For GCP, you need to modify the "terraform-gcp-config" to provide a valid "project_id". For local, the JDK is downloaded but not used and instead the system installed JDK/JRE is used.

### Results

* Results are available after the benchmark in results-\<timestamp\>.json file.

### Datasets

* One can use either TSV files or JSONL files for indexing. Use "tsv" or "json" for the "file-format" section.
* The configset should be zipped, and "index-benchmarks" section should have the name of the file (without the .zip) as "configset".
* The query file should have GET parameters that will be queried against /select.
* Lucene's benchmarks dataset can be found here:  http://people.apache.org/~mikemccand/enwiki-20120502-lines-1k.txt.lzma

### Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory.
