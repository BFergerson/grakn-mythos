const echarts = require("echarts");

if (typeof clonedLegend !== 'undefined' && clonedLegend != null) {
    document.getElementById("include_anonymous_variables").checked = clonedLegend.queryOptions.includeAnonymousVariables;
    document.getElementById("entity_naming_by_variable").checked = clonedLegend.queryOptions.displayOptions.entityNamingScheme === "BY_VARIABLE";
    document.getElementById("entity_naming_by_type").checked = clonedLegend.queryOptions.displayOptions.entityNamingScheme === "BY_TYPE";
    document.getElementById("entity_naming_by_id").checked = clonedLegend.queryOptions.displayOptions.entityNamingScheme === "BY_ID";
    document.getElementById("relation_naming_by_variable").checked = clonedLegend.queryOptions.displayOptions.relationNamingScheme === "BY_VARIABLE";
    document.getElementById("relation_naming_by_type").checked = clonedLegend.queryOptions.displayOptions.relationNamingScheme === "BY_TYPE";
    document.getElementById("relation_naming_by_id").checked = clonedLegend.queryOptions.displayOptions.relationNamingScheme === "BY_ID";
    document.getElementById("attribute_naming_by_value").checked = clonedLegend.queryOptions.displayOptions.attributeNamingScheme === "BY_VALUE";
    document.getElementById("attribute_naming_by_id").checked = clonedLegend.queryOptions.displayOptions.attributeNamingScheme === "BY_ID";
    legendGraphEnabled = true;
}

if (legendGraphEnabled) {
    chart = echarts.init(document.getElementById('main'), 'macarons');
    chart.showLoading();
    let fetchLegendId;
    if (typeof legendId !== 'undefined' && legendId != null) {
        fetchLegendId = legendId
    } else if (typeof clonedLegend !== 'undefined' && clonedLegend != null) {
        fetchLegendId = clonedLegend.id;
    }
    jQuery.get("/api/legend/" + fetchLegendId + '/graph', function (graknGraph) {
        chart.hideLoading();

        graknGraph.nodes.forEach(function (node) {
            if (node.type === "entity" || node.type === "relation") {
                node.symbolSize = 75;
                node.label = {
                    fontSize: 25
                }
            } else {
                node.symbolSize = 50;
            }
            // node.label = {
            //     show: node.symbolSize > 30
            // };
        });
        graknGraph.links.forEach(function (link) {
            link.emphasis = {
                lineStyle: {
                    width: (link.type === "attribute") ? 3 : 10
                }
            }
            link.label = {
                show: true,
                formatter: link.name,
                fontSize: (link.type === "attribute") ? 15 : 20,
                color: "white"
            }
            link.lineStyle = {
                curveness: (link.type === "attribute") ? 0 : 0.1,
                width: (link.type === "attribute") ? 1 : 3,
            }
        });
        option = {
            legend: {
                show: true,
                color: "white",
                textStyle: {
                    color: 'white',
                    fontSize: 20
                }
            },
            series: [{
                type: 'graph',
                layout: 'force',
                animation: true,
                focusNodeAdjacency: true,
                symbolSize: 100,
                itemStyle: {
                    borderColor: '#fff',
                    borderWidth: 1,
                    shadowBlur: 10,
                    shadowColor: 'rgba(0, 0, 0, 0.3)'
                },
                label: {
                    show: true,
                    fontSize: 15,
                    fontWeight: 'bold'
                },
                lineStyle: {
                    color: 'source',
                    curveness: 0.1
                },
                draggable: true,
                data: graknGraph.nodes,
                categories: Array.from(new Set(graknGraph.nodes.map(function (node, idx) {
                    return {name: node.category, label: {show: true}};
                }))),
                roam: true,
                force: {
                    repulsion: 750,
                    edgeLength: 200,
                },
                edges: graknGraph.links
            }]
        };

        chart.setOption(option);
    }).fail(function (error) {
        chart.hideLoading();
        $('#missing-legend').modal('show')
    });

    if (typeof fullscreenChart !== 'undefined' && fullscreenChart) {
        jQuery.get("/api/legend/" + fetchLegendId, function (legend) {
            document.getElementById("title").innerText = legend.description + " [Full Screen] - Grakn Mythos";
        });
    } else {
        jQuery.get("/api/legend/" + fetchLegendId, function (legend) {
            window.editor.setValue(unescape(legend.query));
            if (document.getElementById("title") !== null) {
                document.getElementById("title").innerText = legend.description + " - Grakn Mythos";
            }
            if (document.getElementById("legend_description") !== null) {
                document.getElementById("legend_description").innerText = legend.description;
            }
        });
    }
}


window.executeLegend = function executeLegend() {
    const legendQuery = window.editor.getValue();
    if (legendQuery === "") {
        $('#missing-query').modal('show')
    } else {
        let code = window.editor.getValue()
        let syntaxErrors = ParserFacade.validate(code);
        if (syntaxErrors.length > 0) {
            let monacoErrors = "";
            for (let e of syntaxErrors) {
                monacoErrors += e.message + " (Line: " + e.startLine + ")" + "\n";
            }
            document.getElementById("invalid-query-text").innerText = monacoErrors;
            $('#invalid-query').modal('show')
        } else {
            document.getElementById("execute_icon").style.display = "none";
            document.getElementById("execute_spinner_icon").style.display = "";

            const submitData = {
                query: legendQuery,
                queryOptions: {
                    includeAnonymousVariables: document.getElementById("include_anonymous_variables").checked,
                    displayOptions: {
                        entityNamingScheme: document.querySelector('input[name="entityNaming"]:checked').value.toUpperCase(),
                        relationNamingScheme: document.querySelector('input[name="relationNaming"]:checked').value.toUpperCase(),
                        attributeNamingScheme: document.querySelector('input[name="attributeNaming"]:checked').value.toUpperCase()
                    }
                }
            }

            var xhttp = new XMLHttpRequest();
            xhttp.onreadystatechange = function () {
                if (xhttp.readyState === 4) {
                    document.getElementById("execute_icon").style.display = "";
                    document.getElementById("execute_spinner_icon").style.display = "none";

                    var contentType = xhttp.getResponseHeader("Content-Type");
                    if (contentType.includes("json")) {
                        chart = echarts.init(document.getElementById('main'), 'macarons');
                        chart.showLoading();
                        webkitDep = JSON.parse(this.responseText);
                        chart.hideLoading();

                        webkitDep.nodes.forEach(function (node) {
                            if (node.type === "entity" || node.type === "relation") {
                                node.symbolSize = 75;
                                node.label = {
                                    fontSize: 25
                                }
                            } else {
                                node.symbolSize = 50;
                            }
                            // node.label = {
                            //     show: node.symbolSize > 30
                            // };
                        });
                        webkitDep.links.forEach(function (link) {
                            link.emphasis = {
                                lineStyle: {
                                    width: (link.type === "attribute") ? 3 : 10
                                }
                            }
                            link.label = {
                                show: true,
                                formatter: link.name,
                                fontSize: (link.type === "attribute") ? 15 : 20,
                                color: "white"
                            }
                            link.lineStyle = {
                                curveness: (link.type === "attribute") ? 0 : 0.1,
                                width: (link.type === "attribute") ? 1 : 3,
                            }
                        });
                        option = {
                            legend: {
                                show: true,
                                color: "white",
                                textStyle: {
                                    color: 'white',
                                    fontSize: 20
                                }
                            },
                            series: [{
                                type: 'graph',
                                layout: 'force',
                                animation: true,
                                focusNodeAdjacency: true,
                                symbolSize: 100,
                                itemStyle: {
                                    borderColor: '#fff',
                                    borderWidth: 1,
                                    shadowBlur: 10,
                                    shadowColor: 'rgba(0, 0, 0, 0.3)'
                                },
                                label: {
                                    show: true,
                                    fontSize: 15,
                                    fontWeight: 'bold'
                                },
                                lineStyle: {
                                    color: 'source',
                                    curveness: 0.1
                                },
                                draggable: true,
                                data: webkitDep.nodes,
                                categories: Array.from(new Set(webkitDep.nodes.map(function (node, idx) {
                                    return {name: node.category, label: {show: true}};
                                }))),
                                roam: true,
                                force: {
                                    repulsion: 750,
                                    edgeLength: 200,
                                },
                                edges: webkitDep.links
                            }]
                        };

                        chart.setOption(option);

                        document.getElementById("save_button").classList.remove("disabled");
                        document.getElementById("save_button").disabled = false;
                    } else {
                        document.getElementById("invalid-query-text").innerText = this.responseText;
                        $('#invalid-query').modal('show')
                    }
                }
            };
            xhttp.open("POST", "/api/legend/execute", true);
            xhttp.send(JSON.stringify(submitData));
        }
    }
}

window.saveLegend = function saveLegend() {
    const legendDescription = document.getElementById('legend_description').value;
    const legendQuery = window.editor.getValue();
    if (legendQuery === "") {
        $('#missing-query').modal('show')
    } else {
        let code = window.editor.getValue()
        let syntaxErrors = ParserFacade.validate(code);
        if (syntaxErrors.length > 0) {
            let monacoErrors = "";
            for (let e of syntaxErrors) {
                monacoErrors += e.message + " (Line: " + e.startLine + ")" + "\n";
            }
            document.getElementById("invalid-query-text").innerText = monacoErrors;
            $('#invalid-query').modal('show')
        } else if (legendDescription === "") {
            $('#missing-description').modal('show')
        } else {
            document.getElementById("save_icon").style.display = "none";
            document.getElementById("save_spinner_icon").style.display = "";

            const saveData = {
                query: legendQuery,
                queryOptions: {
                    includeAnonymousVariables: document.getElementById("include_anonymous_variables").checked,
                    displayOptions: {
                        entityNamingScheme: document.querySelector('input[name="entityNaming"]:checked').value.toUpperCase(),
                        relationNamingScheme: document.querySelector('input[name="relationNaming"]:checked').value.toUpperCase(),
                        attributeNamingScheme: document.querySelector('input[name="attributeNaming"]:checked').value.toUpperCase()
                    }
                },
                description: legendDescription,
                image: chart.getDataURL({
                    backgroundColor: "#2E3444"
                })
            }

            const xhttp = new XMLHttpRequest();
            xhttp.onreadystatechange = function () {
                if (xhttp.readyState === 4) {
                    if (xhttp.status === 200) {
                        location.href = "/legend/" + JSON.parse(xhttp.responseText).legendId;
                    } else {
                        document.getElementById("save_icon").style.display = "";
                        document.getElementById("save_spinner_icon").style.display = "none";

                        document.getElementById("invalid-query-text").innerText = this.responseText;
                        $('#invalid-query').modal('show')
                    }
                }
            };
            xhttp.open("POST", "/api/legend", true);
            xhttp.send(JSON.stringify(saveData));
        }
    }
}

$(function () {
    'use strict'
    $('[data-toggle="offcanvas"]').on('click', function () {
        $('.offcanvas-collapse').toggleClass('open')
    })
})
$(window).on('resize', function () {
    if (chart != null && chart != undefined) {
        chart.resize({
            width: document.getElementById("main").clientWidth,
            height: document.getElementById("main").clientHeight
        });
    }
});