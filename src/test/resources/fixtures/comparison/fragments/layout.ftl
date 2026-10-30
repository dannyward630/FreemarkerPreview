<#import "header.ftl" as header>
<#import "footer.ftl" as footer>
<#macro layout>
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <title>${title!"Report"}</title></head>
    <body>
    <@header.render/>
    <#nested>
    <@footer.render/>
    </body>
    </html>
</#macro>
