import json
from git import Repo
import time
import os
import math


def getGraphData(testname, branch, repoFolder):
    repo = Repo(repoFolder)
    repo.git.checkout(branch, force=True)
    commits = repo.iter_commits(branch)

    graphData = ""

    for c in commits:
        #[ new Date(2314, 2, 15), 4, 'dfde16a004206cc92e21cc5a6cad9030fbe13c20',  7, 'dfde16a004206cc92e21cc5a6cad9030fbe13c20'],
        ts = time.strftime("%d %b %Y", time.gmtime(c.committed_date))
        tsGraph = time.strftime("new Date(%Y, %m - 1, %d, %H, %M, 0, 0)", time.gmtime(c.committed_date))
        
        resultsFilename = "suites/results/results-"+testname+"-"+str(c)+".json"
        taskTimes = []
        if (os.path.exists(resultsFilename)):
            results = json.load(open(resultsFilename))
            for task in results:
                start = math.inf
                end = 0
                for instance in results[task]:
                    start = min(start, instance["start-time"])
                    end   = max(end  , instance["end-time"])
                total = end - start
                taskTimes.append(total)
                #print("Time taken for " + task + ": "+str(total))
            if c.message.find("\n") == -1:
                len = 800
            else:
                len = c.message.find("\n")
            msg = c.message.replace("\n", "\t")[0: len].replace("'", "")
            #print("Message: "+msg)
            tooltip = ts +": " + str(c) + ": " + msg
            #print(tooltip)
            line = "[ " + tsGraph + ", " + str(taskTimes[0]) + ", '" +tooltip+"', " + str(taskTimes[1]) + ", '" + tooltip + "'],"
            graphData = graphData + line+"\n"
            #print(line)

        #print(ts + "\t\t"+str(c))
    return graphData

testname = "cluster-test.json"
branches = ["branch_9_1", "branch_9x"]
repoFolder = "SolrNightlyBenchmarksWorkDirectory/Download/solr-repository"

data = []

for branch in branches:
    data.append(getGraphData(testname, branch, repoFolder))

with open('graphTop.txt', 'r') as file:
    template = file.read()

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


with open('graphTemplate.txt', 'r') as file:
    template = file.read()

#print (template % (styles, snippets, divisions))
'''
with open('graphTop.txt', 'r') as file:
    top = file.read()
with open('graphBottom.txt', 'r') as file:
    bottom = file.read()

'''

with open(testname + ".html", "w") as text_file:
    text_file.write(template % (styles, snippets, divisions))
