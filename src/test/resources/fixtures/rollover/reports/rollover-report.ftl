<#import "fragments/layout.ftl" as layout>
<#import "fragments/header.ftl" as header>
<#import "fragments/footer.ftl" as footer>

<@layout.layout>
    <h1 style="text-align: center; color: #003366; margin: 15px 0;">Organization Rollover Comparison Report</h1>

    <!-- Report Details -->
    <div style="margin: 20px 0;">
        <table style="width: 100%; border-collapse: collapse;">
            <tr>
                <td style="padding: 5px; border: 1px solid #ddd; background-color: #f5f5f5; font-weight: bold;">Report
                    Date:
                </td>
                <td style="padding: 5px; border: 1px solid #ddd;">${reportDate!"N/A"}</td>
                <td style="padding: 5px; border: 1px solid #ddd; background-color: #f5f5f5; font-weight: bold;">Current
                    Term:
                </td>
                <td style="padding: 5px; border: 1px solid #ddd;">${currentTerm!"N/A"}</td>
            </tr>
            <tr>
                <td style="padding: 5px; border: 1px solid #ddd; background-color: #f5f5f5; font-weight: bold;">Previous
                    Term:
                </td>
                <td style="padding: 5px; border: 1px solid #ddd;">${previousTerm!"N/A"}</td>
                <td style="padding: 5px; border: 1px solid #ddd; background-color: #f5f5f5; font-weight: bold;">New
                    Term:
                </td>
                <td style="padding: 5px; border: 1px solid #ddd;">${newTerm!"N/A"}</td>
            </tr>
            <tr>
                <td style="padding: 5px; border: 1px solid #ddd; background-color: #f5f5f5; font-weight: bold;">
                    Institutions Analyzed:
                </td>
                <td style="padding: 5px; border: 1px solid #ddd;" colspan="3">${institutionCount!"N/A"}</td>
            </tr>
        </table>
    </div>

    <hr style="margin: 20px 0; border: 0; border-top: 2px solid #800000;">

    <!-- Organization Summary -->
    <h2 style="margin-top: 20px; color: #003366;">Organization Summary</h2>
    <table style="border-collapse: collapse; width: 100%; margin-top: 10px; background-color: white; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
        <thead>
        <tr>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Organization Metrics
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Current Term
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Previous Term
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Change
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                % Change
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Performance
            </th>
        </tr>
        </thead>
        <tbody>
        <tr>
            <td style="text-align: left; border: 1px solid #ddd; padding: 8px;">Total Expected Fees</td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if currentTotals?? && currentTotals.expectedFee??>
                    ${currentTotals.expectedFee?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.expectedFee??>
                    ${previousTotals.expectedFee?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if expectedChange??>
                    ${expectedChange?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.expectedFee?? && previousTotals.expectedFee != 0>
                    ${(expectedChange.divide(previousTotals.expectedFee, 4, "roundHalfUp") * 100)?string["0.00"]}%
                <#else>
                    N/A
                </#if>
            </td>
            <td style="text-align: center; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.expectedFee?? && previousTotals.expectedFee != 0>
                    <#assign expectedPctChange = expectedChange.divide(previousTotals.expectedFee, 4, "roundHalfUp")>
                    <#if expectedPctChange gt 0.05>Excellent
                    <#elseif expectedPctChange gt -0.05>Good
                    <#else>Needs Attention</#if>
                <#else>
                    N/A
                </#if>
            </td>
        </tr>
        <tr>
            <td style="text-align: left; border: 1px solid #ddd; padding: 8px;">Total Collected Fees</td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if currentTotals?? && currentTotals.collectedFee??>
                    ${currentTotals.collectedFee?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.collectedFee??>
                    ${previousTotals.collectedFee?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if collectedChange??>
                    ${collectedChange?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.collectedFee?? && previousTotals.collectedFee != 0>
                    ${(collectedChange.divide(previousTotals.collectedFee, 4, "roundHalfUp") * 100)?string["0.00"]}%
                <#else>
                    N/A
                </#if>
            </td>
            <td style="text-align: center; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.collectedFee?? && previousTotals.collectedFee != 0>
                    <#assign collectedPctChange = collectedChange.divide(previousTotals.collectedFee, 4, "roundHalfUp")>
                    <#if collectedPctChange gt 0.05>Excellent
                    <#elseif collectedPctChange gt -0.05>Good
                    <#else>Needs Attention</#if>
                <#else>
                    N/A
                </#if>
            </td>
        </tr>
        <tr>
            <td style="text-align: left; border: 1px solid #ddd; padding: 8px;">Organization Collection Rate</td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if currentCollectionRate??>
                    ${(currentCollectionRate * 100)?string["0.00"]}%
                <#else>
                    0.00%
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousCollectionRate??>
                    ${(previousCollectionRate * 100)?string["0.00"]}%
                <#else>
                    0.00%
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if collectionRateChange??>
                    ${(collectionRateChange * 100)?string["0.00"]}%
                <#else>
                    0.00%
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousCollectionRate?? && previousCollectionRate != 0>
                    ${((collectionRateChange / previousCollectionRate) * 100)?string["0.00"]}%
                <#else>
                    N/A
                </#if>
            </td>
            <td style="text-align: center; border: 1px solid #ddd; padding: 8px;">
                <#if currentCollectionRate??>
                    <#if currentCollectionRate gte 0.9>Excellent
                    <#elseif currentCollectionRate gte 0.8>Good
                    <#elseif currentCollectionRate gte 0.6>Fair
                    <#else>Poor</#if>
                <#else>
                    N/A
                </#if>
            </td>
        </tr>
        <tr>
            <td style="text-align: left; border: 1px solid #ddd; padding: 8px;">Total Active Students</td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if currentTotals?? && currentTotals.studentCount??>
                    ${currentTotals.studentCount}
                <#else>
                    0
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.studentCount??>
                    ${previousTotals.studentCount}
                <#else>
                    0
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if currentTotals?? && currentTotals.studentCount?? && previousTotals?? && previousTotals.studentCount??>
                    ${currentTotals.studentCount - previousTotals.studentCount}
                <#else>
                    0
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.studentCount?? && previousTotals.studentCount != 0>
                    ${(((currentTotals.studentCount - previousTotals.studentCount) / previousTotals.studentCount) * 100)?string["0.00"]}%
                <#else>
                    N/A
                </#if>
            </td>
            <td style="text-align: center; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.studentCount?? && previousTotals.studentCount != 0>
                    <#assign studentPctChange = (currentTotals.studentCount - previousTotals.studentCount) / previousTotals.studentCount>
                    <#if studentPctChange gt 0.05>Excellent
                    <#elseif studentPctChange gt -0.05>Good
                    <#else>Needs Attention</#if>
                <#else>
                    N/A
                </#if>
            </td>
        </tr>
        <tr>
            <td style="text-align: left; border: 1px solid #ddd; padding: 8px;">Outstanding Balances</td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if currentTotals?? && currentTotals.balance??>
                    ${currentTotals.balance?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.balance??>
                    ${previousTotals.balance?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if balanceChange??>
                    ${balanceChange?string["#,##0.00"]}
                <#else>
                    0.00
                </#if>
            </td>
            <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.balance?? && previousTotals.balance.compareTo(0) != 0>
                    ${(balanceChange.divide(previousTotals.balance, 4, "roundHalfUp") * 100)?string["0.00"]}%
                <#else>
                    N/A
                </#if>
            </td>
            <td style="text-align: center; border: 1px solid #ddd; padding: 8px;">
                <#if previousTotals?? && previousTotals.balance?? && previousTotals.balance.compareTo(0) != 0>
                    <#assign balancePctChange = balanceChange.divide(previousTotals.balance, 4, "roundHalfUp")>
                    <#if balancePctChange lt -0.05>Excellent
                    <#elseif balancePctChange lt 0.05>Good
                    <#else>Needs Attention</#if>
                <#else>
                    N/A
                </#if>
            </td>
        </tr>
        </tbody>
    </table>

    <hr style="margin: 20px 0; border: 0; border-top: 2px solid #800000;">

    <!-- Institution Performance Ranking -->
    <h2 style="margin-top: 20px; color: #003366;">Institution Performance Ranking</h2>
    <table style="border-collapse: collapse; width: 100%; margin-top: 10px; background-color: white; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
        <thead>
        <tr>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Rank
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Institution
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Collection Rate
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Collected Fees
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Students
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Trend
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Rating
            </th>
        </tr>
        </thead>
        <tbody>
        <#list institutionMetrics![] as metric>
            <tr>
                <td style="text-align: center; border: 1px solid #ddd; padding: 8px;">${metric?index + 1}</td>
                <td style="text-align: left; border: 1px solid #ddd; padding: 8px;">
                    <#if metric.institutionCode?? && Q.getInstitutionName(metric.institutionCode)??>
                        ${Q.getInstitutionName(metric.institutionCode)}
                    <#else>
                        Unknown Institution
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if metric.collectionRate??>
                        ${(metric.collectionRate * 100)?string["0.00"]}%
                    <#else>
                        0.00%
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if metric.collectedFees??>
                        ${metric.collectedFees?string["#,##0.00"]}
                    <#else>
                        0.00
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if metric.studentCount??>
                        ${metric.studentCount}
                    <#else>
                        0
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if metric.trend??>
                        ${(metric.trend * 100)?string["0.00"]}%
                    <#else>
                        0.00%
                    </#if>
                </td>
                <td style="text-align: center; border: 1px solid #ddd; padding: 8px;">
                    <#if metric.collectionRate??>
                        <#if metric.collectionRate gte 0.9>Excellent
                        <#elseif metric.collectionRate gte 0.8>Good
                        <#elseif metric.collectionRate gte 0.6>Fair
                        <#else>Poor</#if>
                    <#else>
                        N/A
                    </#if>
                </td>
            </tr>
        </#list>
        </tbody>
    </table>

    <hr style="margin: 20px 0; border: 0; border-top: 2px solid #800000;">

    <!-- Institution Comparison Analysis -->
    <h2 style="margin-top: 20px; color: #003366;">Institution Comparison Analysis</h2>
    <table style="border-collapse: collapse; width: 100%; margin-top: 10px; background-color: white; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
        <thead>
        <tr>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Institution
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Expected Fees
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Collected Fees
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Collection Rate
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Outstanding
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Students
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Prev. Collected
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Change
            </th>
            <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                Performance
            </th>
        </tr>
        </thead>
        <tbody>
        <#list institutionComparisons![] as comparison>
            <tr>
                <td style="text-align: left; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.institutionCode?? && Q.getInstitutionName(comparison.institutionCode)??>
                        ${Q.getInstitutionName(comparison.institutionCode)}
                    <#else>
                        Unknown Institution
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.expectedFees??>
                        ${comparison.expectedFees?string["#,##0.00"]}
                    <#else>
                        0.00
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.collectedFees??>
                        ${comparison.collectedFees?string["#,##0.00"]}
                    <#else>
                        0.00
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.collectionRate??>
                        ${(comparison.collectionRate * 100)?string["0.00"]}%
                    <#else>
                        0.00%
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.outstanding??>
                        ${comparison.outstanding?string["#,##0.00"]}
                    <#else>
                        0.00
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.students??>
                        ${comparison.students}
                    <#else>
                        0
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.previousCollected??>
                        ${comparison.previousCollected?string["#,##0.00"]}
                    <#else>
                        0.00
                    </#if>
                </td>
                <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.changePercent??>
                        ${(comparison.changePercent * 100)?string["0.00"]}%
                    <#else>
                        0.00%
                    </#if>
                </td>
                <td style="text-align: center; border: 1px solid #ddd; padding: 8px;">
                    <#if comparison.collectionRate??>
                        <#if comparison.collectionRate gte 0.9>Excellent
                        <#elseif comparison.collectionRate gte 0.8>Good
                        <#elseif comparison.collectionRate gte 0.6>Fair
                        <#else>Poor</#if>
                    <#else>
                        N/A
                    </#if>
                </td>
            </tr>
        </#list>
        </tbody>
    </table>

    <hr style="margin: 20px 0; border: 0; border-top: 2px solid #800000;">

    <!-- Detailed Class Breakdown by Institution -->
    <h2 style="margin-top: 20px; color: #003366;">Detailed Class Breakdown by Institution</h2>
    <#list classBreakdown!{} as institutionCode, classes>
        <h3 style="margin-top: 15px; color: #003366;">
            <#if institutionCode??>
                ${institutionCode}
            <#else>
                Unknown Institution
            </#if>
        </h3>
        <table style="border-collapse: collapse; width: 100%; margin-top: 10px; background-color: white; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
            <thead>
            <tr>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Class
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Students
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Expected
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Collected
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Rate %
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Balance
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Overpayment
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Discount
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Prev Collected
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Change
                </th>
                <th style="background-color: #D3D3D3; font-weight: bold; color: #003366; border: 1px solid #ddd; padding: 8px;">
                    Trend
                </th>
            </tr>
            </thead>
            <tbody>
            <#list classes![] as classReport>
            <#-- Try to find the previous record for the same class -->
                <#assign previousClassData = ((previousDataByInstitution[institutionCode]![])?filter(p -> (p.name?? && p.name == classReport.name!""))?first)!{}>

                <tr>
                    <td style="text-align: left; border: 1px solid #ddd; padding: 8px;">
                        ${classReport.name!"Unknown Class"}
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        ${classReport.studentCount!0}
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        ${(classReport.expectedFee??)?then(classReport.expectedFee?string["#,##0.00"], "0.00")}
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        ${(classReport.collectedFee??)?then(classReport.collectedFee?string["#,##0.00"], "0.00")}
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        <#if classReport.collectedFee?? && classReport.expectedFee?? && classReport.expectedFee != 0>
                            <#assign classRate = (classReport.collectedFee
                            .divide(classReport.expectedFee, 4, "roundHalfUp")
                            .doubleValue() * 100)>
                            ${classRate?string["0.00"]}%
                        <#else>
                            0.00%
                        </#if>
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        ${(classReport.balance??)?then(classReport.balance?string["#,##0.00"], "0.00")}
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        ${(classReport.overPayment??)?then(classReport.overPayment?string["#,##0.00"], "0.00")}
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        ${(classReport.discount??)?then(classReport.discount?string["#,##0.00"], "0.00")}
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        <#if previousClassData?? && previousClassData.collectedFee??>
                            ${previousClassData.collectedFee?string["#,##0.00"]}
                        <#else>
                            0.00
                        </#if>
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        <#if classReport.collectedFee??>
                            <#if previousClassData?? && previousClassData.collectedFee??>
                                ${(classReport.collectedFee.subtract(previousClassData.collectedFee))?string["#,##0.00"]}
                            <#else>
                                ${classReport.collectedFee?string["#,##0.00"]}
                            </#if>
                        <#else>
                            0.00
                        </#if>
                    </td>

                    <td style="text-align: right; border: 1px solid #ddd; padding: 8px;">
                        <#if previousClassData?? && previousClassData.collectedFee?? && previousClassData.collectedFee.compareTo(0) != 0>
                            <#assign trend = (classReport.collectedFee.subtract(previousClassData.collectedFee))
                            .divide(previousClassData.collectedFee, 4, "roundHalfUp").doubleValue() * 100>
                            ${trend?string["0.00"]}%
                        <#elseif previousClassData??>
                            N/A
                        <#else>
                            NEW
                        </#if>
                    </td>
                </tr>
            </#list>

            </tbody>
        </table>
    </#list>

    <hr style="margin: 20px 0; border: 0; border-top: 2px solid #800000;">
</@layout.layout>
