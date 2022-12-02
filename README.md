# Solr Bench

A comprehensive Solr performance benchmark and stress test framework.

Benchmarking & stress test for standard operations (indexing, querying, collection operations, restarting nodes) or advanced tests (e.g. simulating GC pauses etc.) for a specified Solr build.

## Benchmarking

### Prerequisites

#### GNU/Linux

TBD

#### Mac OS

TBD

### Running the suite

In the coordinator VM, check out this solr-bench repository.

1. `mvn clean compile assembly:single`
2. `./cleanup.sh && ./stress.sh <config-file>`

Example: `./cleanup.sh ./stress.sh suites/cluster-test.json`

Multiple runs can be performed by editing the `<config-file>` and modifying the value `repository/commit-id` .



#### Available tests
```
note: This is subject to change
```

1. `cluster-test.json` : Creates an 8 node cluster and create 1000 collections of various `numShards` and measure shutdown & restart performance
2. --TODO add more--
### Results

* Results are available after the benchmark in `./suites/results/results-\<configfile\>-\<commit-id\>.json` file. 

### Datasets

TBD

### Examples

TBD

### Visualization (WIP)

Test results can be visualized in charts using the following command. 

`./python3 createGraph.py`

This will plot a graph into an html file that plots values of each test on a line chart `./suites/results/<config-file>.html`. The HTML file can be directly opened in a browser

####  dependencies on mac
 Mac requires a few tools to run this script. install the following 
1. `pip3 install gitpython`

## Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory.
