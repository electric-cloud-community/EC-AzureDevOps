package com.electriccloud.plugin.spec

import groovy.json.JsonSlurper
import spock.lang.*
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

@Stepwise
class GetDefaultValuesTests extends PluginTestHelper {

    @Shared
    def procedureName = "GetDefaultValues",
        projectName = "Spec Tests $procedureName"

    @Shared
    def TC = [
            C369836: [ ids: 'C369836', description: 'GetDefaultValues - Bug '],
            C369837: [ ids: 'C369837', description: 'GetDefaultValues - Epic '],
            C369838: [ ids: 'C369838', description: 'GetDefaultValues - Feature'],
            C369839: [ ids: 'C369839', description: 'GetDefaultValues - Issue'],
            C369840: [ ids: 'C369840', description: 'GetDefaultValues - User Story'],
            C369841: [ ids: 'C369840', description: 'GetDefaultValues - json format'],
            C369842: [ ids: 'C369840', description: 'GetDefaultValues - Result Property Sheet'],
            C369843: [ ids: 'C369843', description: 'Empty required fields'],
            C369844: [ ids: 'C369844', description: 'Wrong values'],

    ]

    @Shared
    def summaries = [
            default: 'Default values were saved to a PATH',
            emptyConfig: 'Parameter "Configuration name" is mandatory\n\n',
            emptyProject: 'Parameter "Project" is mandatory\n\n',
            emptyType: 'Parameter "Work Item Type" is mandatory\n\n',
            emptyFormat: 'Parameter "Result Format" is mandatory\n\n',
            emptySheet: 'Parameter "Result Property Sheet" is mandatory\n\n',
            wrongConfig: 'Configuration "wrong" does not exist\n\n',
            wrongFormat: 'Cannot process format wrong: not implemented\n\n'
    ]

    def doSetupSpec() {
        createPluginConfigurationWithProxy(configName, configurationParams, credential, token_credential, proxy_credential)
        def configWrongUrl = configurationParams.clone()
        def configWrongToken = configurationParams.clone()
        def wrongCreds = credential.clone()
        configWrongUrl.endpoint = 'http://wrong:8080/tfs'
        configWrongToken.credential = 'wrongToken'
        wrongCreds.credentialName = 'wrongToken'
        wrongCreds.password = 'wrongToken123'
//        createPluginConfigurationWithProxy(wrongConfigNames['url'], configWrongUrl, credential, proxy_credential)
//        createPluginConfigurationWithProxy(wrongConfigNames['token'], configWrongToken, wrongCreds, proxy_credential)

        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: procedureName, params: getDefaultValuesParams]
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
//        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['url'])
//        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['token'])
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'GetDefaultValues Sanity #caseId.ids #caseId.description'() {
        given:
        def getDefaultValuesParameters = [
                "config" : configuration,
                "project" : project,
                "resultFormat" : resultFormat,
                "resultPropertySheet" : resultPropertySheet,
                "workItemType" : type,
        ]
        when:
        def result = runProcedure(projectName, procedureName, getDefaultValuesParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]

        type = (type == "User Story") ? "User%20Story" : type
        def jsonResult = getDefaultValues(type)
        for (j in jsonResult){
            j.value = j.value.toString()
        }
        if (resultFormat == 'json') {
            jobProperties = new JsonSlurper().parseText(jobProperties)
            for (j in jobProperties){
                j.value = j.value.toString()
            }
        }
        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary == summary.replace("PATH", resultPropertySheet)
            }
            if (url == 'https://dev.azure.com') {
                jsonResult.keySet().containsAll(jobProperties.keySet())
            }
            else {
                jobProperties.sort().equals(jsonResult.sort())
            }
        }
        cleanup:

        where:
        caseId     |  configuration  | project          | type         | resultFormat     | resultPropertySheet        | summary             | outcome
        TC.C369840 |  configName     | tfsProjectName   | "User Story" | 'propertySheet'  | '/myJob/defaultValues'     | summaries.default   | 'success'
        TC.C369841 |  configName     | tfsProjectName   | "Bug"        | 'json'           | '/myJob/defaultValues'     | summaries.default   | 'success'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'GetDefaultValues Positive #caseId.ids #caseId.description'() {
        given:
        def getDefaultValuesParameters = [
                "config" : configuration,
                "project" : project,
                "resultFormat" : resultFormat,
                "resultPropertySheet" : resultPropertySheet,
                "workItemType" : type,
        ]
        when:
        def result = runProcedure(projectName, procedureName, getDefaultValuesParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]

        type = (type == "User Story") ? "User%20Story" : type
        def jsonResult = getDefaultValues(type)
        for (j in jsonResult){
            j.value = j.value.toString()
        }
        if (resultFormat == 'json') {
            jobProperties = new JsonSlurper().parseText(jobProperties)
            for (j in jobProperties){
                j.value = j.value.toString()
            }
        }
        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary == summary.replace("PATH", resultPropertySheet)
            }
            if (url == 'https://dev.azure.com') {
                jsonResult.keySet().containsAll(jobProperties.keySet())
            }
            else {
                jobProperties.sort().equals(jsonResult.sort())
            }
        }
        cleanup:

        where:
        caseId     |  configuration  | project          | type         | resultFormat     | resultPropertySheet        | summary             | outcome
        TC.C369836 |  configName     | tfsProjectName   | "Bug"        | 'propertySheet'  | '/myJob/defaultValues'     | summaries.default   | 'success'
        TC.C369837 |  configName     | tfsProjectName   | "Epic"       | 'propertySheet'  | '/myJob/defaultValues'     | summaries.default   | 'success'
        TC.C369838 |  configName     | tfsProjectName   | "Feature"    | 'propertySheet'  | '/myJob/defaultValues'     | summaries.default   | 'success'
        TC.C369839 |  configName     | tfsProjectName   | "Issue"      | 'propertySheet'  | '/myJob/defaultValues'     | summaries.default   | 'success'
        TC.C369840 |  configName     | tfsProjectName   | "User Story" | 'propertySheet'  | '/myJob/defaultValues'     | summaries.default   | 'success'
        TC.C369841 |  configName     | tfsProjectName   | "Bug"        | 'json'           | '/myJob/defaultValues'     | summaries.default   | 'success'
        TC.C369842 |  configName     | tfsProjectName   | "Bug"        | 'propertySheet'  | '/myJob/QA'                | summaries.default   | 'success'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'GetDefaultValues Negative #caseId.ids #caseId.description'() {
        given:
        def getDefaultValuesParameters = [
                "config" : configuration,
                "project" : project,
                "resultFormat" : resultFormat,
                "resultPropertySheet" : resultPropertySheet,
                "workItemType" : type,
        ]
        when:
        def result = runProcedure(projectName, procedureName, getDefaultValuesParameters)
        def jobSummary
        if (summary) {
            jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        }

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary == summary
            }
        }
        cleanup:

        where:
        caseId     |  configuration  | project          | type         | resultFormat     | resultPropertySheet        | summary                | outcome
        TC.C369843 |  ''             | tfsProjectName   | "Bug"        | 'propertySheet'  | '/myJob/defaultValues'     | summaries.emptyConfig  | 'error'
        TC.C369843 |  configName     | ''               | "Bug"        | 'propertySheet'  | '/myJob/defaultValues'     | summaries.emptyProject | 'error'
        TC.C369843 |  configName     | tfsProjectName   | ""           | 'propertySheet'  | '/myJob/defaultValues'     | summaries.emptyType    | 'error'
        TC.C369843 |  configName     | tfsProjectName   | "Bug"        | ''               | '/myJob/defaultValues'     | summaries.emptyFormat  | 'error'
        TC.C369843 |  configName     | tfsProjectName   | "Bug"        | 'propertySheet'  | ''                         | summaries.emptySheet   | 'error'
        TC.C369844 |  'wrong'        | tfsProjectName   | "Bug"        | 'propertySheet'  | '/myJob/defaultValues'     | summaries.wrongConfig  | 'error'
        TC.C369844 |  configName     | 'wrong'          | "Bug"        | 'propertySheet'  | '/myJob/defaultValues'     | ''                     | 'error'
        TC.C369844 |  configName     | tfsProjectName   | "wrong"      | 'propertySheet'  | '/myJob/defaultValues'     | ''                     | 'error'
        TC.C369844 |  configName     | tfsProjectName   | "Bug"        | 'wrong'          | '/myJob/defaultValues'     | summaries.wrongFormat  | 'error'
    }

    def getDefaultValues(type){
        def http = new HTTPBuilder("$url/$collectionName/$tfsProjectName/_apis/wit/workitems/\$$type")
        http.ignoreSSLIssues()
        def authData
        if (authType == 'pat') {
            authData = ':' + token
        }
        else {
            authData = domain_name + '\\' + userName + ':' + password
        }
        def authHeaderValue = "Basic ${authData.bytes.encodeBase64().toString()}"
        def jsonResult = http.request(GET, JSON){
            println uri.toString()
            headers.'Authorization' = authHeaderValue
            response.success = { resp, json ->
                return json.get('fields')
            }
        }
        return jsonResult
    }

}
