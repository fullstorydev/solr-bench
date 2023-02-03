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
parser.add_argument('-r', '--result-dir', help='Directory that contains the json result files', required=True)
parser.add_argument('-o', '--output',
                    help='Output path of the graph json. If undefined it will be saved as the working dir with name '
                         '<test_name>.json ',
                    required=False)
parser.add_argument('-b', '--branches',
                    help='Result for a single branch <branch> or compare branches in format of <branch1>...<branch2>',
                    required=True)
args = vars(parser.parse_args())

result_dir = args['result_dir']
logging.info("Reading results from dir: " + result_dir)
target_branches = None
if args.get("branches") is not None:
    target_branches = args['branches'].split('...')
    logging.info("Comparing branches: " + str(target_branches))


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
    def __init__(self, branch, commit_hash, commit_date, commit_msg, results):
        self.branch = branch
        self.commit_hash = commit_hash
        self.commit_date = commit_date
        self.commit_msg = commit_msg
        self.results = results

    # def add_timing(self, key, timing):
    #     self.task_timing[key] = timing

    def __str__(self):
        return "Branch: %s Hash: %s Commit Date: %s Commit Msg: %s Results: %s" % (
        self.branch, self.commit_hash, self.commit_date, self.commit_msg, str(self.results))

    def __repr__(self):
        return str(self)

def parse_benchmark_results(result_paths):
    benchmark_results = []

    for result_path in result_paths:
        result_dir = os.path.dirname(result_path)
        file_id = os.path.basename(result_path)[len("results-"):-1 * len(".json")]
        logging.info("File ID: " + file_id)
        meta_path = os.path.join(result_dir, "meta-" + file_id + ".prop")
        logging.info("Meta file: " + meta_path)
        props = load_properties(meta_path)
        logging.info("Loaded props" + str(props))

        # commit_date = time.gmtime(int(props["committed_date"]))
        commit_hash = props["commit"]
        commit_date = int(props["committed_date"])
        commit_msg = props["message"]

        try:
            json_results = json.load(open(result_path))
        except OSError as e:
            logging.warning(f"Skipping {file_id}. Unable to open {result_path}: {e}")
            continue

        benchmark_result = BenchmarkResult(branch, commit_hash, commit_date, commit_msg, json_results)
        benchmark_results.append(benchmark_result.__dict__)

    return benchmark_results


def get_committed_date(props):
    return int(props["committed_date"])


class BranchTaskKey:
    def __init__(self, branch, task_key):
        self.branch = branch
        self.task_key = task_key

    def __eq__(self, other):
        return (self.branch, self.task_key) == (other.branch, other.task_key)

    def __ne__(self, other):
        return not (self == other)

    def __lt__(self, other):
        return (self.branch, self.task_key) < (other.branch, other.task_key)

    def __repr__(self):
        return f"{self.task_key}({self.branch})"

    def __hash__(self):
        # necessary for instances to behave sanely in dicts and sets.
        return hash((self.branch, self.task_key))



branches = []
benchmark_results = collections.OrderedDict()  # key as branch name
test_name = os.path.splitext(os.path.basename(result_dir))[0]

meta_files = [f for f in os.listdir(result_dir) if
              os.path.isfile(os.path.join(result_dir, f)) and f.startswith('meta-')]

for branch in target_branches:
    meta_files = [f for f in os.listdir(result_dir) if
                  os.path.isfile(os.path.join(result_dir, f)) and f.startswith('meta-')]
    meta_props = []
    result_paths = []
    for meta_file in meta_files:
        props = load_properties(os.path.join(result_dir, meta_file))
        if "branches" not in props or branch not in props["branches"].split(','):
            logging.debug(f'skipping {meta_file} for branch {branch}')
            continue
        file_id = meta_file[len("meta-"):-1 * len(".json")]
        props["file_id"] = file_id
        meta_props.append(props)

    # now sort the props by commit date
    meta_props.sort(key=get_committed_date)
    for props in meta_props:
        result_paths.append(os.path.join(result_dir, f'results-{props["file_id"]}.json'))

    benchmark_results[branch] = parse_benchmark_results(result_paths)


output_path = None
if args.get("output") is not None:
    output_path = args['output']
else:
    output_path = "graph/graph-data.js"
with open(output_path, "w") as output_file:
    output_file.write("var graph_data=" + json.dumps(benchmark_results, indent=4))

logging.info(f'Processed results {output_path} generated')