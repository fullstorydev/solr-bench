# Solr Bench

A comprehensive Solr performance benchmark and stress test framework.

Benchmarking & stress test for standard operations (indexing, querying, collection operations, restarting nodes) or advanced tests (e.g. simulating GC pauses etc.) for a specified Solr build.

## Benchmarking

### Prerequisites

#### GNU/Linux

##### Local Mode

Ubuntu/Debian:

    # Install JDK 11, make sure it is the default JDK. Following is a potential way to install the right JDK:
    sudo apt install openjdk-11-jdk

    sudo apt install wget unzip zip ant ivy lsof git netcat make maven jq
    
Fedora/RHEL:

    # Install JDK 11, make sure it is the default JDK. Following is a potential way to install the right JDK:
    sudo yum install  java-11-openjdk-devel

    sudo yum install wget unzip zip ant ivy lsof git nc make maven jq


##### GCP Mode
If running on GCP, spin up a coordinator VM where this suite will run from. Make sure to use a service account for that VM that has permissions to create other VMs.

The VM should have the following:
* Maven and other tools for building `apt install wget unzip zip ant ivy lsof git netcat make openjdk-11-jdk maven jq` (for Ubuntu/Debian) or `sudo yum install wget unzip zip ant ivy lsof git nc make java-11-openjdk-devel maven jq` (for Redhat/CentOS/Fedora)
* Terraform. `wget https://releases.hashicorp.com/terraform/0.12.26/terraform_0.12.26_linux_amd64.zip; sudo unzip terraform_0.12.26_linux_amd64.zip -d /usr/local/bin`


#### Mac OS

TBD (PRs welcome!)

### Running the suite

In the coordinator VM, check out this solr-bench repository.

1. `mvn clean compile assembly:single`
2. `./cleanup.sh && ./stress.sh -c <commit> <config-file>`

Example: `./cleanup.sh ./stress.sh -c dfde16a004206cc92e21cc5a6cad9030fbe13c20 suites/stress-facets-local.json`


#### Available tests
```
note: This is subject to change
```

1. `cluster-test.json` : Creates an 8 node cluster and create 1000 collections of various `numShards` and measure shutdown & restart performance
2. `stress-facets-local.json` : Indexes 20 million documents from an ecommerce events dataset, issues 5k facet queries against it.

### Results

* Results are available after the benchmark in `./suites/results/results-\<configfile\>-\<commit-id\>.json` file. 

### Datasets

TBD

### Visualization (WIP)

Test results can be visualized in charts using the following command. 

`./python3 createGraph.py`

This will plot a graph into an html file that plots values of each test on a line chart `./suites/results/<config-file>.html`. The HTML file can be directly opened in a browser

### Visualization (Multi-branch)

To select/process the test results of specific branch or branch comparisons:
`python3 graph-scripts/generate_graph_json.py -r suites/results/<test name> -b main` (only select tests with commits that belongs to `main` branch)
`python3 graph-scripts/generate_graph_json.py -r suites/results/<test name> -b main...my-branch` (compare tests between `main` and `my-branch`)

Take note that `<test name>` currently is the test config file name w/o the file extension

The script should generate `graph/graph-data.js`. Open `graph/graph.html`, it should show graphs grouped by branches and test tasks


####  dependencies on mac
Mac OS requires a few tools to run this script. Install the following:

1. `brew install coreutils` 
2. `pip3 install gitpython`

## Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory.
