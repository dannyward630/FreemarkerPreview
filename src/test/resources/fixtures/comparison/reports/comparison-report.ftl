<#import "fragments/layout.ftl" as layout>
<#-- A made-up report for tests: the shapes real reports use, none of their content. -->
<@layout.layout>
    <h1>Branch Comparison Report</h1>
    <p>Report date: ${reportDate!"N/A"} · Period: ${currentTerm!"N/A"} · Branches: ${institutionCount!"N/A"}</p>

    <p>Expected change: ${expectedChange?string["#,##0.00"]}
        (${(expectedChange.divide(previousTotals.expectedFee, 4, "roundHalfUp") * 100)?string["0.00"]}%)</p>
    <p>Expected: ${currentTotals.expectedFee?string["#,##0.00"]} · Balance: ${currentTotals.balance?string["#,##0.00"]}
        (was ${previousTotals.balance?string["#,##0.00"]})</p>
    <#if currentCollectionRate??>
        <p>Rate: ${(currentCollectionRate * 100)?string["0.00"]}% ·
            <#if currentCollectionRate gte 0.9>Excellent<#elseif currentCollectionRate gte 0.7>Good<#else>Low</#if></p>
    </#if>

    <table>
        <#list institutionMetrics![] as metric>
            <tr>
                <td><#if metric.institutionCode?? && Q.getInstitutionName(metric.institutionCode)??>${Q.getInstitutionName(metric.institutionCode)}<#else>Unknown Institution</#if></td>
                <td>${metric.collectedFees?string["#,##0.00"]}</td>
                <td><#if metric.collectionRate gte 0.9>Excellent<#elseif metric.collectionRate gte 0.7>Good<#else>Low</#if></td>
            </tr>
        </#list>
    </table>

    <table>
        <#list institutionComparisons![] as comparison>
            <tr>
                <td>${comparison.institutionCode}</td>
                <td>${comparison.students}</td>
                <td>${(comparison.collectionRate * 100)?string["0.00"]}%</td>
            </tr>
        </#list>
    </table>

    <#list classBreakdown!{} as institutionCode, classes>
        <h2>${institutionCode}</h2>
        <#list classes![] as classReport>
            <#assign previousClassData = ((previousDataByInstitution[institutionCode]![])?filter(p -> p.name?? && p.name == classReport.name!"")?first)!{}>
            <p>${classReport.name!"Unknown"}: ${classReport.collectedFee?string["#,##0.00"]}
                <#if previousClassData.collectedFee??>(${classReport.collectedFee.subtract(previousClassData.collectedFee)?string["#,##0.00"]})</#if></p>
        </#list>
    </#list>
</@layout.layout>
