<#macro ellipsize str max>
    <#if str?length &lt; max>
        ${str}
    <#else>
        ${str?substring(0, max-3)?trim}...
    </#if>
</#macro>
