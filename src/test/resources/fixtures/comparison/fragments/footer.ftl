<#macro render>
    <footer>${model.clientConfig.name!''} · ${generatedAt?datetime?string("yyyy-MM-dd HH:mm")}</footer>
</#macro>
