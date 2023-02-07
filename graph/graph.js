function drawAllCharts() {
    var allResultsByTaskName = {}
    var tags = [] //tags are usually just branch names, but it could be some other customized values
    $.each(graph_data, function(tag, dataByTag) { //collect tag names first. then figure out how many pages are there
       tags.push(tag)
    })

    $.each(graph_data, function(tag, dataByTag) {
        var $page = generatePage(tag, dataByTag.length == 1)

        var resultsByTaskName = {}
        $.each(dataByTag, function(index, resultByCommit) {
            var commitMeta = {
                tag: tag,
                commitHash: resultByCommit.commit_hash,
                commitDate: resultByCommit.commit_date,
                commitMsg: resultByCommit.commit_msg
            }
            $.each(resultByCommit.results, function(taskName, resultByTask) {
                var resultsOfThisTask = resultsByTaskName[taskName]
                if (!resultsOfThisTask) {
                    resultsOfThisTask = []
                    resultsByTaskName[taskName] = resultsOfThisTask
                }
                var entry = {
                    commitMeta : commitMeta,
                    result: resultByTask[0]
                }
                resultsOfThisTask.push(entry)
            })
        })
        //plot graph for this tag of this task
        $.each(resultsByTaskName, function(taskName, resultsByTask) {
            drawChartInPage([tag], taskName, resultsByTask, $page)
            if (!allResultsByTaskName[taskName]) {
                allResultsByTaskName[taskName] = [].concat(resultsByTask)
            } else {
                allResultsByTaskName[taskName].push(...resultsByTask)
            }
        })
    })

    //generate a graph that compare all tags/branches
    if (tags.length > 1) {
        var $page = generatePage(tags.join(' vs '), true)
        $.each(allResultsByTaskName, function(taskName, resultsByTask) {
            drawChartInPage(tags, taskName, resultsByTask, $page)
        })
        //have to refresh here, if the page is hidden on render then the graph dimension is rendered incorrectly...
        $page.siblings('.page').hide()
    }
}

const ChartTypes = {
	Simple: Symbol("Simple"),
	Percentile: Symbol("Percentile")
}

function generatePage(title, selected) {
    var $page = $('<div class="page"></div>').appendTo($('#mainContainer'))

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

function detectChartType(graphData) {
    return graphData['result']['timings'] ? ChartTypes.Percentile : ChartTypes.Simple
}

var graphIndex = 0
function drawChartInPage(tags, taskName, graphDataByCommit, $page) {
    var elementId = 'graph_' + graphIndex++
    var $graphDiv = $('<div class="graph" id="' + elementId + '"></div>')
    $page.append($graphDiv)

//TODO xaxis, yaxis
    var title = taskName + ' (' + tags.join(' vs ') + ')'
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
    var columns = []

    columns.push({type: 'date', id:'Commit date'})
    $.each(tags, function(index, tag) {
        var suffix = ''
        if (tags.length > 1) {
            suffix = ' (' + tag + ')'
        }
        //Property "visible" is NOT used by google chart, is it simply for us to track the column state
        if (chartType === ChartTypes.Simple) {
            columns.push({type: 'number', label: "duration" + suffix, visible: true})
            columns.push({type: 'string', role:'tooltip'})

            options['vAxis']['title'] = 'Time (seconds)'  //assume results are in sec, should improve this
        } else if (chartType === ChartTypes.Percentile) {
            columns.push({type: 'number', label: "median" + suffix, visible: true})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "p90" + suffix, visible: false})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "p95" + suffix, visible: false})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "mean" + suffix, visible: false})
            columns.push({type: 'string', role:'tooltip'})

            options['vAxis']['title'] = 'Time (milliseconds)' //assume results are in millisec, should improve this
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
        var row = []
        var commitDate = new Date(0)
        commitDate.setUTCSeconds(dataOfCommit.commitMeta.commitDate);
        row.push(commitDate)
        //iterate through tags and pad if necessary, even if the same task were performed for each tag, in order
        //to have different lines for each tag, we have to treat tasks from each tag as all unique
        $.each(tags, function(walkerBranchIndex, tag) {
            if (tag == dataOfCommit.commitMeta.tag) {
                if (chartType === ChartTypes.Simple) {
                    var duration = dataOfCommit['result']['end-time'] - dataOfCommit['result']['start-time']
                    row.push(duration)
                    row.push(duration.toFixed(2) + ' (' + tag + ') ' + dataOfCommit.commitMeta.commitMsg)
                } else if (chartType === ChartTypes.Percentile) {
                    var timingResult = dataOfCommit['result']['timings'][0] //TODO first element only?
                    row.push(timingResult['50th'])
                    row.push(timingResult['50th'].toFixed(2) + ' (' + tag + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(timingResult['90th'])
                    row.push(timingResult['90th'].toFixed(2) + ' (' + tag + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(timingResult['95th'])
                    row.push(timingResult['95th'].toFixed(2) + ' (' + tag + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(timingResult['mean'])
                    row.push(timingResult['mean'].toFixed(2) + ' (' + tag + ') ' + dataOfCommit.commitMeta.commitMsg)
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
                console.log(selectedColIndex)
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

google.load('visualization', '1', {packages: ['corechart'], callback: drawAllCharts});
