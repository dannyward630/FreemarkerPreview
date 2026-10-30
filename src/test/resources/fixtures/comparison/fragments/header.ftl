<#macro render>
    <header>
        <#if model.clientConfig.logoPath?has_content><img src="${model.clientConfig.logoPath}" alt="logo"></#if>
        <p>${model.clientConfig.name!''}</p>
        <#if model.clientConfig.contactEmail?has_content><p>${model.clientConfig.contactEmail}</p></#if>
        <p>Date: <strong>${date}</strong></p>
    </header>
</#macro>
