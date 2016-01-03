<#function getOrdinalSuffix cardinal="notSet">
    <#assign ext='' />
    <#assign testCardinal = cardinal % 10 />
    <#if (cardinal % 100 < 21 && cardinal % 100 > 4)>
        <#assign ext='th' />
    <#else>
        <#if (testCardinal<1)>
            <#assign ext='th' />
        <#elseif (testCardinal<2)>
            <#assign ext='st' />
        <#elseif (testCardinal<3)>
            <#assign ext='nd' />
        <#elseif (testCardinal<4)>
            <#assign ext='rd' />
        <#else>
            <#assign ext='th' />
        </#if>
    </#if>
    <#return ext>
</#function>