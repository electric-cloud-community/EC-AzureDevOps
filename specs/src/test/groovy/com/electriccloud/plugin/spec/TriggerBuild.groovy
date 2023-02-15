package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import spock.lang.*

@IgnoreIf({System.getenv("DEV_TESTS") != 'true'})
class TriggerBuild extends PluginTestHelper {

    static String procedureName = "TriggerBuild"
    static String projectName = "Spec Tests $procedureName"
    static String configName = "config_${procedureName}"

    @Shared
    TFSHelper tfsClient

    /// Procedure parameters
    // Mandatory
    @Shared
    def config = configName
    @Shared
    def project = getADOSProjectName()
    @Shared
    def definitionId
    @Shared
    def resultPropertySheet
    @Shared
    def resultFormat

    // Optional
    @Shared
    def queueId
    @Shared
    def sourceBranch
    @Shared
    def parameters

    /// Specs parameters
    @Shared
    def caseId,
        expectedSummary,
        expectedOutcome

    static definitionName = getAssertedEnvVariable("BUILD_DEFINITION_NAME")
    static queueName = System.getenv("BUILD_QUEUE_NAME") ?: 'Default'

    static parametersCase = [
        empty   : '',
        simple  : 'system.debug=true',
        advanced: '''system.debug=true
dryRun=true'''
    ]

    def doSetupSpec() {
        createConfiguration(configName)
        dslFile "dsl/$procedureName/procedure.dsl", [projectName: projectName]

        tfsClient = getClient()
        assert tfsClient
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
        conditionallyDeleteProject(projectName)
    }

    @Unroll
    def "#caseId. Smoke. Trigger Build using definition #definition"() {
        given:
        def resultJobPropertyName = 'build'
        resultPropertySheet = '/myJob/' + resultJobPropertyName
        resultFormat = 'propertySheet'
        project = getADOSProjectName()

        if (definition == 'name') {
            definitionId = buildDefinitionName
        } else {
            definitionId = tfsClient.getDefinitionIdByName(buildDefinitionName)
        }

        def procedureParams = [
            'config'             : config,
            'project'            : project,
            'definitionId'       : definitionId,
            'queueId'            : queueId,
            'sourceBranch'       : sourceBranch,
            'parameters'         : parameters,
            'resultPropertySheet': resultPropertySheet,
            'resultFormat'       : resultFormat,
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)

        then:
        println getJobLink(result.jobId)

        assert result.outcome == 'success'
        def jobProperties = getJobProperties(result.jobId)

        Map buildInfo = (Map) jobProperties[resultJobPropertyName]
        int buildId = Integer.valueOf((String) buildInfo.id)
        assert buildId

        cleanup:
        if (buildId) {
            try {
                tfsClient.waitForBuild(buildId, 300)
            } catch (RuntimeException e){
                println("Build has not finished after timeout")
            }
        }
        where:
        caseId       | definition | buildDefinitionName
        'CHANGEME_1' | 'id'       | definitionName
        'CHANGEME_2' | 'name'     | definitionName
    }

    @Unroll
    def "#caseId. Smoke. Trigger Build using Queue #queue"() {
        given:
        def resultJobPropertyName = 'build'
        resultPropertySheet = '/myJob/' + resultJobPropertyName
        resultFormat = 'propertySheet'
        project = getADOSProjectName()
        definitionId = definitionName

        if (queue == 'name') {
            queueId = buildQueueName
        } else {
            queueId = tfsClient.getQueueIdByName(buildQueueName)
        }

        def procedureParams = [
            'config'             : config,
            'project'            : project,
            'definitionId'       : definitionId,
            'queueId'            : queueId,
            'sourceBranch'       : sourceBranch,
            'parameters'         : parameters,
            'resultPropertySheet': resultPropertySheet,
            'resultFormat'       : resultFormat,
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)

        then:
        println getJobLink(result.jobId)

        assert result.outcome == 'success'
        def jobProperties = getJobProperties(result.jobId)

        Map buildInfo = (Map) jobProperties[resultJobPropertyName]
        int buildId = Integer.valueOf((String) buildInfo.id)
        assert buildId

        cleanup:
        if (buildId) {
            try {
                tfsClient.waitForBuild(buildId, 300)
            } catch (RuntimeException e){
                println("Build has not finished after timeout")
            }
        }
        where:
        caseId       | queue  | buildQueueName
        'CHANGEME_3' | 'id'   | queueName
        'CHANGEME_4' | 'name' | queueName
    }

    @Unroll
    def "#caseId. Smoke. Check parameters"() {
        given:
        def resultJobPropertyName = 'build'
        resultPropertySheet = '/myJob/' + resultJobPropertyName
        resultFormat = 'propertySheet'
        project = getADOSProjectName()


        definitionId = tfsClient.getDefinitionIdByName(definitionName)

        def procedureParams = [
            'config'             : config,
            'project'            : project,
            'definitionId'       : definitionId,
            'queueId'            : queueId,
            'sourceBranch'       : sourceBranch,
            'parameters'         : parameters,
            'resultPropertySheet': resultPropertySheet,
            'resultFormat'       : resultFormat,
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)

        then:
        println getJobLink(result.jobId)

        assert result.outcome == 'success'
        def jobProperties = getJobProperties(result.jobId)

        Map buildInfo = (Map) jobProperties[resultJobPropertyName]
        int buildId = Integer.valueOf((String) buildInfo.id)
        assert buildId

        cleanup:
        if (buildId) {
            try {
                tfsClient.waitForBuild(buildId, 300)
            } catch (RuntimeException e){
                println("Build has not finished after timeout")
            }
        }
        where:
        caseId       | parameters
        'CHANGEME_5' | parametersCase.simple
        'CHANGEME_6' | parametersCase.advanced
    }

}
