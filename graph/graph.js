function drawAllCharts() {

    /*var charts = [
        [
            'Branch 9x', 'branch_9x', 'Commit date', 'Time (seconds)', [
                {type: 'date', id:'Commit date'},
                {type: 'number', id: 'task1'},
                {type: 'string', role:'tooltip'},
                {type: 'number', id: 'task2: heap-mb'},
                {type: 'string', role:'tooltip'},
                {type: 'number', id: 'task2: node-shutdown'},
                {type:'string', role:'tooltip'},
                {type: 'number', id: 'task2: node-startup'},
                {type:'string', role:'tooltip'},
                {type: 'number', id: 'task2'},
                {type:'string', role:'tooltip'}
            ],
            [
                [ new Date(2022, 11 - 1, 26, 08, 01, 0, 0), 230.071, '230.071: 6f6bff492f286dce274dc93c49ea169560b33908: SOLR-16420: {!mlt_content} accepting external content (#1045)', 3666.9723292759486, '3666.9723292759486: 6f6bff492f286dce274dc93c49ea169560b33908: SOLR-16420: {!mlt_content} accepting external content (#1045)', 16.579857142857144, '16.579857142857144: 6f6bff492f286dce274dc93c49ea169560b33908: SOLR-16420: {!mlt_content} accepting external content (#1045)', 67.07557142857142, '67.07557142857142: 6f6bff492f286dce274dc93c49ea169560b33908: SOLR-16420: {!mlt_content} accepting external content (#1045)', 372.279, '372.279: 6f6bff492f286dce274dc93c49ea169560b33908: SOLR-16420: {!mlt_content} accepting external content (#1045)', ],
                [ new Date(2022, 11 - 1, 26, 07, 59, 0, 0), 234.351, '234.351: 7f1d4ef1797af11dfe0fa56d26826c0d7c1ea382: Solr 16420 9x (#1195)', 3628.37166922433, '3628.37166922433: 7f1d4ef1797af11dfe0fa56d26826c0d7c1ea382: Solr 16420 9x (#1195)', 15.068857142857143, '15.068857142857143: 7f1d4ef1797af11dfe0fa56d26826c0d7c1ea382: Solr 16420 9x (#1195)', 73.68357142857143, '73.68357142857143: 7f1d4ef1797af11dfe0fa56d26826c0d7c1ea382: Solr 16420 9x (#1195)', 396.30800000000005, '396.30800000000005: 7f1d4ef1797af11dfe0fa56d26826c0d7c1ea382: Solr 16420 9x (#1195)', ],
                [ new Date(2022, 11 - 1, 24, 12, 44, 0, 0), 233.813, '233.813: c62e49f2426828bb8f0b0c6ceab9a73bfeec029c: Update search-process.png (#1180)', 3676.199005126953, '3676.199005126953: c62e49f2426828bb8f0b0c6ceab9a73bfeec029c: Update search-process.png (#1180)', 15.997428571428571, '15.997428571428571: c62e49f2426828bb8f0b0c6ceab9a73bfeec029c: Update search-process.png (#1180)', 72.88071428571429, '72.88071428571429: c62e49f2426828bb8f0b0c6ceab9a73bfeec029c: Update search-process.png (#1180)', 392.10999999999996, '392.10999999999996: c62e49f2426828bb8f0b0c6ceab9a73bfeec029c: Update search-process.png (#1180)', ],
                [ new Date(2022, 11 - 1, 22, 16, 11, 0, 0), 229.667, '229.667: a748189a6a8da6378e6f6def231c689f0bdf2a2f: SOLR-15955: Fix placing of slf4j-api jar in the lib/ext dir (#1178)', 3678.503171648298, '3678.503171648298: a748189a6a8da6378e6f6def231c689f0bdf2a2f: SOLR-15955: Fix placing of slf4j-api jar in the lib/ext dir (#1178)', 15.538714285714287, '15.538714285714287: a748189a6a8da6378e6f6def231c689f0bdf2a2f: SOLR-15955: Fix placing of slf4j-api jar in the lib/ext dir (#1178)', 68.89685714285714, '68.89685714285714: a748189a6a8da6378e6f6def231c689f0bdf2a2f: SOLR-15955: Fix placing of slf4j-api jar in the lib/ext dir (#1178)', 380.00499999999994, '380.00499999999994: a748189a6a8da6378e6f6def231c689f0bdf2a2f: SOLR-15955: Fix placing of slf4j-api jar in the lib/ext dir (#1178)', ],
                [ new Date(2022, 11 - 1, 22, 16, 11, 0, 0), 229.047, '229.047: d41598068ca40b35caf7d91a0b0804b4e0fb9268: SOLR-15955: Update Jetty dependency to 10 (#585)', 3648.550429207938, '3648.550429207938: d41598068ca40b35caf7d91a0b0804b4e0fb9268: SOLR-15955: Update Jetty dependency to 10 (#585)', 15.897285714285713, '15.897285714285713: d41598068ca40b35caf7d91a0b0804b4e0fb9268: SOLR-15955: Update Jetty dependency to 10 (#585)', 69.19685714285716, '69.19685714285716: d41598068ca40b35caf7d91a0b0804b4e0fb9268: SOLR-15955: Update Jetty dependency to 10 (#585)', 374.75, '374.75: d41598068ca40b35caf7d91a0b0804b4e0fb9268: SOLR-15955: Update Jetty dependency to 10 (#585)', ]
            ]
        ]
    ];*/

//    var charts = [
//    ];
//
//    for (const ch of charts)
//        drawChart(ch[0], ch[1], ch[2], ch[3], ch[4], ch[5]);
    var allResultsByTaskName = {}
    var branches = []
    $.each(graph_data, function(branch, branchData) { //collect branch names first. then figure out how many pages are there
       branches.push(branch)
    })


    $.each(graph_data, function(branch, branchData) {
        var $page = generatePage(branch, graph_data.length == 1)

        var branchResultsByTaskName = {}
        $.each(branchData, function(index, resultByCommit) {
            var commitMeta = {
                branch: resultByCommit.branch,
                commitHash: resultByCommit.commit_hash,
                commitDate: resultByCommit.commit_date,
                commitMsg: resultByCommit.commit_msg
            }
            $.each(resultByCommit.results, function(taskName, resultByTask) {
                var resultsOfThisTask = branchResultsByTaskName[taskName]
                if (!resultsOfThisTask) {
                    resultsOfThisTask = []
                    branchResultsByTaskName[taskName] = resultsOfThisTask
                }
                var entry = {
                    commitMeta : commitMeta,
                    result: resultByTask[0]
                }
                resultsOfThisTask.push(entry)
            })
        })
        //plot graph of this branch of this task
        $.each(branchResultsByTaskName, function(taskName, resultsByTask) {
            drawChartInPage([branch], taskName, resultsByTask, $page)
            if (!allResultsByTaskName[taskName]) {
                allResultsByTaskName[taskName] = [].concat(resultsByTask)
            } else {
                allResultsByTaskName[taskName].push(...resultsByTask)
            }
        })
    })

    //generate a graph with all branches
    if (branches.length > 1) {
        var $page = generatePage(branches.join(' vs '), true)
        $.each(allResultsByTaskName, function(taskName, resultsByTask) {
            drawChartInPage(branches, taskName, resultsByTask, $page)
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
function drawChartInPage(branches, taskName, graphDataByCommit, $page) {
    var elementId = 'graph_' + graphIndex++
    var $graphDiv = $('<div class="graph" id="' + elementId + '"></div>')
    $page.append($graphDiv)
//    $('#canvas').append($graphDiv)

//TODO xaxis, yaxis
    var title = taskName + ' (' + branches.join(' vs ') + ')'
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
                        title: 'Time (seconds)',
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
    //addViewColumns(viewColumns, 1)
    $.each(branches, function(index, branch) {
        var suffix = ''
        if (branches.length > 1) {
            suffix = ' (' + branch + ')'
        }
        if (chartType === ChartTypes.Simple) {
            columns.push({type: 'number', label: "duration" + suffix})
            columns.push({type: 'string', role:'tooltip'})
//            addViewColumns(viewColumns, 2)
        } else if (chartType === ChartTypes.Percentile) {
            columns.push({type: 'number', label: "median" + suffix})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "p90" + suffix})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "p95" + suffix})
            columns.push({type: 'string', role:'tooltip'})
            columns.push({type: 'number', label: "mean" + suffix})
            columns.push({type: 'string', role:'tooltip'})
//            addViewColumns(viewColumns, 8)
        }
    })

    var viewColumns = []
    $.each(columns, function(index, column) {
        if (column.type === 'number') {
            column.visible = true //this property is NOT used by google chart, is it simply for us to track the column state
        }
        viewColumns.push(index)
        data.addColumn(column)
    })


    var rows = []
    $.each(graphDataByCommit, function(index, dataOfCommit) {
        var row = []
        var commitDate = new Date(0)
        commitDate.setUTCSeconds(dataOfCommit.commitMeta.commitDate);
        row.push(commitDate)
        $.each(branches, function(walkerBranchIndex, branch) { //walk through branch and pad if necessary
            if (branch == dataOfCommit.commitMeta.branch) {
                if (chartType === ChartTypes.Simple) {
                    var duration = dataOfCommit['result']['end-time'] - dataOfCommit['result']['start-time']
                    row.push(duration)
                    row.push(duration + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
                } else if (chartType === ChartTypes.Percentile) {
                    var timingResult = dataOfCommit['result']['timings'][0] //TODO first element only?
                    row.push(timingResult['50th'])
                    row.push(timingResult['50th'] + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(timingResult['90th'])
                    row.push(timingResult['90th'] + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(timingResult['95th'])
                    row.push(timingResult['95th'] + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
                    row.push(timingResult['mean'])
                    row.push(timingResult['mean'] + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
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


    console.log(title)
    console.log(columns)
    console.log(viewColumns)
    console.log(rows)

    // Add the rows
    data.addRows(rows);

    // Instantiate and draw our chart, passing in some options.
    var chart = new google.visualization.ChartWrapper({
        chartType: 'LineChart',
        containerId: elementId,
        dataTable: data,
        options: /*{
            width: 600,
            height: 400
        }*/ options
    });
//    var chart = new google.visualization.LineChart(document.getElementById(elementId));

    // create columns array
//    var columns = [0];
//    /* the series map is an array of data series
//     * "column" is the index of the data column to use for the series
//     * "roleColumns" is an array of column indices corresponding to columns with roles that are associated with this data series
//     * "display" is a boolean, set to true to make the series visible on the initial draw
//     */
//    var seriesMap = [];
//    for (i=0; i<data.getNumberOfColumns()/2-1; i++) {
//        var disp = true;
//        if ((branchColumns[i*2+1]["label"]).indexOf("heap-mb") != -1 || (branchColumns[i*2+1]["label"]).indexOf("timings_") != -1) {
//            disp = false;
//        }
//        var ser = {column: i*2+1, roleColumns: [i*2+2], display: disp};
//        seriesMap.push(ser);
//    }
//
//    var columnsMap = {};

//    for (var i = 0; i < seriesMap.length; i++) {
//        var col = seriesMap[i].column;
//        columnsMap[col] = i;
//        // set the default series option
//        series[i] = {};
//        if (seriesMap[i].display) {
//            // if the column is the domain column or in the default list, display the series
//            columns.push(col);
//        }
//        else {
//            // otherwise, hide it
//            columns.push({
//                label: data.getColumnLabel(col),
//                type: data.getColumnType(col),
//                sourceColumn: col,
//                calc: function () {
//                    return null;
//                }
//            });
//            // backup the default color (if set)
//            if (typeof(series[i].color) !== 'undefined') {
//                series[i].backupColor = series[i].color;
//            }
//            series[i].color = '#CCCCCC';
//        }
//        for (var j = 0; j < seriesMap[i].roleColumns.length; j++) {
//            columns.push(seriesMap[i].roleColumns[j]);
//        }
//    }

//    var view = chart.getView() || {};
//    view.columns = columns;
//    chart.setView(view);

// Toggle visibility of data series on click of legend.
//    google.visualization.events.addListener(chart, 'click', function (target) {
//        var c = chart.getColumns()
//        console.log(c)
//    });
    google.visualization.events.addListener(chart, 'select', showHideSeries)

//    var dataView = new google.visualization.DataView(data);
    var dataView = chart.getView() || {};
    dataView.columns = viewColumns
    chart.setView(dataView)
    chart.draw();


    function showHideSeries(target) {
        var sel = chart.getChart().getSelection();
        // if selection length is 0, we deselected an element
        if (sel.length > 0) {
            // if row is undefined, we clicked on the legend
            if (sel[0].row == null) {
                var selectedColIndex = sel[0].column;
                console.log(selectedColIndex)
                columns[selectedColIndex].visible = !columns[selectedColIndex].visible //flip the status
                if (columns[selectedColIndex].visible) {
                    viewColumns[selectedColIndex] = selectedColIndex
                } else {
                    viewColumns[selectedColIndex] = {
                                        label: columns[selectedColIndex].label,
                                        type: columns[selectedColIndex].type,
                                        calc: function () {
                                            return null;
                                        }
                                    };
                }
                dataView = chart.getView()
                dataView.columns = viewColumns
                chart.setView(dataView)
                //regen colors
                var series = []
                $.each(columns, function(index, column) {
                    if (column.type == 'number') {
                        if (column.visible) {
                            series.push({}) //use default color
                        } else {
                            series.push({ color : '#CCCCCC'})
                        }
                    }
                })
                chart.setOption('series', series);
                chart.draw();

    //            if (typeof(columns[col]) == 'number') {
    //                var src = columns[col];
    //
    //                // hide the data series
    //                columns[col] = {
    //                    label: data.getColumnLabel(src),
    //                    type: data.getColumnType(src),
    //                    sourceColumn: src,
    //                    calc: function () {
    //                        return null;
    //                    }
    //                };
    //
    //                // grey out the legend entry
    //                series[columnsMap[src]].color = '#CCCCCC';
    //            }
    //            else {
    //                var src = columns[col].sourceColumn;
    //
    //                // show the data series
    //                columns[col] = src;
    //                series[columnsMap[src]].color = null;
    //            }
    //            var view = chart.getView() || {};
    //            view.columns = columns;
    //            chart.setView(view);
    //            chart.draw();
            }
        }
    }
}


function addViewColumns(viewColumns, count) {
    for (i = 0; i < count ; i ++) {
        viewColumns.push()
    }
}

google.load('visualization', '1', {packages: ['corechart'], callback: drawAllCharts});
