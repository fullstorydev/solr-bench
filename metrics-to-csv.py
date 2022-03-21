import json
import sys
from natsort import natsorted

startingTimestamp = 0

taskName = sys.argv[3]
metric = sys.argv[4]

with open(sys.argv[2]) as file:
    results = json.load(file)
    startingTimestamp = results[taskName][0]["start-time"]
  

#print (startingTimestamp)

output="Time"
with open(sys.argv[1]) as file:
    metrics = json.load(file)
    
    nodeNames = metrics.keys()
    metricNames = []
    numPoints = 0

    for node in nodeNames:
        metricNames = metrics[node].keys()
        numPoints = len(metrics[node][sorted(metricNames)[0]])
        break

    # header
    for n in natsorted(nodeNames):
        output += "," + n
    output += "\n"

    multiFactor = 1
    for i in range(numPoints):
        if (i*2 < startingTimestamp):
            continue
        line = str(int(i*2 - startingTimestamp))
        for n in natsorted(nodeNames):
            val = "-1"
            #print ("i is: "+str(i)+", len is "+len(results[n][metric]))
            if i < len(metrics[n][metric]):
                val = str(round(metrics[n][metric][i]*multiFactor, 2))
            line += "," + val
        output += line + "\n"

    print ("num points: "+str(numPoints))
   print(output)
    #print((resultsJson))
