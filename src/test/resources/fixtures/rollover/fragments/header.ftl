<#macro render>
    <div class="container" style="clear: both; height: auto;">

        <div style="float: left; width: 150px;">
            <#if model.clientConfig.logoPath?has_content>
                <img src="${model.clientConfig.logoPath}"
                     alt="${model.clientConfig.name!''} Logo"
                     style="width: 100%; height: auto; object-fit: contain; float: right">
            </#if>
        </div>

        <div style="float: right; text-align: left; width: 220px;">
            <#if model.clientConfig.supportPhone?has_content>
                <div style="margin-bottom: 5px;">
                    <i class="fas fa-phone"></i>
                    <span>  ${model.clientConfig.supportPhone}</span>
                </div>
            </#if>

            <#if model.clientConfig.contactEmail?has_content>
                <div style="margin-bottom: 5px;">
                    <i class="fas fa-envelope"></i>
                    <span>  ${model.clientConfig.contactEmail}</span>
                </div>
            </#if>

            <#if model.clientConfig.formattedAddress?has_content>
                <div style="margin-bottom: 5px;">
                    <i class="fas fa-map-marker-alt"></i>
                    <span>  ${model.clientConfig.formattedAddress}</span>
                </div>
            </#if>

            <div style="font-size: 14px; font-weight: bold; text-align: left; margin-top: 10px;">
                Date: <strong>${date}</strong>
            </div>
        </div>

        <div style="clear: both;"></div>
    </div>
</#macro>
