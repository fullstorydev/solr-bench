import json
from git import Repo
import time
import os
import math
import collections

testname = "cluster-test.json"
branches = ["branch_9x", "branch_9_1"]
repoFolder = "SolrNightlyBenchmarksWorkDirectory/Download/solr-repository"

def getGraphData(testname, branch, repoFolder):
    repo = Repo(repoFolder)
    repo.git.checkout(branch, force=True)
    commits = repo.iter_commits(branch)

    graphData = ""
    headerLine = ""

    for c in commits:
        #[ new Date(2314, 2, 15), 4, 'dfde16a004206cc92e21cc5a6cad9030fbe13c20',  7, 'dfde16a004206cc92e21cc5a6cad9030fbe13c20'],
        ts = time.strftime("%d %b %Y", time.gmtime(c.committed_date))
        tsGraph = time.strftime("new Date(%Y, %m - 1, %d, %H, %M, 0, 0)", time.gmtime(c.committed_date))
        
        resultsFilename = "suites/results/results-" + testname + "-" + str(c) + ".json"
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
                        if key == "start-time" or key == "end-time" or key == "total-time":
                            continue
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
                
                taskNames.append(task)
                taskTimes.append(total)
            if c.message.find("\n") == -1:
                length = 800
            else:
                length = c.message.find("\n")

            #print("Tasks: "+str(taskNames))
            #print("Tasks times: "+str(taskTimes))

            # chartData.addColumn('number', 'Task1'); chartData.addColumn({type:'string', role:'tooltip'});
            if len(taskNames) > 0:
                headerLine = "{type: 'date', id:'Commit date'},\n"
                for name in taskNames:
                    headerLine = headerLine + "{type: 'number', id: '"+name+"'}, {type: 'string', role:'tooltip'},\n"
                #print("Header line: " + headerLine)
            headerLine = "[\n" + headerLine + "]"           
            
            msg = c.message.replace("\n", "\t")[0: length].replace("'", "")
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

for branch in branches:
    headerLine, graphData = getGraphData(testname, branch, repoFolder)
    data.append(graphData)
    headers.append(headerLine)
print("Headers: "+str(headers))

styles = ""
for branch in branches:
    styles = styles + "#"+branch+"  { width: 100%; height: 80%; }\n"

snippets = ""
for i in range(len(branches)):
    branch = branches[i]
    graphData = data[i]

    snippets = snippets + "var %s_data = [ %s ];\n drawChart(\"%s (%s)\", \"%s\", %s_data);\n\n" % (branch, graphData, testname, branch, branch, branch)
    
divisions = ""
for branch in branches:
    divisions = divisions + "<p><div id=\"%s\"></div></p>\n" % (branch)



headerLine = ""
for h in headers:
    if h != "":
        headerLine = h

charts = ""
for i in range(len(branches)):
    branch = branches[i]
    graphData = data[i]
    chartDataTemplate = "[ '%s', '%s', 'Commit date', 'Time (seconds)',\n %s ,\n [ %s ] ]"
    chartLine = chartDataTemplate % (branch, branch, headerLine, graphData)

    charts = charts + chartLine + ", \n"
    

with open('graphTemplate.txt', 'r') as file:
    template = file.read()

with open(testname + ".html", "w") as text_file:
    text_file.write(template % (styles, charts, divisions))
