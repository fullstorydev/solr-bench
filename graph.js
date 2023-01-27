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
    var graphIndex = 0
    $.each(graph_data, function(branch, branchData) {
        var resultsByTaskName = {}
        $.each(branchData, function(index, resultByCommit) {
            var commitMeta = {
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
        //plot graph of this branch of this task
        $.each(resultsByTaskName, function(taskName, resultsByTask) {
            var graphDivId = 'graph_' + graphIndex++
            var $graphDiv = $('<div class="graph" id="' + graphDivId + '"></div>')
            $('#mainContainer').append($graphDiv)
            drawChart2(branch, taskName, resultsByTask, graphDivId)
        })
    })
}

function drawChart2(branch, taskName, graphDataByCommit, elementId) {
//TODO xaxis, yaxis
    var options = {
                    title: branch,
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
                };

    var data = new google.visualization.DataTable();


    data.addColumn({type: 'date', id:'Commit date'})
    var rows = []
    if (graphDataByCommit[0]['result']['timings']) { //then graph the with the median
        data.addColumn({type: 'number', label: taskName + "-median"})
        data.addColumn({type: 'string', role:'tooltip'})
        data.addColumn({type: 'number', label: taskName + "-p90"})
        data.addColumn({type: 'string', role:'tooltip'})
        data.addColumn({type: 'number', label: taskName + "p95"})
        data.addColumn({type: 'string', role:'tooltip'})
        data.addColumn({type: 'number', label: taskName + "-mean"})
        data.addColumn({type: 'string', role:'tooltip'})
        $.each(graphDataByCommit, function(index, dataOfCommit) {
            var row = []
            var timingResult = dataOfCommit['result']['timings'][0] //TODO first element only?
            var commitDate = new Date(0)
            commitDate.setUTCSeconds(dataOfCommit.commitMeta.commitDate);
            row.push(commitDate)
            row.push(timingResult['50th'])
            row.push(timingResult['50th'] + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
            row.push(timingResult['90th'])
            row.push(timingResult['90th'] + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
            row.push(timingResult['95th'])
            row.push(timingResult['95th'] + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
            row.push(timingResult['mean'])
            row.push(timingResult['mean'] + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
            rows.push(row)
        })
    } else {
        data.addColumn({type: 'number', label: taskName})
        data.addColumn({type: 'string', role:'tooltip'})
        $.each(graphDataByCommit, function(index, dataOfCommit) {
            var row = []
            var duration = dataOfCommit['result']['end-time'] - dataOfCommit['result']['start-time']
            var commitDate = new Date(0)
            commitDate.setUTCSeconds(dataOfCommit.commitMeta.commitDate);
            row.push(commitDate)
            row.push(duration)
            row.push(duration + ' (' + branch + ') ' + dataOfCommit.commitMeta.commitMsg)
            rows.push(row)
        })
    }

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
    chart.draw();

//    chart.setOption('series', series);
//
//    function showHideSeries () {
//        var sel = chart.getChart().getSelection();
//        // if selection length is 0, we deselected an element
//        if (sel.length > 0) {
//            // if row is undefined, we clicked on the legend
//            if (sel[0].row == null) {
//                var col = sel[0].column;
//                if (typeof(columns[col]) == 'number') {
//                    var src = columns[col];
//
//                    // hide the data series
//                    columns[col] = {
//                        label: data.getColumnLabel(src),
//                        type: data.getColumnType(src),
//                        sourceColumn: src,
//                        calc: function () {
//                            return null;
//                        }
//                    };
//
//                    // grey out the legend entry
//                    series[columnsMap[src]].color = '#CCCCCC';
//                }
//                else {
//                    var src = columns[col].sourceColumn;
//
//                    // show the data series
//                    columns[col] = src;
//                    series[columnsMap[src]].color = null;
//                }
//                var view = chart.getView() || {};
//                view.columns = columns;
//                chart.setView(view);
//                chart.draw();
//            }
//        }
//    }
//
//    google.visualization.events.addListener(chart, 'select', showHideSeries);
//
//    // create a view with the default columns
//    var view = {
//        columns: columns
//    };
//    chart.draw();
}


google.load('visualization', '1', {packages: ['corechart'], callback: drawAllCharts});
