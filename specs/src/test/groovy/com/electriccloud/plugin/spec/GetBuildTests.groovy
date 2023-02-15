package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import groovy.json.JsonSlurper
import spock.lang.*

class GetBuildTests extends PluginTestHelper {

    @Shared
    def procedureName = "GetBuild",
        projectName = "Spec Tests $procedureName",
        definitionName = getBuildDefinitionName(),
        ADOSProject = getADOSProjectName(),
        buildID

    @Shared
    def TC = [
            C369932: [ ids: 'C369932', description: 'only required fields - sheet'],
            C369968: [ ids: 'C369968', description: 'only required fields - json'],
            C369933: [ ids: 'C369933', description: 'all fields'],
            C369934: [ ids: 'C369934', description: 'BuildName*'],
            C369952: [ ids: 'C369952', description: '*BuildName'],
            C369935: [ ids: 'C369935', description: 'Wait for Build - default time'],
            C369953: [ ids: 'C369953', description: 'Wait for Build - definite time'],
            C369936: [ ids: 'C369936', description: 'run without required field \'config\''],
            C369957: [ ids: 'C369957', description: 'run without required field \'project\''],
            C369958: [ ids: 'C369958', description: 'run without required field \'buildId\''],
            C369965: [ ids: 'C369965', description: 'run without required field \'resultFormat\''],
            C369959: [ ids: 'C369959', description: 'run without required field \'resultPropertySheet\''],
            C369949: [ ids: 'C369949', description: 'run with invalid value of \'config\''],
            C369960: [ ids: 'C369960', description: 'run with invalid value of \'project\''],
            C369966: [ ids: 'C369966', description: 'run with invalid value of \'resultFormat\''],
            C369961: [ ids: 'C369961', description: 'run with invalid value of \'waitTimeout\''],
            C369937: [ ids: 'C369937', description: 'Build Number without Build Definition Name'],
            C369944: [ ids: 'C369944', description: 'build ID isn\'t existed'],

    ]

    @Shared
    def summaries = [
            success: 'Build values are saved to a specified property',
            emptyConfig: 'Parameter "Configuration name" is mandatory\n\n',
            emptyProject: 'Parameter "Project" is mandatory\n\n',
            emptyBuidId: 'Parameter "Build Id or Number" is mandatory\n\n',
            emptyFormat: 'Parameter "Result Format" is mandatory\n\n',
            emptySheet: 'Parameter "Result Property Sheet" is mandatory\n\n',
            emptyBuildDefinitionName: 'Parameter \'Build Definition Name\' is required if you\'ve specified Build number in a \'Build Id of Number\' parameter.\n\n',
            wrongConfig: 'Configuration "invalidValue" does not exist\n\n',
            wrongProject: 'The following project does not exist: invalidValue',
            wrongWaitTimeout: 'Parameter \'Wait Timeout\' check failed: Parameter \'Wait Timeout\' should contain number, but has \'invalidValue\'.\n\n',
            wrongBuildId: (url == 'https://dev.azure.com') ? 'The requested build 10000 could not be found.':
                'Requested build 10000 could not be found.\n\n',
            wrongFormat: 'Cannot process format invalidValue: not implemented\n\n'
    ]

    @Shared
    TFSHelper tfsClient

    def doSetupSpec() {
        tfsClient = getClient()
        buildID = tfsClient.triggerBuild(definitionName)['id']
        createPluginConfigurationWithProxy(configName, configurationParams, credential, token_credential, proxy_credential)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: procedureName, params: getBuild]
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'GetBuild Sanity #caseId.ids #caseId.description'() {
        given:
        def getBuildParameters = [
                config : configName,
                project : project,
                buildId : buildId,
                buildDefinitionName : buildDefinitionN,
                waitForBuild : waitForBuild,
                waitTimeout : waitTimeout,
                resultPropertySheet : resultPropertySheet,
                resultFormat : resultFormat
        ]

        when:
        def result = runProcedure(projectName, "GetBuild", getBuildParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        if (resultFormat == 'json') {
            jobProperties = new JsonSlurper().parseText(jobProperties)
            for (j in jobProperties){
                j.value = j.value.toString()
            }
        }
        int buildNumber = java.lang.Integer.parseInt(jobProperties['buildNumber'])
        def procedureResponse = tfsClient.getBuild(buildNumber)

        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == summaries.success

            jobProperties['project'] == procedureResponse['project']['name']
            jobProperties['buildNumber'] == procedureResponse['buildNumber']
            if (buildDefinitionName){
                jobProperties['definition'] == procedureResponse['definition']['name']
            }
        }
        cleanup:

        where:
        caseId       | project     | buildId | buildDefinitionN | waitForBuild | waitTimeout   | resultFormat    | resultPropertySheet
        TC.C369932   | ADOSProject | buildID | ''               | '0'          | ''            | 'propertySheet' | '/myJob/newWorkItems'
        TC.C369953   | ADOSProject | "${buildID.toString()[0]}*" | definitionName   | '1'          | '30'          | 'propertySheet' | '/myJob/newWorkItems'
    }


    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'GetBuild Positive #caseId.ids #caseId.description'() {
        given:
        def getBuildParameters = [
                config : configName,
                project : project,
                buildId : buildId,
                buildDefinitionName : buildDefinitionN,
                waitForBuild : waitForBuild,
                waitTimeout : waitTimeout,
                resultPropertySheet : resultPropertySheet,
                resultFormat : resultFormat
        ]

        when:
        def result = runProcedure(projectName, "GetBuild", getBuildParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        if (resultFormat == 'json') {
            jobProperties = new JsonSlurper().parseText(jobProperties)
            for (j in jobProperties){
                j.value = j.value.toString()
            }
        }
        int buildNumber = java.lang.Integer.parseInt(jobProperties['buildNumber'])
        def procedureResponse = tfsClient.getBuild(buildNumber)

        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == summaries.success

            jobProperties['project'] == procedureResponse['project']['name']
            jobProperties['buildNumber'] == procedureResponse['buildNumber']
            if (buildDefinitionName){
                jobProperties['definition'] == procedureResponse['definition']['name']
            }
        }
        cleanup:

        where:
        caseId       | project     | buildId | buildDefinitionN | waitForBuild | waitTimeout   | resultFormat    | resultPropertySheet
        TC.C369932   | ADOSProject | buildID | ''               | '0'          | ''            | 'propertySheet' | '/myJob/newWorkItems'
        TC.C369968   | ADOSProject | buildID | ''               | '0'          | ''            | 'json'          | '/myJob/newWorkItems'
        TC.C369933   | ADOSProject | "${buildID.toString()[0]}*" | definitionName   | '1'          | 10 + (60 * 3) | 'propertySheet' | '/myJob/newWorkItems'
//        TC.C369934   | ADOSProject | '2*'    | definitionName   | '0'          | ''            | 'propertySheet' | '/myJob/newWorkItems'
        TC.C369952   | ADOSProject | "*${buildID.toString()[-1]}" | definitionName   | '0'          | ''            | 'propertySheet' | '/myJob/newWorkItems'
        TC.C369935   | ADOSProject | "*${buildID.toString()[-1]}" | definitionName   | '1'          | 300           | 'propertySheet' | '/myJob/newWorkItems'
        TC.C369953   | ADOSProject | "${buildID.toString()[0]}*"  | definitionName   | '1'          | 15            | 'propertySheet' | '/myJob/newWorkItems'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'GetBuild Negative #caseId.ids #caseId.description'() {
        given:
        def getBuildParameters = [
                config : configTest,
                project : project,
                buildId : buildId,
                buildDefinitionName : buildDefinitionN,
                waitForBuild : waitForBuild,
                waitTimeout : waitTimeout,
                resultPropertySheet : resultPropertySheet,
                resultFormat : resultFormat
        ]

        when:
        def result = runProcedure(projectName, "GetBuild", getBuildParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        then:
        verifyAll {
            result.outcome == 'error'
            if (summary) {
                jobSummary.contains(summary)
            }
        }
        cleanup:

        where:
        caseId       | configTest     | project        | buildId           | buildDefinitionN | waitForBuild   | waitTimeout   | resultFormat    | resultPropertySheet    | summary
        TC.C369936   | ''             | ADOSProject    | buildID           | ''               | '0'            | ''            | 'propertySheet' | '/myJob/newWorkItems'  | summaries.emptyConfig
        TC.C369957   | configName     | ''             | buildID           | ''               | '0'            | ''            | 'propertySheet' | '/myJob/newWorkItems'  | summaries.emptyProject
        TC.C369958   | configName     | ADOSProject    | ''                | ''               | '0'            | ''            | 'propertySheet' | '/myJob/newWorkItems'  | summaries.emptyBuidId
        TC.C369965   | configName     | ADOSProject    | buildID           | ''               | '0'            | ''            | ''              | '/myJob/newWorkItems'  | summaries.emptyFormat
        TC.C369959   | configName     | ADOSProject    | buildID           | ''               | '0'            | ''            | 'propertySheet' | ''                     | summaries.emptySheet
        TC.C369949   | 'invalidValue' | ADOSProject    | buildID           | ''               | '0'            | ''            | 'propertySheet' | '/myJob/newWorkItems'  | summaries.wrongConfig
        TC.C369960   | configName     | 'invalidValue' | '1*'              | definitionName   | '0'            | ''            | 'propertySheet' | '/myJob/newWorkItems'  | summaries.wrongProject
        TC.C369966   | configName     | ADOSProject    | buildID           | ''               | '0'            | ''            | 'invalidValue'  | '/myJob/newWorkItems'  | summaries.wrongFormat
        TC.C369961   | configName     | ADOSProject    | buildID           | ''               | '1'            | 'invalidValue'| 'propertySheet' | '/myJob/newWorkItems'  | summaries.wrongWaitTimeout
        TC.C369937   | configName     | ADOSProject    | '1*'              | ''               | '0'            | ''            | 'propertySheet' | '/myJob/newWorkItems'  | summaries.emptyBuildDefinitionName
        TC.C369944   | configName     | ADOSProject    | 10000             | ''               | '0'            | ''            | 'propertySheet' | '/myJob/newWorkItems'  | summaries.wrongBuildId
    }
}