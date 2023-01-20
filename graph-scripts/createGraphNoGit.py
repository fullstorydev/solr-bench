import copy

import json
import time
import os
import math
import collections
import argparse


parser = argparse.ArgumentParser(description='Description of your program')
parser.add_argument('-r','--result-dir', help='Directory that contains the json result files', required=True)
parser.add_argument('-b','--branches', help='Result for a single branch <branch> or compare branches in format of <branch1>...<branch2>', required=True)
args = vars(parser.parse_args())

result_dir = args['result_dir']
print("Reading results from dir: "+result_dir)
target_branches = None
if args.get("branches") is not None:
    target_branches = args['branches'].split('...')
    print("Comparing branches: "+str(target_branches))

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


class BenchmarkResult:
    def __init__(self, branch, commit_hash, commit_date, commit_msg):
        self.branch = branch
        self.commit_hash = commit_hash
        self.commit_date = commit_date
        self.commit_msg = commit_msg
        self.task_timing = collections.OrderedDict()

    def add_timing(self, key, timing):
        self.task_timing[key] = timing

    def __str__(self):
        return "Branch: %s Hash: %s Commit Date: %s Commit Msg: %s Task Timing Dic %s" %(self.branch, self.commit_hash, self.commit_date, self.commit_msg, str(self.task_timing))

    def __repr__(self):
        return str(self)


def parse_benchmark_results(result_paths):
    benchmark_results = []
    for result_path in result_paths:
        result_dir = os.path.dirname(result_path)
        commit_hash = os.path.basename(result_path)[len("results-"):-1*len(".json")]
        print("Hash: " + commit_hash)
        meta_path=os.path.join(result_dir,"meta-"+commit_hash+".prop")
        print("Meta file: " + meta_path)
        props = load_properties(meta_path)
        print("Loaded props" + str(props))

        commit_date = time.gmtime(int(props["committed_date"]))
        commit_msg = props["message"]

        benchmark_result = BenchmarkResult(branch, commit_hash, commit_date, commit_msg)

        json_results = json.load(open(result_path))
        for task in json_results:
            start = math.inf
            end = 0
            other_timings_sums = collections.OrderedDict()
            other_timings_counts = collections.OrderedDict()

            for instance in json_results[task]:
                instance = collections.OrderedDict(sorted(instance.items()))
                start = min(start, instance["start-time"])
                end = max(end, instance["end-time"])
                for key in instance:
                    if key == "start-time" or key == "end-time" or key == "total-time":
                        continue

                    if type(instance[key]) == list:
                        for subkey_index in range(len(instance[key])):
                            for subkey in instance[key][subkey_index]:
                                composite_key = key+"_"+str(subkey_index)+"_"+subkey
                                if composite_key not in other_timings_counts.keys():
                                    other_timings_counts[composite_key] = 0
                                    other_timings_sums[composite_key] = 0
                                other_timings_sums[composite_key] = other_timings_sums[composite_key] + instance[key][subkey_index][subkey]
                                other_timings_counts[composite_key] = other_timings_counts[composite_key] + 1
                    elif type(instance[key]) == int and not key.endswith('-timestamp'):
                        if key not in other_timings_counts.keys():
                            other_timings_counts[key] = 0
                            other_timings_sums[key] = 0
                        other_timings_sums[key] = other_timings_sums[key] + instance[key]
                        other_timings_counts[key] = other_timings_counts[key] + 1
            #print("Sums: "+str(other_timings_sums))
            #print("Counts: "+str(other_timings_counts))

            for key in other_timings_sums:
                benchmark_result.add_timing(task + ": " + key, other_timings_sums[key] / other_timings_counts[key])

            #add total . Cannot take total-time directly as there could be several instances for the same tests
            total = end - start
            benchmark_result.add_timing(task, total)

        benchmark_results.append(benchmark_result)

    return benchmark_results


def generate_chart_data(branch, benchmark_results : list[BenchmarkResult]):
    headers = ["{type: 'date', label: 'Commit date'}"]
    rows = []
    first_result = True
    for benchmark_result in benchmark_results:
        # first item is the commit date
        row_items = [ time.strftime("new Date(%Y, %m - 1, %d, %H, %M, 0, 0)", benchmark_result.commit_date) ]
        # then alternate between the actual data and tooltip
        for key in benchmark_result.task_timing:
            if first_result:
                headers.append("{type: 'number', label: '%s'}" % key)
                headers.append("{type: 'string', role: 'tooltip'}")
            row_items.append(str(benchmark_result.task_timing[key]))
            tooltip_text = f"'{benchmark_result.task_timing[key]} : {benchmark_result.branch}/{benchmark_result.commit_msg}'"
            row_items.append(tooltip_text)
        rows.append(f"[ {', '.join(row_items)} ]")
        first_result = False

    headers_str = ', \n'.join(headers)
    rows_str = ', \n'.join(rows)

    chart_data_template = "[ '%s', '%s', 'Commit date', 'Time (seconds)',\n [ %s ] ,\n [ %s ] ]"
    chart_data = chart_data_template % (branch, branch, headers_str, rows_str)

    print("Final chart data (new) " + chart_data)
    return chart_data


branches= []

benchmark_results = collections.OrderedDict() #key as branch name
test_name=os.path.splitext(os.path.basename(result_dir))[0]

#branchDirs = [f for f in os.listdir(resultFolder) if os.path.isdir(os.path.join(resultFolder, f))]

meta_files = [f for f in os.listdir(result_dir) if os.path.isfile(os.path.join(result_dir, f)) and f.startswith('meta-')]

def get_committed_date(props):
    return int(props["committed_date"])

for branch in target_branches:
    print("branch: "+branch)
    meta_files = [f for f in os.listdir(result_dir) if os.path.isfile(os.path.join(result_dir, f)) and f.startswith('meta-')]
    meta_props = []
    result_paths = []
    for meta_file in meta_files:
        props = load_properties(os.path.join(result_dir, meta_file))
        if branch not in props["branches"].split(','):
            print("skipping " + meta_file)
            continue
        commit_hash = meta_file[len("meta-"):-1*len(".json")]
        props["hash"] = commit_hash
        meta_props.append(props)

    #now sort the props by commit date
    meta_props.sort(key=get_committed_date)
    for props in meta_props:
        result_paths.append(os.path.join(result_dir, f'results-{props["hash"]}.json'))

    benchmark_results[branch] = parse_benchmark_results(result_paths)
    print("\n".join(map(str,benchmark_results[branch])))
    branches.append(branch)


# if target_branches is not None:  # Try to combine the graphs
#     combined_branch_name = "-vs-".join(branches)
#     print("using new branch name " + combined_branch_name)
#     branches.clear()
#     branches.append(combined_branch_name)
#     new_data = []
#     for graph_points in data:
#         new_data = new_data + graph_points
#     data.clear()
#     data.append(new_data)


styles = ""
for branch in branches:
    styles = styles + "#"+branch+"  { width: 100%; height: 80%; }\n"

divisions = ""
for branch in branches:
    divisions = divisions + "<p><div id=\"%s\"></div></p>\n" % (branch)

class BranchTaskKey:
    def __init__(self, branch, task_key):
        self.branch = branch
        self.task_key = task_key

    def __eq__(self, other):
        return ((self.branch, self.task_key) == (other.branch, other.task_key))

    def __ne__(self, other):
        return not (self == other)

    def __lt__(self, other):
        return ((self.branch, self.task_key) < (other.branch, other.task_key))

    def __repr__(self):
        return f"{self.task_key}({self.branch})"

    def __hash__(self):
    # necessary for instances to behave sanely in dicts and sets.
        return hash((self.branch, self.task_key))



def merge_benchmark_results(benchmark_results):
    #combined_name = '+'.join(benchmark_results.keys())

    branch_task_keys = collections.OrderedDict()

    #collect all task key combination with branch
    for branch in benchmark_results:
        for benchmark_result in benchmark_results[branch]:
            for task_key in benchmark_result.task_timing:
                branch_task_keys[BranchTaskKey(branch, task_key)] = None

    new_results = []
    for branch in benchmark_results:
        branch_task_timing = collections.OrderedDict() #key is <task key>-<branch>
        original_results: list[BenchmarkResult] = benchmark_results[branch]
        for original_result in original_results:
            for branch_task_key in branch_task_keys:
                if branch_task_key.branch == branch and branch_task_key.task_key in original_result.task_timing:
                    branch_task_timing[str(branch_task_key)] = original_result.task_timing[branch_task_key.task_key]
                else:
                    branch_task_timing[str(branch_task_key)] = 'null' #pad

            # for key in original_result.task_timing:
            #     branch_key == f"{key}({branch})"
            #     branch_task_timing[new_key] = original_result.task_timing[key]
            # #pad key/values from other branch result
            # for other_branch in benchmark_results:
            #     if branch == other_branch:
            #         continue
            #     if len(benchmark_results[other_branch]) > 0:
            #         for key in benchmark_results[other_branch][0].task_timing:
            #             new_key = f"{key}({other_branch})"
            #             branch_task_timing[new_key] = 'null'  #pad
            new_result = copy.copy(original_result)
            new_result.task_timing = branch_task_timing
            new_results.append(new_result)

    return new_results

charts = ""
for branch in benchmark_results:
    chart_data = generate_chart_data(branch, benchmark_results[branch])
    charts = charts + chart_data + ", \n"

#Generate one more graph with combined results
if len(branches) > 1:
    combined_branch_name = '_vs_'.join(target_branches)
    combined_benchmark_results = merge_benchmark_results(benchmark_results)

    styles = styles + "#"+combined_branch_name+"  { width: 100%; height: 80%; }\n"
    divisions = divisions + "<p><div id=\"%s\"></div></p>\n" % (combined_branch_name)
    charts = charts + generate_chart_data(combined_branch_name, combined_benchmark_results)


with open('graphTemplate.txt', 'r') as file:
    template = file.read()

with open(test_name + ".html", "w") as text_file:
    text_file.write(template % (styles, charts, divisions))




