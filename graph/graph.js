var chartDetailedStats = getValueFromParam('detailed-stats', false)

function drawAllCharts() {
    var allResultsByTaskName = {}
    var testnames;
    var groups = [] //groups are usually just branch names, but it could be some other customized values
    $.each(graph_data, function(group, dataByGroup) { //collect group names first. then figure out how many pages are there
       groups.push(group)
       testnames = Object.keys(dataByGroup);
    })


    $.each(graph_data, function(group, dataByGroup) {
        var $page = generatePage(group, dataByGroup.length == 1, dataByGroup)

        var testnames = Object.keys(dataByGroup);
        var resultsByTaskName = calculateResultsByTaskName(testnames, group, dataByGroup)
        // compute comparison data
        for (const testname of testnames) {
            $.each(resultsByTaskName[testname], function(taskName, resultsByTask) {
                var testTaskKey = taskName + " (" + testname + ")"
                if (!allResultsByTaskName[testTaskKey]) {
                    allResultsByTaskName[testTaskKey] = [].concat(resultsByTask)
                } else {
                    allResultsByTaskName[testTaskKey].push(...resultsByTask)
                }
            })
        }

        if (!getValueFromParam("expand", false)) { //plot collapsed graph for this group
            $.each(dataByGroup, function(testname, results) {
                drawChartInPageCollapsed(group, testname, results, $page);
            })
        } else { //plot graph for this group of this task
            for (const testname of testnames) {
                $.each(resultsByTaskName[testname], function(taskName, resultsByTask) {
                    drawChartInPage([group], taskName + " (" + testname + ")", resultsByTask, $page)
                })
            }
        }

        appendDetailsStatsTable(resultsByTaskName, $page)
    })

    //generate a graph that compare all groups/branches
    if (groups.length > 1) {
        var $page = generatePage(groups.join(' vs '), true)
        $.each(allResultsByTaskName, function(testTaskName, resultsByTask) {
            drawChartInPage(groups, testTaskName, resultsByTask, $page)
        })
        //have to refresh here, if the page is hidden on render then the graph dimension is rendered incorrectly...
        $page.siblings('.page').hide()
    }
}

const ChartTypes = {
	Simple: Symbol("Simple"),
	Percentile: Symbol("Percentile")
}

function generatePage(title, selected, dataByGroup) {
    var $page = $('<div class="page"></div>').appendTo($('#mainContainer'))

    // On a branch specific page, let a user collapse and expand the graphs
    if (title.indexOf(" vs ") == -1) {
        var $collapseButton = $('<button id="btn">Collapse</button>').appendTo($page)
        var $expandButton = $('<button id="btn">Expand</button>').appendTo($page)

        $collapseButton.click(function(data) {
            $page = $(this).parent()
            $page.find('div.graph').remove()
            var group = title;
            var testnames = Object.keys(dataByGroup);
            var resultsByTaskName = calculateResultsByTaskName(testnames, group, dataByGroup)
            $.each(dataByGroup, function(testname, results) {
                drawChartInPageCollapsed(group, testname, results, $page);
            })
        })

        $expandButton.click(function(data) {
            $page = $(this).parent()
            $page.find('div.graph').remove()
            var group = title;
            var testnames = Object.keys(dataByGroup);
            var resultsByTaskName = calculateResultsByTaskName(testnames, group, dataByGroup)
            //plot graph for this group of this task
            for (const testname of testnames) {
                $.each(resultsByTaskName[testname], function(taskName, resultsByTask) {
                    drawChartInPage([group], taskName + " (" + testname + ")", resultsByTask, $page)
                })
            }
        })
    }

    var $item = $('<div class="item clickable"><p>' + title + '</p></div>')
    if (selected) {
        $item.addClass('selected')
    }
    $item.click(function() {
        $(this).siblings().removeClass('selected')
        $(this).addClass('selected')
        $page.siblings('.page').hide()
        $page.show()
    })
    $('#sideBar').append($item)
    return $page
}

function appendDetailsStatsTable(dataByTestAndTaskName, $page) {
    var $table
    $.each(dataByTestAndTaskName, function(testName, dataByTaskName) {
        $.each(dataByTaskName, function(taskName, dataByCommit) {
                if (taskName.startsWith("detailed-stats-")) {
                    if (! $table) {
                        $table = $('<div class="detailed-stats-table"></div>').appendTo($page)
                        $tableHeader = $('<div style="display: table-header-group;"></div>').appendTo($table)
                        $tableHeader.append('<div style="display: table-cell; width: 70%;">Task</div>')
                        $tableHeader.append('<div style="display: table-cell; width: 10%;">Value</div>')
                        $tableHeader.append('<div style="display: table-cell; width: 10%;">Delta to previous run</div>')
                        $tableHeader.append('<div style="display: table-cell; width: 10%;">Delta to all runs median</div>')
                    }
                    taskName = taskName.substring("detailed-stats-".length) //still yike...
                    var allValues = []
                    $.each(dataByCommit, function(runI, data) {
                        var keyValue = getDetailStatsKeyValue(data)
                        if (keyValue != null) {
                            allValues.push(keyValue)
                        }
                    })


                    $tableRow = $('<div class="table-row"></div>').appendTo($table)
                    $tableRow.append('<div style="display: table-cell;">' + taskName + '</div>')
                    if (allValues.length >= 1) { //change from the run before this
                        var latestValue = allValues[allValues.length - 1]
                        $tableRow.append('<div style="display: table-cell;">' + latestValue + '</div>')
                        if (allValues.length >= 2) { //change from the run before this
                            var previousValue = allValues[allValues.length - 2]
                            var delta = latestValue - previousValue
                            $tableRow.append('<div style="display: table-cell;">' + getChangeText(previousValue, delta) + '</div>')
                            if (allValues.length >= 3) { //change from median of all runs
                                allValues.sort()
                                var median = allValues[allValues.length / 2]
                                var delta = latestValue - median
                                $tableRow.append('<div style="display: table-cell;">' + getChangeText(median, delta) + '</div>')
                            } else {
                                $tableRow.append('<div style="display: table-cell;" title="Not enough samples">-</div>')
                            }
                        } else {
                            $tableRow.append('<div style="display: table-cell;" title="Not enough samples">-</div>')
                        }
                    } else {
                        $tableRow.append('<div style="display: table-cell;">-</div>')
                    }
                }
            })
     })
}

function getDetailStatsKeyValue(data) {
    var fieldKey = detectFieldKey(data)
    if (data.result[fieldKey].length > 0) {
        //only take the first instance for now, to be honest, at this level I don't quite get what the list is anymore...
        var stats = data.result[fieldKey][0]
        if (stats.hasOwnProperty('50th')) {
            return stats['50th']
        } else if (stats.hasOwnProperty('count')){
           return stats['count']
        } else {
           console.warn("cannot find property for details stats for entry " + stats)
           return null
        }
    } else {
        return null
    }
}

function getChangeText(baseValue, delta) {
   var percentageChange = (baseValue > 0) ? delta * 100 / baseValue : 0
   var percentageChangeText = percentageChange >= 0 ? ('+' + percentageChange + '%') : (percentageChange + '%')
   return delta + '(' + percentageChangeText + ')'
}

function calculateResultsByTaskName(testnames, group, dataByGroup) {
    var resultsByTaskName = {}
    for (const testname of testnames) {
        resultsByTaskName[testname] = {}
        $.each(dataByGroup[testname], function(index, resultByCommit) {
            var commitMeta = {
                group: group,
                commitHash: resultByCommit.commit_hash,
                commitDate: resultByCommit.commit_date,
                commitMsg: resultByCommit.commit_msg
            }
            $.each(resultByCommit.results, function(taskName, resultByTask) {
                var resultsOfThisTask = resultsByTaskName[testname][taskName]
                if (!resultsOfThisTask) {
                    resultsOfThisTask = []
                    resultsByTaskName[testname][taskName] = resultsOfThisTask
                }
                var entry = {
                    commitMeta : commitMeta,
                    result: resultByTask[0]
                }
                resultsOfThisTask.push(entry)
            })
        })
    }
    return resultsByTaskName;
}

function detectChartType(graphData) {
    return (graphData['result']['timings'] || graphData['result']['percentile']) ? ChartTypes.Percentile : ChartTypes.Simple
}

function detectFieldKey(graphData) {
    if (graphData['result']['timings'])  {
      return 'timings'
    } else if (graphData['result']['percentile']) {
      return 'percentile'
    } else if (graphData['result']['error_count']) {
      return 'error_count'
    } else {
      return ''
    }
}

var graphIndex = 0
function drawChartInPage(groups, taskName, graphDataByCommit, $page) {
    if (taskName.startsWith("detailed-stats-") && !chartDetailedStats) { //not great! we should have better structure
        return
    }
    var elementId = 'graph_' + graphIndex++
    var $graphDiv = $('<div class="graph" id="' + elementId + '"></div>')
    $page.append($graphDiv)

    //TODO xaxis, yaxis
    var title = taskName + ' (' + groups.join(' vs ') + ')'
    var options = {
                    title: title,
                    pointSize: 5,
                    hAxis: {
                        title: 'Commit date',
                        titleTextStyle: {
                        color: '#333'
                        },
                        //slantedText: true,
                        //slantedTextAngle: 80
                    },
                    vAxis: {
                        minValue: 0
                    },
                    explorer: {
                        actions: ['dragToZoom', 'rightClickToReset'],
                        axis: 'horizontal',
                        keepInBounds: true,
                        maxZoomIn: 4.0
                    },
                    chartArea: {width: '70%'},
                };

    var data = new google.visualization.DataTable();
    var chartType = detectChartType(graphDataByCommit[0])
    var fieldKey = detectFieldKey(graphDataByCommit[0])
    var columns = []

    columns.push({type: 'date', id:'Commit date'})
    $.each(groups, function(index, group) {
        var suffix = ''
        if (groups.length > 1) {
            suffix = ' (' + group + ')'
        }
        //Property "visible" is NOT used by google chart, is it simply for us to track the column state
        if (chartType === ChartTypes.Simple) {
            if (fieldKey === '') {
                options['vAxis']['title'] = 'Time (seconds)'
                columns.push({type: 'number', label: "duration" + suffix, visible: true})
            } else {
                options['vAxis']['title'] = 'Value'
                columns.push({type: 'number', label: fieldKey + suffix, visible: true})
            }
            columns.push({type: 'string', role:'tooltip'})
        } else if (chartType === ChartTypes.Percentile) {
            columns.push({type: 'number', label: "median" + suffix, visible: true})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "p90" + suffix, visible: false})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "p95" + suffix, visible: false})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "mean" + suffix, visible: false})
            columns.push({type: 'string', role:'tooltip'})

            if (fieldKey === 'timings') {
                options['vAxis']['title'] = 'Time (milliseconds)'
            } else {
                options['vAxis']['title'] = 'Value'
            }
        }
    })

    //view column is used to control which column is visible, it's simply an array of either integer if visible (with value as the column index)
    //or a json object with calc function that returns null, this is super weird, but see
    //https://stackoverflow.com/questions/17444586/show-hide-lines-data-in-google-chart
    var viewColumns = []
    $.each(columns, function(index, column) {
        viewColumns.push(index)
        data.addColumn(column)
    })


    var rows = []
    $.each(graphDataByCommit, function(index, dataOfCommit) {
        if (dataOfCommit['result'] == undefined) return;
        var row = []
        var commitDate = new Date(0)
        commitDate.setUTCSeconds(dataOfCommit.commitMeta.commitDate);
        row.push(commitDate)
        //iterate through groups and pad if necessary, even if the same task were performed for each group, in order
        //to have different lines for each group, we have to treat tasks from each group as all unique
        $.each(groups, function(walkerBranchIndex, group) {
            if (group == dataOfCommit.commitMeta.group) {
                if (chartType === ChartTypes.Simple) {
                    if (fieldKey === '') { //no special field key, just calculate duration
                        var duration = dataOfCommit['result']['end-time'] - dataOfCommit['result']['start-time']
                        row.push(duration)
                        row.push(duration.toFixed(2) + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
                    } else {
                        var simpleValue = dataOfCommit['result'][fieldKey][0].count
                        row.push(simpleValue)
                        row.push(simpleValue + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
                    }
                } else if (chartType === ChartTypes.Percentile) {
                    var percentileResult = dataOfCommit['result'][fieldKey][0] //TODO first element only?
                    row.push(percentileResult['50th'])
                    row.push(percentileResult['50th'].toFixed(2) + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(percentileResult['90th'])
                    row.push(percentileResult['90th'].toFixed(2) + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(percentileResult['95th'])
                    row.push(percentileResult['95th'].toFixed(2) + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(percentileResult['mean'])
                    row.push(percentileResult['mean'].toFixed(2) + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
                }
            } else {
                columnPerBranchCount = chartType === ChartTypes.Simple ? 2 : 8
                for (i = 0 ; i < columnPerBranchCount ; i++) {
                    row.push(null)
                }
            }
        })

        rows.push(row)
    })


    // Add the rows
    data.addRows(rows);

    // Instantiate and draw our chart, passing in some options.
    var chart = new google.visualization.ChartWrapper({
        chartType: 'LineChart',
        containerId: elementId,
        dataTable: data,
        options: options
    });

    google.visualization.events.addListener(chart, 'select', showHideSeries)

    refreshViewAndSeries()

    function showHideSeries(target) {
        var sel = chart.getChart().getSelection();
        // if selection length is 0, we deselected an element
        if (sel.length > 0) {
            // if row is undefined, we clicked on the legend
            if (sel[0].row == null) {
                var selectedColIndex = sel[0].column;
                columns[selectedColIndex].visible = !columns[selectedColIndex].visible //flip the status
                refreshViewAndSeries()
            }
        }
    }

    //Refresh the view base on visibility
    function refreshViewAndSeries() {
        //regen line visibility (in dataView and colors in series)
        var series = []
        $.each(columns, function(index, column) {
            if (column.type == 'number') {
                if (column.visible) {
                    series.push({}) //use default color
                    viewColumns[index] = index //this shows the line
                } else {
                    series.push({ color : '#CCCCCC'})
                    viewColumns[index] = { //this hides the line
                        label: columns[index].label,
                        type: columns[index].type,
                        calc: function () {
                            return null;
                        }
                    };
                }
            }
        })

        dataView = chart.getView() || {};
        dataView.columns = viewColumns
        chart.setView(dataView)
        chart.setOption('series', series);
        chart.draw();
    }
}

function drawChartInPageCollapsed(group, testname, graphDataByCommit, $page) {
    if (graphDataByCommit.length == 0) return;

    var elementId = 'graph_' + graphIndex++
    var $graphDiv = $('<div class="graph" id="' + elementId + '"></div>')
    $page.append($graphDiv)

    //TODO xaxis, yaxis
    var title = testname
    var options = {
                    title: title,
                    hAxis: {
                        title: 'Commit date',
                        titleTextStyle: {
                        color: '#333'
                        },
                        //slantedText: true,
                        //slantedTextAngle: 80
                    },
                    vAxis: {
                        minValue: 0
                    },
                    explorer: {
                        actions: ['dragToZoom', 'rightClickToReset'],
                        axis: 'horizontal',
                        keepInBounds: true,
                        maxZoomIn: 4.0
                    },
                    chartArea: {width: '70%', height: '80%'},
                };

    var data = new google.visualization.DataTable();
    var columns = []

    columns.push({type: 'date', id:'Commit date'})

    // Collect the column names for the graph from the first commit
    for (const task in graphDataByCommit[0].results) {
        if (task.startsWith("detailed-stats-") && !chartDetailedStats) { //not great! we should have better structure
            continue
        }
        addColumn(columns, task, true);
        for (const i in graphDataByCommit[0].results[task]) {
            var taskData = graphDataByCommit[0].results[task][i]
            for (const subkey of Object.keys(taskData)) {
                if (subkey.endsWith("-time") || subkey.endsWith("-timestamp") || subkey=="heap-mb" || subkey=="status") continue;
                if (subkey == "timings") {
                    timings = taskData[subkey]
                    for (const timingSubkeyIdx of Object.keys(timings)) {
                        timingData = timings[timingSubkeyIdx]
                        for (var j=0; j<Object.keys(timingData).length; j++) {
                            var timingKey = Object.keys(timingData)[j]
                            var timingValue = timingData[timingKey]
                            if (["50th", "90th", "95th", "mean"].indexOf(timingKey) > -1) {
                                addColumn(columns, task + ": query_" + timingSubkeyIdx + "_" + timingKey, false)
                            }
                        }
                    }
                } else {
                    addColumn(columns, task + ": " + subkey, false)
                }
            }
            break;
        }
    }

    //view column is used to control which column is visible, it's simply an array of either integer if visible (with value as the column index)
    //or a json object with calc function that returns null, this is super weird, but see
    //https://stackoverflow.com/questions/17444586/show-hide-lines-data-in-google-chart
    var viewColumns = []
    $.each(columns, function(index, column) {
        viewColumns.push(index)
        data.addColumn(column)
    })

    var rows = []
    for (const i in graphDataByCommit) {
        var commit = graphDataByCommit[i]
        var row = []
        var commitDate = new Date(0)
        commitDate.setUTCSeconds(commit["commit_date"]);
        row.push(commitDate)

        var tooltipSuffix = commit["commit_hash"] + ": " + commit["commit_msg"]

        var results = commit["results"];
        for (const task in results) {
            if (task.startsWith("detailed-stats-") && !chartDetailedStats) { //not great! we should have better structure
                continue
            }
            var taskStart = Infinity, taskEnd = 0
            for (const i in results[task]) {
                var taskInstanceData = results[task][i]
                taskStart = Math.min(taskStart, taskInstanceData["start-time"])
                taskEnd = Math.max(taskEnd, taskInstanceData["end-time"])
            }
            row.push((taskEnd-taskStart))
            row.push((taskEnd-taskStart) + " secs: " + tooltipSuffix)

            // Apart from duration of the whole task, compute every other value as an
            // average of all such values from the task instances
            var totalValueAcrossInstances = {}
            var totalInstancesCount = {}

            for (const i in results[task]) {
                var taskInstanceData = results[task][i]
                for (const subkey of Object.keys(taskInstanceData)) {
                    if (subkey.endsWith("-time") || subkey.endsWith("-timestamp") || subkey=="heap-mb" || subkey=="status") continue;
                    if (subkey == "timings") {
                        timings = taskInstanceData[subkey]
                        for (const timingSubkeyIdx of Object.keys(timings)) {
                            timingData = timings[timingSubkeyIdx]
                            for (var j=0; j<Object.keys(timingData).length; j++) {
                                var timingKey = Object.keys(timingData)[j]
                                var timingValue = timingData[timingKey]
                                if (["50th", "90th", "95th", "mean"].indexOf(timingKey) > -1) {
                                    if (totalValueAcrossInstances["timing_"+timingSubkeyIdx+"_"+timingKey] == undefined) {
                                        totalValueAcrossInstances["timing_"+timingSubkeyIdx+"_"+timingKey] = timingValue
                                        totalInstancesCount["timing_"+timingSubkeyIdx+"_"+timingKey] = 1
                                    } else{
                                        totalValueAcrossInstances["timing_"+timingSubkeyIdx+"_"+timingKey] +=  + timingValue
                                        totalInstancesCount["timing_"+timingSubkeyIdx+"_"+timingKey]++
                                    }
                                }
                            }
                        }
                    } else {
                        if (totalValueAcrossInstances[subkey] == undefined) {
                            totalValueAcrossInstances[subkey] = taskInstanceData[subkey]
                            totalInstancesCount[subkey] = 1
                        } else{
                            totalValueAcrossInstances[subkey] = totalValueAcrossInstances[subkey] + taskInstanceData[subkey]
                            totalInstancesCount[subkey] = totalInstancesCount[subkey] + 1
                        }
                    }
                }
            }
            for (const key of Object.keys(totalValueAcrossInstances)) {
                var avg = totalValueAcrossInstances[key]/totalInstancesCount[key]
                row.push(avg)
                if (key.startsWith("timing_"))
                    row.push(avg + " ms: " + tooltipSuffix)
                else
                    row.push(avg + ": " + tooltipSuffix)
            }
        }    
        rows.push(row)
    }

    // Add the rows
    data.addRows(rows);

    // Instantiate and draw our chart, passing in some options.
    var chart = new google.visualization.ChartWrapper({
        chartType: 'LineChart',
        containerId: elementId,
        dataTable: data,
        options: options
    });

    google.visualization.events.addListener(chart, 'select', showHideSeries)

    refreshViewAndSeries()

    function showHideSeries(target) {
        var sel = chart.getChart().getSelection();
        // if selection length is 0, we deselected an element
        if (sel.length > 0) {
            // if row is undefined, we clicked on the legend
            if (sel[0].row == null) {
                var selectedColIndex = sel[0].column;
                columns[selectedColIndex].visible = !columns[selectedColIndex].visible //flip the status
                refreshViewAndSeries()
            }
        }
    }

    //Refresh the view base on visibility
    function refreshViewAndSeries() {
        //regen line visibility (in dataView and colors in series)
        var series = []
        $.each(columns, function(index, column) {
            if (column.type == 'number') {
                if (column.visible) {
                    series.push({}) //use default color
                    viewColumns[index] = index //this shows the line
                } else {
                    series.push({ color : '#CCCCCC'})
                    viewColumns[index] = { //this hides the line
                        label: columns[index].label,
                        type: columns[index].type,
                        calc: function () {
                            return null;
                        }
                    };
                }
            }
        })

        dataView = chart.getView() || {};
        dataView.columns = viewColumns
        chart.setView(dataView)
        chart.setOption('series', series);
        chart.draw();
    }
}

function addColumn(columns, columnName, visible) {
    columns.push({type: 'number', label: columnName, visible: visible})
    columns.push({type: 'string', role:'tooltip'})
}

function getValueFromParam(paramKey, defaultVal) {
    const urlSearchParams = new URLSearchParams(window.location.search)
    const params = Object.fromEntries(urlSearchParams.entries())
    return params[paramKey] === undefined ? defaultVal : params[paramKey]
}

google.load('visualization', '1', {packages: ['corechart'], callback: drawAllCharts});
