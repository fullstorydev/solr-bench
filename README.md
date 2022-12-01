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

     1. mvn clean compile assembly:single
     2. ./cleanup.sh && ./stress.sh <config-file>

Example: `./cleanup.sh suites/cluster-test.json`

### Results

* Results are available after the benchmark in suites/results/results-\<configfile\>-\<commitid\>.json file.

### Datasets

TBD

### Examples

TBD

### Visualization (WIP)

TBD

## Acknowledgement
This started as a project funded by Google Summer of Code (SOLR-10317), later supported by FullStory.
