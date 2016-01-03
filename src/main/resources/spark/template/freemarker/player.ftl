<#include "master.ftl"/>
<#import "pagination.ftl" as pagination />
<#import "rating.ftl" as rating />
<#import "ellipsise.ftl" as ellip />
<#import "ordinal.ftl" as ordinal />

<#macro scripts>
    <#if currentPage=1>
        <script type="text/javascript" src="https://www.google.com/jsapi?autoload={'modules':[{'name':'visualization','version':'1.1','packages':['line', 'corechart']}]}"></script>
    
        <script type="text/javascript">
            google.load('visualization', '1.1', {packages: ['line', 'corechart']});
            google.setOnLoadCallback(drawChart);
    
            function drawChart() {
    
                var data = new google.visualization.DataTable();
                data.addColumn('date', 'Date');
                data.addColumn('number', "Rating");
    
                var rows = [
                    <#list ratingGraph as point>
[ ${point.timestamp?c}, ${point.val?string["0.00"]}],
                    </#list>
                ];
                rows.sort(function (a, b) { return b[0] - a[0]; });
                for (var i = 0; i < rows.length; i++) {
                    rows[i] = [ new Date(1000 * rows[i][0]), rows[i][1] ];
                }
                data.addRows(rows);
    
                
                var classicOptions = {
                    title: 'Rating',
                    width: 800,
                    height: 300,
                    legend: { position: 'none' },
                    curveType: 'function'
                };
    
                var graphDiv = document.getElementById('ratingGraph');
                var ratingChart = new google.visualization.LineChart(graphDiv);
                ratingChart.draw(data, classicOptions);
            }
        </script>
    </#if>
    
</#macro>

<#macro content>
    <div style="text-align: center; margin-top: 2.5em">Rating Stats for</a></div>
    <h2 style="margin-top: 0em;"><a href="http://stackoverflow.com/users/${player.id?c}">${player.displayName}</a></h2>

    <center>
        <table width="70%" style="border: none">
            <tr>
                <td style="text-align: center;">
                    Rating<br/>
                    ${player.rating?string["0.00"]}
                    <span style="margin-left: 1em" class="secondary">(${player.ratingPos}<span style="font-size: 60%">${ordinal.getOrdinalSuffix(player.ratingPos)}</span>)</span>
                </td>
                <td style="text-align: center;">
                    Reputation<br/>
                    ${player.rep}
                    <span style="margin-left: 1em" class="secondary">(${player.repPos}<span style="font-size: 60%">${ordinal.getOrdinalSuffix(player.repPos)}</span>)</span>
                </td>
            </tr>
        </table>
        <div id="ratingGraph"></div>
    </center>

    <#if !entries?has_content>
        <div style="text-align: center">No answers found.</div>
    <#else>
        <div style="width: 85%; margin: 0px auto 2em;">
            <div style="margin: 0em 0em 1em;">Page: <@pagination.pages 1..numPages currentPage /></div>
            <table style="width: 100%;">
                <tr>
                    <th>Title</th>
                    <th style="text-align: right;">&#916;</th>
                </tr>
                <#list entries as entry>
                    <tr>
                        <td><a href="/question/${entry.gameId?c}"><@ellip.ellipsize entry.gameTitle 70 /></a></td>
                        <td style="text-align: right;"><@rating.ratingDelta val=entry.ratingDelta /></td>
                    </tr>
                </#list>
            </table>
        </div>
    </#if>
</#macro>

<@display_page />
