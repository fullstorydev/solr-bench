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
2. `./cleanup.sh && ./stress.sh -c <commit> -f <config-file>`

Example: `./cleanup.sh ./stress.sh -c dfde16a004206cc92e21cc5a6cad9030fbe13c20 -f suites/stress-facets-local.json`

    Usage: ./stress.sh -c <commit> [-g] [-v] -f <config-file>

| parameter | Required? | Description |
| ------- | ---------- | --------- |
|  -f | Required | Configuration file for a suite |
|  -c | Required | Commit point to run against |
|  -g | Optional | Generate validations file(s) for all query benchmark tasks |
|  -v | Optional | Perform validations based on specified validations file(s) |

    Note: Only -g or -v can be specified at a time

#### Available tests
```
Note: This is subject to change
```

1. `cluster-test.json` : Creates an 8 node cluster and create 1000 collections of various `numShards` and measure shutdown & restart performance
2. `stress-facets-local.json` : Indexes 20 million documents from an ecommerce events dataset, issues 5k facet queries against it.
3. `prs-vs-nonprs.json` : Creates 1k collections using non-PRS mode (default), then restarts 7 nodes, then cleans up, and repeats the same for PRS.

### Results

* Results are available after the benchmark in `./suites/results/<testname (without the .json suffix)>/<commit-id>/results.json` file.

### Datasets

TBD

### Validations

User workflow:

1. Run their suite with `-g` (generate validation) flag. This will generate a file in the `suites/` dir containing a tuple `<query, numFound, facets>`.
2. Manually verify the generated file (`suites/validations-<testname>-docs-<docs>-queries-<numQueries>.json`).
3. The validations file can used for validations in subsequent runs.

#### Using validations with a generated file

    a. Add a `"validation": "<file>"` parameter in the query-benchmark definition,
    b. Run `stress.sh` with a `-v` (validate) flag. It will use the validations file in the query benchmark task and report number of successful and failed queries.

The results would be reported in the query benchmark task, for example (500 validations succeeded, 0 failed):

    {
       threads=1,
       50th=8.2426775,
       90th=18.409747399999993,
       95th=28.618552849999993,
       mean=16.281752914583333,
       total-queries=480,
       total-time=14085,
       validations-succeeded=500,
       validations-failed=0
    }

### Visualization

To select/process the test results of specific branch or branch comparisons:
* `python3 graph-scripts/generate_graph_json.py -r suites/results/<test name> -b main` (only select tests with commits that belongs to `main` branch)
* `python3 graph-scripts/generate_graph_json.py -r suites/results/<test name> -b main...my-branch` (compare tests between `main` and `my-branch`)

```
Note: The `<test name>` currently is the test config file name without the `.json` file extension.

Note: Many result directories can be specified at once, in case multiple tests have been performed. Example: `python3 graph-scripts/generate_graph_json.py -r suites/results/stress-facets-local -r suites/results/cluster-test -b branch_9x...branch_9_1`
```

The script should generate `graph/graph-data.js`. Open `graph/graph.html`, it should show graphs grouped by branches and test tasks

####  Dependencies on mac
Mac OS requires a few tools to run this script. Install the following:

1. `brew install coreutils` 

## Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory.
