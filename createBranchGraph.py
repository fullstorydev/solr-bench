import json
import time
import os
import math
import collections
import argparse

parser = argparse.ArgumentParser(description='Description of your program')
parser.add_argument('-b','--branch', help='Branch to test on', required=True)
args = vars(parser.parse_args())

testnames = ["cluster-test.json", "stress-facets-local.json"]
branch = args['branch']

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

def get_commit_date(meta_file):
    props = load_properties(meta_file)
    return (int(props["committed_date"]))


def getGraphData(testname, branch):
    graphData = ""
    headerLine = ""

    meta_files = [f for f in os.listdir("suites/results/" + testname[:-5]) if
              os.path.isfile(os.path.join("suites/results/" + testname[:-5], f)) and f.startswith('meta-')]
    meta_files = list(map(lambda f: os.path.join("suites/results/" + testname[:-5], f), meta_files))
    meta_files.sort(key=get_commit_date)
    
    for m in meta_files:
        props = load_properties(m)
        c = m[len("meta-"):-1 * len(".json")][-40:]

        #[ new Date(2314, 2, 15), 4, 'dfde16a004206cc92e21cc5a6cad9030fbe13c20',  7, 'dfde16a004206cc92e21cc5a6cad9030fbe13c20'],
        ts = time.strftime("%d %b %Y", time.gmtime(int(props["committed_date"])))
        tsGraph = time.strftime("new Date(%Y, %m - 1, %d, %H, %M, 0, 0)", time.gmtime(int(props["committed_date"])))
        
        resultsFilename = "suites/results/"+testname[:-5]+"/results" + "-" + str(c) + ".json"
        configFilename  = "suites/results/"+testname[:-5]+"/configs" + "-" + str(c) + ".json"

        taskNames = []
        taskTimes = []

        if (os.path.exists(resultsFilename)):
            results = json.load(open(resultsFilename))
            for task in results:
                start = math.inf
                end = 0

                otherTimingsSums = collections.OrderedDict()
                otherTimingsCounts = collections.OrderedDict()

                for instance in results[task]:
                    start = min(start, instance["start-time"])
                    end   = max(end  , instance["end-time"])
                    instance = collections.OrderedDict(sorted(instance.items()))
                    for key in instance:
                        if key == "start-time" or key == "end-time" or key == "total-time" or key == "end-timestamp" or key == "init-timestamp" or key == "start-timestamp":
                            continue
                        if type(instance[key]) == list:
                            for subkeyindex in range(len(instance[key])):
                                for subkey in instance[key][subkeyindex]:
                                    if subkey[:12] == "validations-":
                                        continue
                                    compositekey = key+"_"+str(subkeyindex)+"_"+subkey
                                    if compositekey not in otherTimingsCounts.keys():
                                        otherTimingsCounts[compositekey] = 0
                                        otherTimingsSums[compositekey] = 0
                                    otherTimingsSums[compositekey] = otherTimingsSums[compositekey] + instance[key][subkeyindex][subkey]
                                    otherTimingsCounts[compositekey] = otherTimingsCounts[compositekey] + 1
                        else:
                            if key not in otherTimingsCounts.keys():
                                otherTimingsCounts[key] = 0
                                otherTimingsSums[key] = 0
                            otherTimingsSums[key] = otherTimingsSums[key] + instance[key]
                            otherTimingsCounts[key] = otherTimingsCounts[key] + 1
                #print("Sums: "+str(otherTimingsSums))
                #print("Counts: "+str(otherTimingsCounts))

                for key in otherTimingsSums:
                    taskNames.append(task + ": " + key)
                    taskTimes.append(otherTimingsSums[key] / otherTimingsCounts[key])

                total = end - start
                
                configs = json.load(open(configFilename))
                description = configs["execution-plan"][task]["description"]
                #print(description)
                taskNames.append(task + " (" + description + ")")
                taskTimes.append(total)
            if props["message"].find("\n") == -1:
                length = 800
            else:
                length = props["message"].find("\n")

            #print("Tasks: "+str(taskNames))
            #print("Tasks times: "+str(taskTimes))

            # chartData.addColumn('number', 'Task1'); chartData.addColumn({type:'string', role:'tooltip'});
            if len(taskNames) > 0:
                headerLine = "{type: 'date', label:'Commit date'},\n"
                for name in taskNames:
                    headerLine = headerLine + "{type: 'number', label: '"+name+"'}, {type: 'string', role:'tooltip'},\n"
                #print("Header line: " + headerLine)
            headerLine = "[\n" + headerLine + "]"           
            
            msg = props["message"].replace("\n", "\t")[0: length].replace("'", "")
            tooltip = str(c) + ": " + msg
            vals = ""
            for times in taskTimes:
                vals = vals + str(times) + ", '" +str(times)+": "+tooltip+"', " # + str(taskTimes[1]) + ", '" + str(taskTimes[1])+": "+tooltip + "'],"
            line = "[ " + tsGraph + ", " + vals + "],"
            graphData = graphData + line+"\n"
    #print("Before returning, headerLine="+headerLine)
    return headerLine, graphData

data = []
headers = []

for testname in testnames:
    headerLine, graphData = getGraphData(testname, branch)
    data.append(graphData)
    headers.append(headerLine)
print("Headers: "+str(headers))

styles = ""
for testname in testnames:
    underscored_name = testname.replace(".", "_").replace("-", "_")
    styles = styles + "#"+underscored_name+"  { width: 100%; height: 80%; }\n"

snippets = ""
for i in range(len(testnames)):
    testname = testnames[i]
    underscored_name = testname.replace(".", "_").replace("-", "_")

    graphData = data[i]

    snippets = snippets + "var %s_data = [ %s ];\n drawChart(\"%s (%s)\", \"%s\", %s_data);\n\n" % (underscored_name, graphData, underscored_name, underscored_name, underscored_name, underscored_name)
    
divisions = ""
for testname in testnames:
    underscored_name = testname.replace(".", "_").replace("-", "_")
    divisions = divisions + "<p><div id=\"%s\"></div></p>\n" % (underscored_name)



headerLine = ""
for h in headers:
    if h != "":
        headerLine = h

charts = ""
for i in range(len(testnames)):
    testname = testnames[i]
    underscored_name = testname.replace(".", "_").replace("-", "_")

    graphData = data[i]
    chartDataTemplate = "[ '%s', '%s', 'Commit date', 'Time (seconds)',\n %s ,\n [ %s ] ]"
    chartLine = chartDataTemplate % (underscored_name, underscored_name, headers[i], graphData)

    charts = charts + chartLine + ", \n"
    

with open('graphTemplate.txt', 'r') as file:
    template = file.read()

with open(branch + ".html", "w") as text_file:
    text_file.write(template % (styles, charts, divisions))
