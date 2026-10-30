<#macro render>
    <footer style="margin-top: 20px; font-size: 11px;">
        ${model.clientConfig.name!''} · Generated ${generatedAt?datetime?string("yyyy-MM-dd HH:mm")}
    </footer>
</#macro>
