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
See [this section](../../../../../../../../README.md#prometheus-exporter) for prometheus export details

### ScriptExecutionTask
A task to execute a script once with optional fields `script-params` (params to pass to the script) and `max-retry` (default to 0, retry up to this value for non-zero exit code, no retry on exception) , take note that concurrent or repeated executions are not currently supported
```
"my-script-type": {
  "task-by-class": {
    "task-class": "org.apache.solr.benchmarks.task.ScriptExecutionTask",
       "name": "My name",
       "params" : {
         "script": "./some-script.sh",
         "script-params": ["c01", true],
         "max-retry": 10
      }
    }
  }
}
```

### UrlCommandTask
Very similar to the "command" task, which sends a URL command to a solr node via GET request.

However, UrlCommandTask provides extra features:
1. Execute against a list of solr nodes based with optional node role filter, instead of a single random node.
2. Allow repeating the same URL "command" with concurrency, duration and rpm control like other tasks

#### Parameters
- `command` (mandatory) : The URL command, it is usually in format of `${SOLRURL}<endpoint>`. For example `${SOLRURL}admin/cores?action=STATUS`, take note that `SOLRURL` is the node base URL, which has trailing /
- `node-role` (optional) : The list of nodes to apply the command: `DATA`, `COORDINATOR` OR `OVERSEER`. All nodes if undefined
- `max-retry` (optional) : Retry up to this value for non 2XX status code. There is a 10 sec delay between each retry

#### Example
The below config will issue cores status request to each data node (in round-robin), at rate of 4 per min, for 10 minutes
```
"core-status-task": {
  "task-by-class": {
    "task-class": "org.apache.solr.benchmarks.task.UrlCommandTask",
    "name": "Periodically call core status",
    "params" : {
      "command" : "${SOLRURL}admin/cores?action=STATUS",
      "node-role": "DATA"
    },
    "min-threads": 1,
    "max-threads": 1,
    "rpm": 4,
    "duration-secs" : 600
  }
}
```

#### Remarks
If neither `rpm` nor `duration-secs` are defined, then it will assume the command be executed only once per node.

`rpm` is the total rate on all the nodes. Therefore, if there are 4 nodes, a 4 rpm would mean 1 per minute per node. With the current implementation, the requests are queued in round-robin manner, however slow processing on certain node could impact the whole threadpool which is shared for the task instance.

To issue request to only a single node, use static `command`. For example `http://my-solr-node:8983/solr/admin/cores?action=STATUS`   

This task supports Prometheus export with metric name `solr_bench_url_command_duration`, labels `command_url` and `http_status_code`, See [this section](../../../../../../../../README.md#prometheus-exporter) for prometheus export details for prometheus export details