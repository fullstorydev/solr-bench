import datetime

import json
import numbers
import string
import os
import argparse
import logging
import csv

logging.basicConfig(level=logging.INFO)
parser = argparse.ArgumentParser(description='Description of your program')
parser.add_argument('-r', '--result-dir',
                    help='Directory that contains the json result dirs/files. Can repeat this '
                         'multiple times for converting multiple source json results', required=True, action='append')
parser.add_argument('-o', '--output',
                    help='Output folder of the csv files. If undefined it will be saved in the working dir with name '
                         '<test execution time>-<groups>-<run #>-<metric type>.csv ',
                    required=False)
args = vars(parser.parse_args())

result_dirs = args['result_dir']
logging.info("Reading results from dir: " + str(result_dirs))


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


class DetailedQueryStats:
    def __init__(self, commit_hash, commit_date, commit_msg, test_date, groups, stats):
        self.commit_hash = commit_hash
        self.commit_date = commit_date
        self.commit_msg = commit_msg
        self.test_date = test_date
        self.groups = groups
        self.stats = stats

    def __str__(self):
        return "Hash: %s Commit Date: %s Commit Msg: %s Test Date: %s stats: %s" % (
            self.commit_hash, self.commit_date, self.commit_msg, self.test_date, str(self.stats))

    def __repr__(self):
        return str(self)


metric_type_results_key: dict[str, str] = {
    'DURATION': 'timings',
    'DOC_HIT_COUNT': 'percentile',
    'ERROR_COUNT': 'error_count'
}


def extract_query_detailed_stats(meta_prop):
    detailed_stats = []
    try:
        test_run_dir = meta_prop["test_run_dir"]
        # commit_date = time.gmtime(int(props["committed_date"]))
        # commit_hash = meta_prop.get("commit", '')
        # commit_date = int(meta_prop.get("commit_date", '0'))
        # commit_msg = meta_prop.get("message", '')
        # test_date = meta_prop.get("test_date", '')
        # groups = meta_prop.get("groups", "")

        result_path = os.path.join(test_run_dir, "results.json")
        json_results = json.load(open(result_path))

        is_detailed_stats_run = False
        for key, stats_arr in json_results.items():
            if key.startswith('detailed-stats'):
                # not sure what exactly is this array - different task instances?
                for stats in stats_arr:
                    metric_type = stats['metricType']
                    stat_entry = {'taskName': stats['taskName'], 'query': stats['query'],
                                  "metricType": metric_type, 'results': stats[metric_type_results_key[metric_type]]}

                    detailed_stats.append(stat_entry)
    except OSError as e:
        logging.warning(f"Skipping meta data parsing. Unable to open {result_path}: {e}")
    except KeyError as e:
        logging.warning(f"Skipping meta data parsing for {result_path}. KeyError: {e}")
    except BaseException as e:
        logging.warning(f"Skipping meta data parsing for {result_path}. Unexpected exception: {e}")

    return detailed_stats


def extract_stat_entry(stat_entry, prefix, results_per_execution):
    for key, value in results_per_execution.items():
        if prefix != "":
            key = prefix + "." + key
        if isinstance(value, numbers.Number):
            stat_entry[key] = value
        elif isinstance(value, dict):
            extract_stat_entry(stat_entry, key, value)

def extract_generic_stats(meta_prop):
    generic_stats = {}
    try:
        test_run_dir = meta_prop["test_run_dir"]

        result_path = os.path.join(test_run_dir, "results.json")
        json_results = json.load(open(result_path))

        for key, results_per_task in json_results.items():
            task_name = key
            run_stats = []
            for i, results_per_execution in enumerate(results_per_task):
                # logging.info(f"key: {key} {i} results_per_execution: {results_per_execution}")
                stat_entry = {}
                extract_stat_entry(stat_entry, "", results_per_execution)
                run_stats.append(stat_entry)
            generic_stats[task_name] = run_stats
    except OSError as e:
        logging.warning(f"Skipping meta data parsing. Unable to open {result_path}: {e}")
    except BaseException as e:
        logging.warning(f"Skipping meta data parsing for {result_path}. Unexpected exception: {e}")
    return generic_stats




def get_commit_date(props):
    return int(props.get("commit_date", '0'))


# benchmark_results = collections.OrderedDict()  # key as group, which usually is just the branch name

def sanitize_filename(input):
    valid_chars = "-_%s%s" % (string.ascii_letters, string.digits)
    # removing any invalid characters
    return ''.join(c for c in input if c in valid_chars)

output_path = None
if args.get("output") is not None:
    output_path = args['output']
else:
    output_path = ""


def export_to_query_details_csv(output_key, stats):

    # Each element in stats array is results on a query, but the result for that
    # (either timings, percentile (for doc hit count) or error_count) are in an array.
    # Each element in that array is actually the result for a task run on n threads (a single run can execute the same
    # query many times though), which n goes from min-threads to max-threads in benchmark settings.
    #
    # Usually min-threads is equal to max-threads, which means the task is only run once.
    #
    # In case if max-threads > min-threads, then we will export to multiple CSV file, one for task run on n threads

    # First figure out and validate # of runs based on the result array size. And group the elements by metricType
    run_count = 0
    stats_by_metric_type = {}
    generated = False
    for i, stats_entry in enumerate(stats):
        if run_count == 0:
            run_count = len(stats_entry['results'])
        elif run_count != len(stats_entry['results']):
            raise Exception(
                f"inconsistent result array size found. Expect {run_count} but found {len(stats_entry['results'])} at {stats_entry}")
        metric_type = stats_entry['metricType']
        stats_of_this_metric_type = None
        if metric_type not in stats_by_metric_type:
            stats_of_this_metric_type = []
            stats_by_metric_type[metric_type] = stats_of_this_metric_type
        else:
            stats_of_this_metric_type = stats_by_metric_type[metric_type]
        stats_of_this_metric_type.append(stats_entry)

    for run in range(run_count):
        for metric_type, stats in stats_by_metric_type.items():
            if len(stats) == 0 or len(stats[0]['results']) == 0:
                continue
            generated = True
            output_file_name = sanitize_filename(output_key + "-" + str(run + 1)) + "-" + metric_type + ".csv"
            output_file_path = os.path.join(output_path, output_file_name)
            with open(output_file_path, 'w', newline='') as csvfile:
                # Define the field names
                field_names = ['taskName', 'query', 'metricType']
                # Use the breakdown field from first stats entry, assuming all entries are consistent
                for result_breakdown_field in stats[0]['results'][run]:
                    field_names.append(result_breakdown_field)  # 50th, 99th, max etc

                # Create a CSV writer
                writer = csv.DictWriter(csvfile, fieldnames=field_names, escapechar='\\', quoting=csv.QUOTE_ALL)

                # Write the header
                writer.writeheader()

                # Write rows
                for entry_by_query in stats:
                    row = {
                        'taskName': entry_by_query['taskName'],
                        'query': entry_by_query['query'],
                        'metricType': entry_by_query['metricType']
                    }
                    for result_breakdown_field, val in entry_by_query['results'][run].items():
                        row[result_breakdown_field] = val
                    writer.writerow(row)

                logging.info(f"Finished writing to {os.path.abspath(output_file_path)}")
    return generated

def export_to_generic_stats_csv(output_key, stats):

    # Each element in stats array is results on a query, but the result for that
    # (either timings, percentile (for doc hit count) or error_count) are in an array.
    # Each element in that array is actually the result for a task run on n threads (a single run can execute the same
    # query many times though), which n goes from min-threads to max-threads in benchmark settings.
    #
    # Usually min-threads is equal to max-threads, which means the task is only run once.
    #
    # In case if max-threads > min-threads, then we will export to multiple CSV file, one for task run on n threads

    # First figure out and validate # of runs based on the result array size. And group the elements by metricType
    run_count = 0
    stats_by_metric_type = {}
    for task, runs in stats.items():
        if len(runs) == 0:
            logging.debug(f"No runs found for task {task} for {output_key}. Skipping export")
            continue
        output_file_name = sanitize_filename(output_key + "-" + task) + ".csv"
        output_file_path = os.path.join(output_path, output_file_name)
        with open(output_file_path, 'w', newline='') as csvfile:
            # Define the field names
            field_names = []
            # Use the breakdown field from first stats entry, assuming all entries are consistent
            for field_name in runs[0]:
                field_names.append(field_name)

            # Create a CSV writer
            writer = csv.DictWriter(csvfile, fieldnames=field_names, escapechar='\\', quoting=csv.QUOTE_ALL)

            # Write the header
            writer.writeheader()

            # Write rows
            for kvs in runs:
                row = {}
                for key, val in kvs.items():
                    row[key] = val
                writer.writerow(row)
            logging.info(f"Finished writing to {os.path.abspath(output_file_path)}")

for result_dir in result_dirs:
    test_name = os.path.splitext(os.path.basename(result_dir))[0]
    test_run_dirs = [f for f in os.listdir(result_dir) if
                     os.path.isdir(os.path.join(result_dir, f))]
    meta_props = []
    result_paths = []
    for test_run_base_dir in test_run_dirs:
        test_run_dir = os.path.join(result_dir, test_run_base_dir)

        results_file = os.path.join(test_run_dir, "results.json")

        if os.path.isfile(results_file) is False:
            logging.warning("Results file not found: " + results_file)
            continue

        try:
            props = load_properties(os.path.join(test_run_dir, "meta.prop"))
        except OSError as e:
            logging.warning(f'failed to open meta.prop in {test_run_dir}. Skipping...')
            continue

        if "groups" not in props:
            logging.debug(f'skipping {test_run_dir} es groups prop not found')
            continue
        props["test_run_dir"] = test_run_dir
        meta_props.append(props)

    # now sort the props by commit date
    meta_props.sort(key=get_commit_date)

    # stats_by_meta = {}
    for meta_prop in meta_props:
        date = datetime.datetime.fromtimestamp(int(meta_prop.get("test_date")))
        output_key = date.strftime('%Y-%m-%d-%H-%M-%S') + '-' + meta_prop.get("groups", "")
        # output_file_name = sanitize_filename(output_key) + ".csv"
        query_stats = extract_query_detailed_stats(meta_prop)
        generated_query_csv = export_to_query_details_csv(output_key + "-query-details", query_stats)

        if not generated_query_csv:
            generic_stats = extract_generic_stats(meta_prop)
            export_to_generic_stats_csv(output_key, generic_stats)

        # export_to_generic_csv(output_key, stats)
        # logging.info(f'{stats}')

