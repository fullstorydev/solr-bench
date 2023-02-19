import copy

import json
import time
import os
import math
import collections
import argparse
import logging

logging.basicConfig(level=logging.INFO)
parser = argparse.ArgumentParser(description='Description of your program')
parser.add_argument('-r', '--result-dir',
                    help='Directory that contains the json result dirs/files. Can repeat this '
                         'multiple times for plotting more than one test in the graphs', required=True, action='append')
parser.add_argument('-o', '--output',
                    help='Output path of the graph json. If undefined it will be saved as the working dir with name '
                         '<test_name>.json ',
                    required=False)
parser.add_argument('-b', '--compare-groups',
                    help='Chart results that matches a single group <group> or compare different groups in format of '
                         '<group 1>...<group 2>. Groupings are usually just branch names, '
                         'for example "main" or "master...branch_8x"',
                    required=True)
args = vars(parser.parse_args())

result_dirs = args['result_dir']
logging.info("Reading results from dir: " + str(result_dirs))
target_groups = args['compare_groups'].split('...')
logging.info("Comparing groups: " + str(target_groups))

def load_properties(filepath, sep='=', comment_char='#'):
    """
    Read the file passed as parameter as a properties file.
    """
    props = {}
    with open(filepath, "rt") as f:
        for line in f:
            l = line.strip()
            if l and not l.startswith(comment_char):
                key_value = l.split(sep)
                key = key_value[0].strip()
                value = sep.join(key_value[1:]).strip().strip('"')
                props[key] = value
    return props


element_keys = []


def get_element_id(key):
    if key not in element_keys:
        element_keys.append(key)
    return f'element_{element_keys.index(key)}'


class BenchmarkResult:
    def __init__(self, commit_hash, commit_date, commit_msg, test_date, results):
        self.commit_hash = commit_hash
        self.commit_date = commit_date
        self.commit_msg = commit_msg
        self.test_date = test_date
        self.results = results

    # def add_timing(self, key, timing):
    #     self.task_timing[key] = timing

    def __str__(self):
        return "Hash: %s Commit Date: %s Commit Msg: %s Test Date: %s Results: %s" % (
        self.commit_hash, self.commit_date, self.commit_msg, self.test_date, str(self.results))

    def __repr__(self):
        return str(self)

def parse_benchmark_results(meta_props):
    benchmark_results = []

    for meta_prop in meta_props:
        try:
            test_run_dir = meta_prop["test_run_dir"]
            # commit_date = time.gmtime(int(props["committed_date"]))
            commit_hash = meta_prop["commit"]
            commit_date = int(meta_prop["commit_date"])
            commit_msg = meta_prop["message"]
            test_date = meta_prop["test_date"]

            result_path = os.path.join(test_run_dir, "results.json")
            json_results = json.load(open(result_path))
        except OSError as e:
            logging.warning(f"Skipping meta data parsing. Unable to open {result_path}: {e}")
            continue
        except KeyError as e:
            logging.warning(f"Skipping meta data parsing for {result_path}. KeyError: {e}")
            continue
        except BaseException as e:
            logging.warning(f"Skipping meta data parsing for {result_path}. Unexpected exception: {e}")
            continue

        benchmark_result = BenchmarkResult(commit_hash, commit_date, commit_msg, test_date, json_results)
        benchmark_results.append(benchmark_result.__dict__)

    return benchmark_results


def get_commit_date(props):
    return int(props["commit_date"])

benchmark_results = collections.OrderedDict()  # key as group, which usually is just the branch name

for group in target_groups:
    for result_dir in result_dirs:
        test_name = os.path.splitext(os.path.basename(result_dir))[0]
        meta_files = [f for f in os.listdir(result_dir) if
            os.path.isfile(os.path.join(result_dir, f)) and f.startswith('meta-')]

        test_run_dirs = [f for f in os.listdir(result_dir) if
                    os.path.isdir(os.path.join(result_dir, f))]
        meta_props = []
        result_paths = []
        for test_run_base_dir in test_run_dirs:
            test_run_dir = os.path.join(result_dir, test_run_base_dir)
            try:
                props = load_properties(os.path.join(test_run_dir, "meta.prop"))
            except OSError as e:
                logging.warning(f'failed to open meta.prop in {test_run_dir}. Skipping...')
                continue
            if "groups" not in props or group not in props["groups"].split(','):
                logging.debug(f'skipping {test_run_dir} for group {group}')
                continue
            props["test_run_dir"] = test_run_dir
            meta_props.append(props)

        # now sort the props by commit date
        meta_props.sort(key=get_commit_date)

        if group not in benchmark_results.keys():
            benchmark_results[group] = collections.OrderedDict()
        benchmark_results[group][test_name] = parse_benchmark_results(meta_props)


output_path = None
if args.get("output") is not None:
    output_path = args['output']
else:
    output_path = "graph/graph-data.js"
with open(output_path, "w") as output_file:
    output_file.write("var graph_data=" + json.dumps(benchmark_results, indent=4))

logging.info(f'Processed results {output_path} generated')