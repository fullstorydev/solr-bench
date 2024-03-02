## Introduction
This package contains Task (TaskType) implementation that is loaded by class name provided in the solr-bench config.

Take note that most of the common tasks are currently implemented and embedded in the BenchmarkMain.

## Usage
In the solr-bench config, for example under `task-types`, use `task-by-class` and specify the `task-class`:
```
"task-types": {
    "segment-monitor": {
        "task-by-class": {
            "task-class": "org.apache.solr.benchmarks.task.SegmentMonitoringTask",
            "name": "Monitor segment status in collection",
            "min-threads": 1,
            "max-threads": 1,
            "rpm": 1,
            "duration-secs" : 600,
            "params" : {
                "collection": "solr-bench-test"
            }
        }
    }
}
```


In the above example, a task of name `segment-monitor` would be created with the implementation of `SegmentMonitoringTask` with params `collection`. Take note the params are specific to the actual task.

Currently, all the config for `task-by-class` task type assumes all fields from [`BaseBenchmark`](../beans/BaseBenchmark.java) (name is always required. duration-secs is required if the task is finite) and would be executed using the `ControlledExecutor` with rate, duration and thread count control.

## Implementations
All the below class types are in package `org.apache.solr.benchmarks.task`, for example `org.apache.solr.benchmarks.task.SegmentMonitorTask` should be used as `task-class` in the config under `task-by-class`
### SegmentMonitorTask
A task to monitor segment count and doc count median per collection. The values will then be exposed via the Prometheus endpoint of solr-bench. Therefore, this task should be accompanied by the prometheus export config, ie
```
"prometheus-export": { "port": 1234 }
```
See [this section](../README.md#prometheus-exporter) for prometheus export details

### ScriptExecutionTask
A task to execute a script once with optional params (`script-params`), take note that concurrent or repeated executions are not currently supported 
```
"script": "some-script.sh"
"script-params": ["c01", "true"]
```
