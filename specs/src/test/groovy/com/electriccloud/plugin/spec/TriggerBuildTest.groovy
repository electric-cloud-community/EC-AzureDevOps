package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import groovy.json.JsonSlurper
import spock.lang.*

class TriggerBuildTest extends PluginTestHelper {

    @Shared
    def procedureName = "TriggerBuild",
        projectName = "Spec Tests $procedureName"

    @Shared
    def TC = [
            C369969: [ ids: 'C369969', description: 'TriggerBuild - required fields'],
            C369970: [ ids: 'C369970', description: 'TriggerBuild - queueId, speciify name'],
            C369971: [ ids: 'C369971', description: 'TriggerBuild - queueId, speciify id'],
            C369972: [ ids: 'C369972', description: 'TriggerBuild - provide sourceBranch'],
            C369973: [ ids: 'C369973', description: 'TriggerBuild - provide Parameters'],
            C369974: [ ids: 'C369974', description: 'TriggerBuild - Result Format: json '],
            C369975: [ ids: 'C369975', description: 'TriggerBuild - custom property sheet '],
            C369976: [ ids: 'C369976', description: 'TriggerBuild - empty required fields '],
            C369977: [ ids: 'C369977', description: 'TriggerBuild - invalid values '],
    ]

    @Shared
    def summaries = [
            default: "Build values are saved to a specified property",
            emptyConfig: 'Parameter "Configuration" is mandatory',
            emptyProject: 'Parameter "Project" is mandatory',
            emptyID: 'Parameter "Definition ID or name" is mandatory',
            emptyFormat: 'Parameter "Result Format" is mandatory',

            wrongConfig: 'Configuration "wrong" does not exist',
            wrongProject: ( url == 'https://dev.azure.com') ?
                    'The following project does not exist: wrong. Verify that the name of the project is correct and that the project exists on the specified Azure DevOps Server.' :
                    'The following project does not exist: wrong. Verify that the name of the project is correct and that the project exists on the specified Team Foundation Server.',
            wrongID: 'Failed to find given definition. No definitions found for specified Build Definition Name \\(wrong\\).',
            wrongQueue: '',
            wrongBranch: '',
            wrongFormat: 'Cannot process format wrong: not implemented\n\n'
    ]

    @Shared
    TFSHelper tfsClient

    @Shared
    def defaultDefinitionId = getAssertedEnvVariable("BUILD_DEFINITION_NAME")

    @Shared
    def queue = (url == 'https://dev.azure.com') ? [
            id: '97',
            name: 'Hosted VS2017'
        ] :
        [
            id: '1',
            name: 'Default'
        ]

    @Shared
    def parametersExample = 'system.debug=true\nBuildConfiguration=debug\nBuildPlatform=x64'

    @Shared
    def additionalBranch = 'release'


    def doSetupSpec() {
        tfsClient = getClient()
        createPluginConfigurationWithProxy(configName, configurationParams, credential, token_credential, proxy_credential)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: procedureName, params: triggerBuildParams]
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)

    }


    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'QueryWorkItemsTests Sanity #caseId.ids #caseId.description'() {
        given:

        def triggerBuildParameters = [
                "config": configuration,
                "definitionId": definitionId,
                "parameters": defParameters,
                "project": project,
                "queueId": queueId,
                "resultFormat": resultFormat,
                "resultPropertySheet":resultPropertySheet,
                "sourceBranch": sourceBranch,
        ]
        when:

        def result = runProcedure(projectName, procedureName, triggerBuildParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]

        if (resultFormat == 'json') {
            jobProperties = new JsonSlurper().parseText(jobProperties)
            for (j in jobProperties){
                j.value = j.value.toString()
            }
        }

        def buildId = jobProperties['buildNumber']
        def buildInfoFromTfs = tfsClient.getBuild(Integer.parseInt(buildId))
        def expectedKeys = ['buildNumber', 'definition', 'id', 'keepForever', 'lastChangedDate', 'priority', 'project', 'queue', 'queueTime', 'reason', 'repository', 'retainedByRelease', 'sourceBranch', 'status', 'triggeredByBuild', 'uri', 'url', ]
        if (defParameters){
            expectedKeys += 'parameters'
        }
        if (resultFormat == 'json'){
            expectedKeys += 'triggerInfo'
            expectedKeys += 'properties'
            expectedKeys += 'tags'
            expectedKeys += 'validationResults'
        }
        expectedKeys = expectedKeys.sort()

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary =~ summary
            }
            jobProperties.keySet().sort() == expectedKeys
            jobProperties['buildNumber'] == buildInfoFromTfs.'buildNumber'
            jobProperties['definition'] == buildInfoFromTfs.'definition'.'name'
            jobProperties['id'] == buildInfoFromTfs.'id'.toString()
            jobProperties['keepForever'] == buildInfoFromTfs.'keepForever'.toString()
            jobProperties['lastChangedDate']
            jobProperties['priority'] == buildInfoFromTfs.'priority'
            jobProperties['project'] == buildInfoFromTfs.'project'.'name'
            jobProperties['queue'] == buildInfoFromTfs.'queue'.'pool'.'name'
            jobProperties['queueTime'] == buildInfoFromTfs.'queueTime'
            jobProperties['reason'] == buildInfoFromTfs.'reason'
            jobProperties['repository'] == buildInfoFromTfs.'repository'.'name'
            jobProperties['retainedByRelease'] == buildInfoFromTfs.'retainedByRelease'.toString()
            jobProperties['sourceBranch'] == buildInfoFromTfs.'sourceBranch'
            if (sourceBranch) {
                jobProperties['sourceBranch'].contains(sourceBranch)
            } else {
                jobProperties['sourceBranch'].contains('master')
            }
            jobProperties['status']
            jobProperties['triggeredByBuild'] == ( buildInfoFromTfs.'triggeredByBuild' != 'null'  ? buildInfoFromTfs.'triggeredByBuild' :
                    (resultFormat == "propertySheet" ? '' : 'null') )
            jobProperties['uri'] == buildInfoFromTfs.'uri'
            jobProperties['url'] == buildInfoFromTfs.'url'
            if (defParameters) {
                jobProperties['parameters'] == buildInfoFromTfs.'parameters'
            }
        }
        cleanup:
        if (buildId) {
            try {
                println("Start waiting for build ending")
                tfsClient.waitForBuild(buildId.toInteger(), 300)
                println("Fininsh waiting build ending")
            } catch (RuntimeException e){
                println("Build has not finished after timeout")
            }
        }
        where:
        caseId     | configuration | definitionId           | defParameters     | project         | queueId         |  sourceBranch     | resultFormat    | resultPropertySheet   | summary           | outcome
        TC.C369969 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.default | 'success'
        TC.C369969 | configName    | defaultDefinitionId    | parametersExample | tfsProjectName  |  queue.name     | additionalBranch  | 'propertySheet' | '/myJob/queueBuild'   | summaries.default | 'success'
    }


    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'QueryWorkItemsTests Positive #caseId.ids #caseId.description'() {
        given:

        def triggerBuildParameters = [
                "config": configuration,
                "definitionId": definitionId,
                "parameters": defParameters,
                "project": project,
                "queueId": queueId,
                "resultFormat": resultFormat,
                "resultPropertySheet":resultPropertySheet,
                "sourceBranch": sourceBranch,
        ]
        when:

        def result = runProcedure(projectName, procedureName, triggerBuildParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]

        if (resultFormat == 'json') {
            jobProperties = new JsonSlurper().parseText(jobProperties)
            for (j in jobProperties){
                j.value = j.value.toString()
            }
        }

        def buildId = jobProperties['buildNumber']
        def buildInfoFromTfs = tfsClient.getBuild(Integer.parseInt(buildId))
        def expectedKeys = ['buildNumber', 'definition', 'id', 'keepForever', 'lastChangedDate', 'priority', 'project', 'queue', 'queueTime', 'reason', 'repository', 'retainedByRelease', 'sourceBranch', 'status', 'triggeredByBuild', 'uri', 'url', ]
        if (defParameters){
            expectedKeys += 'parameters'
        }
        if (resultFormat == 'json'){
            expectedKeys += 'triggerInfo'
            expectedKeys += 'properties'
            expectedKeys += 'tags'
            expectedKeys += 'validationResults'
        }
        expectedKeys = expectedKeys.sort()

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary =~ summary
            }
            jobProperties.keySet().sort() == expectedKeys
            jobProperties['buildNumber'] == buildInfoFromTfs.'buildNumber'
            jobProperties['definition'] == buildInfoFromTfs.'definition'.'name'
            jobProperties['id'] == buildInfoFromTfs.'id'.toString()
            jobProperties['keepForever'] == buildInfoFromTfs.'keepForever'.toString()
            jobProperties['lastChangedDate']
            jobProperties['priority'] == buildInfoFromTfs.'priority'
            jobProperties['project'] == buildInfoFromTfs.'project'.'name'
            jobProperties['queue'] == buildInfoFromTfs.'queue'.'pool'.'name'
            jobProperties['queueTime'] == buildInfoFromTfs.'queueTime'
            jobProperties['reason'] == buildInfoFromTfs.'reason'
            jobProperties['repository'] == buildInfoFromTfs.'repository'.'name'
            jobProperties['retainedByRelease'] == buildInfoFromTfs.'retainedByRelease'.toString()
            jobProperties['sourceBranch'] == buildInfoFromTfs.'sourceBranch'
            if (sourceBranch) {
                jobProperties['sourceBranch'].contains(sourceBranch)
            } else {
                jobProperties['sourceBranch'].contains('master')
            }
            jobProperties['status']
            jobProperties['triggeredByBuild'] == ( buildInfoFromTfs.'triggeredByBuild' != 'null'  ? buildInfoFromTfs.'triggeredByBuild' :
                                                                                                    (resultFormat == "propertySheet" ? '' : 'null') )
            jobProperties['uri'] == buildInfoFromTfs.'uri'
            jobProperties['url'] == buildInfoFromTfs.'url'
            if (defParameters) {
                jobProperties['parameters'] == buildInfoFromTfs.'parameters'
            }
        }
        cleanup:
        if (buildId) {
            try {
                println("Start waiting for build ending")
                tfsClient.waitForBuild(buildId.toInteger(), 300)
                println("Fininsh waiting build ending")
            } catch (RuntimeException e){
                println("Build has not finished after timeout")
            }
        }
        where:
        caseId     | configuration | definitionId           | defParameters     | project         | queueId         |  sourceBranch     | resultFormat    | resultPropertySheet   | summary           | outcome
        TC.C369969 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.default | 'success'
        TC.C369970 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | queue.name      |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.default | 'success'
        TC.C369971 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | queue.id        |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.default | 'success'
        TC.C369972 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | ''              |  additionalBranch | 'propertySheet' | '/myJob/queueBuild'   | summaries.default | 'success'
        TC.C369973 | configName    | defaultDefinitionId    | parametersExample | tfsProjectName  | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.default | 'success'
        TC.C369974 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | ''              |  ''               | 'json'          | '/myJob/queueBuild'   | summaries.default | 'success'
        TC.C369975 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | queue.name      |  ''               | 'propertySheet' | '/myJob/QA'           | summaries.default | 'success'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'QueryWorkItemsTests Negative #caseId.ids #caseId.description'() {
        given:

        def triggerBuildParameters = [
                "config": configuration,
                "definitionId": definitionId,
                "parameters": defParameters,
                "project": project,
                "queueId": queueId,
                "resultFormat": resultFormat,
                "resultPropertySheet":resultPropertySheet,
                "sourceBranch": sourceBranch,
        ]
        when:

        def result = runProcedure(projectName, procedureName, triggerBuildParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary =~ summary
            }
        }
        cleanup:

        where:
        caseId     | configuration | definitionId           | defParameters     | project         | queueId         |  sourceBranch     | resultFormat    | resultPropertySheet   | summary               | outcome
        TC.C369976 | ''            | defaultDefinitionId    | ''                | tfsProjectName  | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.emptyConfig | 'error'
        TC.C369976 | configName    | ''                     | ''                | tfsProjectName  | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.emptyID     | 'error'
        TC.C369976 | configName    | defaultDefinitionId    | ''                | ''              | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.emptyProject| 'error'
        TC.C369976 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | ''              |  ''               | ''              | '/myJob/queueBuild'   | summaries.emptyFormat | 'error'
        TC.C369977 | 'wrong'       | defaultDefinitionId    | ''                | tfsProjectName  | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.wrongConfig | 'error'
        TC.C369977 | configName    | 'wrong'                | ''                | tfsProjectName  | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.wrongID     | 'error'
        TC.C369977 | configName    | defaultDefinitionId    | ''                | 'wrong'         | ''              |  ''               | 'propertySheet' | '/myJob/queueBuild'   | summaries.wrongProject| 'error'
        TC.C369977 | configName    | defaultDefinitionId    | ''                | tfsProjectName  | ''              |  ''               | 'wrong'         | '/myJob/queueBuild'   | summaries.wrongFormat | 'error'
    }

}