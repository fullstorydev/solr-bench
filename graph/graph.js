var chartDetailedStats = getValueFromParam('all-stats', false)

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

        appendDetailsStatsTable(resultsByTaskName, $page, group)

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


    })

    //generate a graph that compare all groups/branches
    if (groups.length > 1) {
        var $page = generatePage(groups.join(' vs '), true)
        appendDetailsSummaryTable(allResultsByTaskName, $page, groups)

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

var loadedStatsRowsByGroup = {}
var loadedSummaryRows = []

function appendDetailsStatsTable(dataByTestAndTaskName, $page, group) {
    var $table
    var $tableContainer
    var loadedStatsRows = []
    $.each(dataByTestAndTaskName, function(testName, dataByTaskName) {
        $.each(dataByTaskName, function(taskName, dataByCommit) {
            if (taskName.startsWith("detailed-stats-")) {
                if (! $table) {
                    $tableContainer = $('<div class="section" style="width: 90%; margin: 10px auto; font-size:12px;"></div>').appendTo($page)
//                        $tableContainer.append('<h4 style="margin: 0 0 10px 0;">Stats</h4>')
                     //a header table, this only contains the header so scrolling the actual table will keep the header
                    $headerTable = $('<div class="detailed-stats-table-header-only" style="display: table; width: 100%;"></div>').appendTo($tableContainer)
                    $tableHeader = $('<div style="display: table-header-group;"></div>').appendTo($headerTable)
                    $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="task" data-sort-order="descending" onclick="toggleStatsTableSort($(this))">Task</div>')
                    $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="metricType" data-sort-order="descending" onclick="toggleStatsTableSort($(this))">Metric</div>')
                    $tableHeader.append('<div class="clickable" style="display: table-cell; width: 45%;" data-sort-property="query" data-sort-order="descending" onclick="toggleStatsTableSort($(this))">Query</div>')
                    $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="latestValue" data-sort-order="ascending" onclick="toggleStatsTableSort($(this))">Value</div>')
                    $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="previousDeltaPercentage" data-sort-order="ascending" onclick="toggleStatsTableSort($(this))">Delta to previous run</div>')
                    $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="medianDeltaPercentage" data-sort-order="ascending" onclick="toggleStatsTableSort($(this))">Delta to all runs median</div>')
                    $tableHeader.append('<div style="display: table-cell; width: 5%;"></div>')

                    //actual data table, the header row is for controlling the widths
                    var $dataTableContainer = $('<div style="max-height:150px; overflow-y:auto; width: 100%;"></div>').appendTo($tableContainer)
                    $table = $('<div class="detailed-stats-table table" style="display: table; width: 100%;  table-layout: fixed;"></div>').appendTo($dataTableContainer)
                    $tableHeader = $('<div style="display: table-header-group;"></div>').appendTo($table)
                    $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                    $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                    $tableHeader.append('<div style="display: table-cell; width: 45%;"></div>')
                    $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                    $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                    $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                    $tableHeader.append('<div style="display: table-cell; width: 5%;"></div>')

                    $table.data('group', group)
                }
                taskName = taskName.substring("detailed-stats-".length) //still yike...
                var allValues = []
                $.each(dataByCommit, function(runI, data) {
                    var keyValue = getDetailStatsKeyValue(data)
                    if (keyValue != null) {
                        allValues.push(keyValue)
                    }
                })

                var statsRow = {}
                statsRow.taskName = dataByCommit[0].result.taskName
                statsRow.query = dataByCommit[0].result.query
                statsRow.metricType = dataByCommit[0].result.metricType
                if (allValues.length >= 1) { //change from the run before this
                    var latestValue = allValues[allValues.length - 1]
                    statsRow.latestValue = latestValue
//                          $tableRow.append('<div style="display: table-cell;">' + latestValue + '</div>')
                    if (allValues.length >= 2) { //change from the run before this
                        var previousValue = allValues[allValues.length - 2]
                        var delta = latestValue - previousValue
                        statsRow.previousValue = previousValue
                        statsRow.previousDelta = delta
                        statsRow.previousDeltaPercentage = delta * 100 / previousValue

                        if (allValues.length >= 3) { //change from median of all runs
                            allValues.sort()
                            var median = allValues[Math.floor(allValues.length / 2)]
                            var delta = latestValue - median
                            statsRow.median = median
                            statsRow.medianDelta = delta
                            statsRow.medianDeltaPercentage = delta * 100 / median
                        }
                    }
                }
                statsRow.results = dataByCommit
                loadedStatsRows.push(statsRow)
            }
       })
   })
    if ($table) {
        loadedStatsRows = sortPreserveOrder(loadedStatsRows, "previousDeltaPercentage", false) // sort by previous delta first (if median delta not available or equal)
        loadedStatsRowsByGroup[group] = loadedStatsRows
        updateStatsTable($table, "medianDeltaPercentage", "descending") //then sort by median delta

        var $download = $("<a href='#'>DOWNLOAD CSV</a>").appendTo($tableContainer)
        $download.click(function() {
            exportToCsv(loadedStatsRows)
        })
    }
}

function appendDetailsSummaryTable(allResultsByTaskName, $page, groups) {
    var $table, $tableContainer
    var loadedStatsRows = []
    $.each(allResultsByTaskName, function(taskName, dataByCommit) {
        if (taskName.startsWith("detailed-stats-")) {
            if (! $table) {
                $tableContainer = $('<div class="section" style="width: 90%; margin: 10px auto; font-size:12px;"></div>').appendTo($page)
//                        $tableContainer.append('<h4 style="margin: 0 0 10px 0;">Stats</h4>')
                 //a header table, this only contains the header so scrolling the actual table will keep the header
                $headerTable = $('<div class="detailed-stats-table-header-only" style="display: table; width: 100%;"></div>').appendTo($tableContainer)
                $tableHeader = $('<div style="display: table-header-group;"></div>').appendTo($headerTable)
                $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="task" data-sort-order="descending" onclick="toggleSummaryTableSort($(this))">Task</div>')
                $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="metricType" data-sort-order="descending" onclick="toggleSummaryTableSort($(this))">Metric</div>')
                $tableHeader.append('<div class="clickable" style="display: table-cell; width: 45%;" data-sort-property="query" data-sort-order="descending" onclick="toggleSummaryTableSort($(this))">Query</div>')
                $.each(groups, function(index, group) {
                    $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="' + group + '-median" data-sort-order="ascending" onclick="toggleSummaryTableSort($(this))">' + group + ' median</div>')
                })
                if (groups.length == 2) {
                    $tableHeader.append('<div class="clickable" style="display: table-cell; width: 10%;" data-sort-property="deltaPercentage" data-sort-order="ascending" onclick="toggleSummaryTableSort($(this))">Delta</div>')
                }
                $tableHeader.append('<div style="display: table-cell; width: 5%;"></div>')


                //actual data table, the header row is for controlling the widths
                var $dataTableContainer = $('<div style="max-height:150px; overflow-y:auto; width: 100%;"></div>').appendTo($tableContainer)
                $table = $('<div class="summary-table table" style="display: table; width: 100%;  table-layout: fixed;"></div>').appendTo($dataTableContainer)
                $tableHeader = $('<div style="display: table-header-group;"></div>').appendTo($table)
                $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                $tableHeader.append('<div style="display: table-cell; width: 45%;"></div>')
                $.each(groups, function(index, group) {
                    $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                })
                if (groups.length == 2) {
                  $tableHeader.append('<div style="display: table-cell; width: 10%;"></div>')
                }
                $tableHeader.append('<div style="display: table-cell; width: 5%;"></div>')
                $table.data("groups", groups)
            }
            taskName = taskName.substring("detailed-stats-".length) //still yike...
            var allValuesByGroup = {}
            $.each(dataByCommit, function(runI, data) {
                var keyValue = getDetailStatsKeyValue(data)
                var group = data.commitMeta.group
                if (keyValue != null) {
                    var allValuesOfThisGroup = allValuesByGroup[group]
                    if (! allValuesOfThisGroup) {
                        allValuesOfThisGroup = []
                        allValuesByGroup[group] = allValuesOfThisGroup
                    }
                    allValuesOfThisGroup.push(keyValue)
                }
            })

            var summaryRow = {}
            summaryRow.taskName = dataByCommit[0].result.taskName
            summaryRow.query = dataByCommit[0].result.query
            summaryRow.metricType = dataByCommit[0].result.metricType
            var mediansByGroup = []
            $.each(allValuesByGroup, function(group, values) {
                var medianOfThisGroup = values[Math.floor(values.length / 2)]
                summaryRow[group + '-median'] = medianOfThisGroup
                mediansByGroup.push(medianOfThisGroup)
            })
            if (mediansByGroup.length == 2) {
                summaryRow.delta = mediansByGroup[1] - mediansByGroup[0]
                summaryRow.deltaPercentage = summaryRow.delta * 100 / mediansByGroup[0]
            }

            summaryRow.results = dataByCommit

            loadedSummaryRows.push(summaryRow)
        }
   })
    if ($table) {
        updateSummaryTable($table) //then sort by median delta
        var $download = $("<a href='#'>Download CSV</a>").appendTo($tableContainer)
        $download.click(function() {
            exportToCsv(loadedSummaryRows)
        })
    }
}

function toggleStatsTableSort(sortHeader) {
	if (sortHeader.data("sort-order") == "ascending") {
		sortHeader.data("sort-order", "descending")
	} else {
		sortHeader.data("sort-order", "ascending")
	}

	sortHeader.siblings().removeClass("selected")
	sortHeader.addClass("selected")

	updateStatsTable(sortHeader.closest('.page').find('.detailed-stats-table'), sortHeader.data("sort-property"), sortHeader.data("sort-order"))
}

function toggleSummaryTableSort(sortHeader) {
	if (sortHeader.data("sort-order") == "ascending") {
		sortHeader.data("sort-order", "descending")
	} else {
		sortHeader.data("sort-order", "ascending")
	}

	sortHeader.siblings().removeClass("selected")
	sortHeader.addClass("selected")

	updateSummaryTable(sortHeader.closest('.page').find('.summary-table'), sortHeader.data("sort-property"), sortHeader.data("sort-order"))
}

function updateStatsTable($table, sortProperty, sortOrder) {
	$table.children("div.table-row").remove()

	//sort the list
	//loadedLinks.sort(sortByProperty(sortProperty, sortOrder == "ascending"))
	var group = $table.data('group')
	var loadedStatsRows = loadedStatsRowsByGroup[group]
	loadedStatsRows = sortPreserveOrder(loadedStatsRows, sortProperty, sortOrder == "ascending")
	loadedStatsRowsByGroup[group] = loadedStatsRows //has to update the map as the returned array is new

	$.each(loadedStatsRows, function(index, statsRow) {
        var $tableRow = $('<div class="table-row clickable"></div>').appendTo($table)
        $tableRow.append('<div style="display: table-cell;">' + statsRow.taskName + '</div>')
        $tableRow.append('<div style="display: table-cell;">' + statsRow.metricType + '</div>')
        var $queryCell = $('<div style="display: table-cell;"><div class="query-text" style="max-height: 15px; overflow: hidden;">' + statsRow.query + '</div></div>').appendTo($tableRow)
        $queryCell.attr('title', statsRow.query)
        addRowClickListener($tableRow, $queryCell)

        if (statsRow.latestValue !== undefined) {
            $tableRow.append('<div style="display: table-cell; text-align:right;">' + statsRow.latestValue.toFixed(2) + '</div>')
        } else {
            $tableRow.append('<div style="display: table-cell; text-align:right;">-</div>')
        }
        if (statsRow.previousDeltaPercentage !== undefined) {
            $tableRow.append('<div style="display: table-cell; text-align:right;">' + getChangeText(statsRow.previousValue, statsRow.previousDelta) + '</div>')
        } else {
            $tableRow.append('<div style="display: table-cell; text-align:right;">-</div>')
            $tableRow.attr('title', 'Need at least 2 runs')
        }
        if (statsRow.medianDeltaPercentage !== undefined) {
            $tableRow.append('<div style="display: table-cell; text-align:right;">' + getChangeText(statsRow.median, statsRow.medianDelta) + '</div>')
        } else {
            $tableRow.append('<div style="display: table-cell; text-align:right;">-</div>')
            $tableRow.attr('title', 'Need at least 3 runs')
        }
        var $viewCell = $('<div style="display: table-cell; text-align:right;"><a href="#">View</a></div>')
        $viewCell.click(function() {
            $('#graphModal').show()
            $('#graphModal .graphCanvas').empty()
            drawChartInPage([group], statsRow.taskName, statsRow.results, $('#graphModal .graphCanvas'))
        })

        $tableRow.append($viewCell)
        $table.append($tableRow)
	});
}

function exportToCsv(rows) {
    var csvData = []
    var keys = []
    $.each(rows[0], function(key, value) {
        key = key.replace(/"/g, '""');
        if (key.indexOf(',') >= 0) {
            key = `"${key}"`
        }
       keys.push(key)
    })
    csvData.push(keys.join(','))

    $.each(rows, function(i, row) {
        var values = []
        $.each(row, function(key, value) {
            if (typeof value === 'string') {
             value = value.replace(/"/g, '""');
             if (value.indexOf(',') >= 0) {
                value = `"${value}"`
             }
            } else if (typeof value === 'number' && !Number.isInteger(value)) {
                value = value.toFixed(2)
            }
            values.push(value)
        })
        var rowString = values.join(',')
        csvData.push(rowString)
    })

  const dataUri = 'data:text/csv;charset=utf-8,' + encodeURIComponent(csvData.join('\n'))
  const linkElement = document.createElement('a')
  linkElement.setAttribute('href', dataUri)
  linkElement.setAttribute('download', "export.csv")
  linkElement.click()
}

function updateSummaryTable($table, sortProperty, sortOrder) {
	$table.children("div.table-row").remove()

	//sort the list
	if (sortProperty) {
	    loadedSummaryRows = sortPreserveOrder(loadedSummaryRows, sortProperty, sortOrder == "ascending")
    }

	$.each(loadedSummaryRows, function(index, summaryRow) {
        var $tableRow = $('<div class="table-row clickable"></div>').appendTo($table)
        $tableRow.append('<div style="display: table-cell;">' + summaryRow.taskName + '</div>')
        $tableRow.append('<div style="display: table-cell;">' + summaryRow.metricType + '</div>')
        var $queryCell = $('<div style="display: table-cell;"><div class="query-text" style="max-height: 15px; overflow: hidden;">' + summaryRow.query + '</div></div>').appendTo($tableRow)
        $queryCell.attr('title', summaryRow.query)

        addRowClickListener($tableRow, $queryCell)

        var groups = $table.data('groups')
        $.each(groups, function(index, group) {
            if (summaryRow[group + '-median']) {
                $tableRow.append('<div style="display: table-cell; text-align:right;">' + summaryRow[group + '-median'].toFixed(2) + '</div>')
            } else {
                $tableRow.append('<div style="display: table-cell; text-align:right;">-</div>')
            }
        })


        if (groups.length == 2) {
            if (summaryRow.delta === undefined) {
                $tableRow.append('<div style="display: table-cell; text-align:right;">-</div>')
            } else {
                $tableRow.append('<div style="display: table-cell; text-align:right;">' + getChangeText(summaryRow[groups[0] + '-median'], summaryRow.delta) + '</div>')
            }
        }
        var $viewCell = $('<div style="display: table-cell; text-align:right;"><a href="#">View</a></div>')
        $viewCell.click(function() {
            $('#graphModal').show()
            $('#graphModal .graphCanvas').empty()
            drawChartInPage(groups, summaryRow.taskName, summaryRow.results, $('#graphModal .graphCanvas'))
        })

        $tableRow.append($viewCell)

		$table.append($tableRow)
	});
}

function addRowClickListener($tableRow, $queryCell) {
    $tableRow.on('mousedown', function(event) {
        // Record the mouse position on mousedown
        $tableRow.data('mouseDownPos', {x: event.pageX, y: event.pageY})
    })
    $tableRow.on('mouseup', function(event) {
        // Calculate the distance between the mouse positions
        var mouseDownPos = $tableRow.data('mouseDownPos')
        if (mouseDownPos) {
            var xDiff = Math.abs(event.pageX - mouseDownPos.x)
            var yDiff = Math.abs(event.pageY - mouseDownPos.y)
            var distance = Math.sqrt(xDiff * xDiff + yDiff * yDiff)
            // If the distance is greater than a certain threshold, assume the user was highlighting text
            // and do not expand/collapse
            if (distance < 5) {
                $contentDiv = $queryCell.find('.query-text')
                if ($contentDiv.css('max-height') === "none") {
                    $contentDiv.css({'max-height' : '15px'})
                } else {
                    $contentDiv.css({'max-height' : ''})
                }
            }
        }
    })
}

function sortPreserveOrder(array, property, ascending) {
	if (ascending == undefined) {
		ascending = true
	}
    var sortOrder = 1;

    if(!ascending) {
        sortOrder = -1;
    }

	var sortArray = array.map(function(data, idx){
	    return {idx:idx, data:data}
	})

	sortArray.sort(function(a, b) {
		var aVal = a.data[property]
    	var bVal = b.data[property]

    	if (aVal === undefined || (typeof aVal === "number" && isNaN(aVal))) { //consider undefined or NaN as Number.NEGATIVE_INFINITY
    	    aVal = Number.NEGATIVE_INFINITY
    	}
    	if (bVal === undefined || (typeof bVal === "number" && isNaN(bVal))) {
    	    bVal = Number.NEGATIVE_INFINITY
    	}

    	var result = (aVal < bVal) ? -1 : (aVal > bVal) ? 1 : 0;
    	if (result == 0) {
    		return a.idx - b.idx
    	} else {
    		return result * sortOrder;
    	}
	});

	var result = sortArray.map(function(val){
	    return val.data
	});

	return result;
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
   if (baseValue > 0) {
    var percentageChange = (delta * 100 / baseValue).toFixed(1)
    var percentageChangeText = percentageChange >= 0 ? ('+' + percentageChange + '%') : (percentageChange + '%')
    return delta.toFixed(2) + '(' + percentageChangeText + ')'
   } else {
    return delta.toFixed(2)
   }


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
function drawChartInPage(groups, taskName, graphDataByCommit, $page, xAxisBy) {
    if (taskName.startsWith("detailed-stats-") && !chartDetailedStats) { //not great! we should have better structure
        return
    }
    var elementId = 'graph_' + graphIndex++
    var $graphDiv = $('<div class="graph" id="' + elementId + '"></div>')
    $page.append($graphDiv)

    if (xAxisBy === undefined) {
        xAxisBy = getValueFromParam('x-by', "date")
    }

    //TODO xaxis, yaxis
    var title = taskName + ' (' + groups.join(' vs ') + ')'
    var options = {
                    title: title,
                    pointSize: 5,
                    hAxis: {
                        title: xAxisBy === 'run' ? 'Run #' : 'Commit date',
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

    if (xAxisBy === 'run') {
        columns.push({type: 'number', id:'Run #'})
    } else {
        columns.push({type: 'date', id:'Commit date'})
    }
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

//            if (taskName.startsWith("detailed-stats-")) {
//                columns.push({type: 'number', label: "p99" + suffix, visible: false})
//                columns.push({type: 'string', role:'tooltip'})
//                columns.push({type: 'number', label: "p05" + suffix, visible: false})
//                columns.push({type: 'string', role:'tooltip'})
//                columns.push({type: 'number', label: "p10" + suffix, visible: false})
//                columns.push({type: 'string', role:'tooltip'})
//            }


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
    var runCounter = 0
    var currentGroup
    $.each(graphDataByCommit, function(index, dataOfCommit) {
        if (dataOfCommit['result'] == undefined) return;
        var row = []

        if (xAxisBy === 'run') {
            if (dataOfCommit.commitMeta.group !== currentGroup) {
                runCounter = 0
            }
            currentGroup = dataOfCommit.commitMeta.group
            row.push(++ runCounter)
        } else { //otherwise by default x-axis refers to commit/run date
            var commitDate = new Date(0)
            commitDate.setUTCSeconds(dataOfCommit.commitMeta.commitDate);
            row.push(commitDate)
        }
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
//                    if (taskName.startsWith("detailed-stats-")) {
//                        row.push(percentileResult['99th'])
//                        row.push(percentileResult['99th'].toFixed(2) + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
//                        row.push(percentileResult['5th'])
//                        row.push(percentileResult['5th'].toFixed(2) + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
//                        row.push(percentileResult['10th'])
//                        row.push(percentileResult['10th'].toFixed(2) + ' (' + group + ') ' + dataOfCommit.commitMeta.commitMsg)
//                    }
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
            if (index != 0 && column.type == 'number') { //first column is the not data
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

    if ($page.is('#graphModal .graphCanvas')) {
        $('#graphModal .xByDate').unbind('click').click( function() {
           $page.empty()
           drawChartInPage(groups, taskName, graphDataByCommit, $page, 'date')
        })
        $('#graphModal .xByRun').unbind('click').click( function() {
           $page.empty()
           drawChartInPage(groups, taskName, graphDataByCommit, $page, 'run')
        })
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
