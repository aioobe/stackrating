<#macro ratingDelta val><#assign str = val?string["0.00"]><#if 0 < val><span style="color: #00AA00;">+${str}</span><#elseif val < 0><span style="color: #AA0000;">${str}</span><#else>${str}</#if></#macro>
<#macro rating val>${val?string["0.00"]}</#macro>
