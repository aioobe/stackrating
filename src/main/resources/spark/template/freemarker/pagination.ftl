<#-- Curtsey Chaquotay: http://stackoverflow.com/a/6392836 -->

<#function max x y>
    <#if (x<y)><#return y><#else><#return x></#if>
</#function>

<#function min x y>
    <#if (x<y)><#return x><#else><#return y></#if>
</#function>

<#macro pages totalPages p>
    <#assign size = totalPages?size>
    <#if (p <= 5)> <#-- p among first 5 pages -->
        <#assign interval = 1..(min(p + 2, size))>
    <#elseif ((size-p)<5)> <#-- p among last 5 pages -->
        <#assign interval = (max(1, (p - 2)))..size >
    <#else>
        <#assign interval = (p - 2)..(p + 2)>
    </#if>
    <#if !(interval?seq_contains(1))>
        <a href="?page=1">1</a> ... <#rt>
    </#if>
    <#list interval as page>
        <#if page=p>
            ${page} <#t>
        <#else>
            <a href="?page=${page?c}">${page}</a> <#t>
        </#if>
    </#list>
    <#if !(interval?seq_contains(size))>
        ... <a href="?page=${size?c}">${size}</a><#lt>
    </#if>
</#macro>
