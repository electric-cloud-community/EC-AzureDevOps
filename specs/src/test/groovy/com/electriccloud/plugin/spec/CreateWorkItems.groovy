package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import spock.lang.*

@Stepwise
@IgnoreIf({System.getenv("DEV_TESTS") != 'true'})
class CreateWorkItems extends PluginTestHelper {

    static String procedureName = "CreateWorkItems"
    static String projectName = "Spec Tests $procedureName"
    static String configName = "config_${procedureName}"

    @Shared
    TFSHelper tfsClient

    /// Procedure parameters
    // Mandatory
    @Shared
    def config = configName
    @Shared
    def project
    @Shared
    def type
    @Shared
    def title

    @Shared
    def resultPropertySheet
    @Shared
    def resultFormat = 'propertySheet'

    // Optional
    @Shared
    def priority
    @Shared
    def assignTo
    @Shared
    def description
    @Shared
    def additionalFields
    @Shared
    def workItemsJSON

    /// Specs parameters
    @Shared
    def caseId,
        expectedSummary,
        expectedOutcome

    static def assignees = [
        valid  : 'Administrator',
        empty  : '',
        invalid: 'Some invalid assignee'
    ]

    static def types = [
        valid         : 'Feature',
        withDollarSign: '$Feature', // Valid, too

        empty         : '',
        unexisting    : 'Unexisting',
        invalid       : '?H3I_I_#',
    ]

    static def additionalFieldsJSON = [
        valid           : '[{"op": "add", "path": "/fields/System.State", "value": "new" }]',
        storyPoints5    : '[{"op": "add", "path": "/fields/Microsoft.VSTS.Scheduling.StoryPoints", "value": "5" }]',
        storyPoints10   : '[{"op": "add", "path": "/fields/Microsoft.VSTS.Scheduling.StoryPoints", "value": "10" }]',
        withAttributes  : '[{"op": "add", "path": "/fields/System.State", "value": "new", "attributes": {"comment": "decomposition of work"} }]',
        withoutOperation: '[{"path": "/fields/System.State", "value": "New" }]',
        empty           : '',

        // Invalid
        noPermission    : '[{"op": "add", "path": "/relations/-", "value": {"rel": "System.LinkTypes.Hierarchy-Reverse", "url": "https://fabrikam-fiber-inc.visualstudio.com/DefaultCollection/_apis/wit/workItems/297"} }]',
        withoutValue    : '[{"op": "add", "path": "/relations/-"}]',
        notJson         : 'just a simple text',
        withoutPath     : '[{"op": "add", "value": "Value that does not matter"}]'
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
    def '#caseId. Smoke. Create Single'() {
        given:
        project = getADOSProjectName()
        title = randomize(procedureName + '_' + caseId)
        description = "Delete me"

        // Will be used later to get the result
        String resultJobProperty = 'newWorkItems'
        resultPropertySheet = '/myJob/' + resultJobProperty

        Map procedureParams = [
            config             : config,
            project            : project,
            type               : type,
            title              : title,
            priority           : priority,
            assignTo           : assignTo,
            description        : description,
            additionalFields   : additionalFields,
            workItemsJSON      : workItemsJSON,
            resultPropertySheet: resultPropertySheet,
            resultFormat       : resultFormat
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobProperties = getJobProperties(result.jobId)

        then:
        println getJobLink(result.jobId)
        assert result.outcome == 'success'

        def newItemsHash = jobProperties[resultJobProperty]

        // Will contain single ID
        String newItemId = newItemsHash['workItemIds']
        Map newItem = (Map) newItemsHash[newItemId]

        assert newItem['id']
        assert newItem['System.Title'] == title

        cleanup:
        if (newItemId) {
            tfsClient.deleteWorkItem(newItemId)
        }

        where:
        caseId       | type                 | assignTo        | additionalFields
        'CHANGEME_1' | types.valid          | assignees.empty | additionalFieldsJSON.empty
        'CHANGEME_2' | types.withDollarSign | assignees.empty | additionalFieldsJSON.empty

        // This can be moved to a separate regression set
        'CHANGEME_3' | types.valid          | assignees.empty | additionalFieldsJSON.valid
        'CHANGEME_4' | types.valid          | assignees.empty | additionalFieldsJSON.withAttributes
        'CHANGEME_5' | types.valid          | assignees.empty | additionalFieldsJSON.withoutOperation
    }

    @Unroll
    def '#caseId. Smoke. Create Multiple'() {
        given:
        project = getADOSProjectName()

        def itemObjects = []
        [1, 2, 3].each { n ->
            itemObjects.push(["Title": randomize(procedureName + '_' + caseId + 'workItem' + n)])
        }
        workItemsJSON = objectToJson(itemObjects)

        description = "Delete me" + randomize("Something to identify the set")

        // Will be used later to get the result
        String resultJobProperty = 'newWorkItems'
        resultPropertySheet = '/myJob/' + resultJobProperty

        Map procedureParams = [
            config             : config,
            project            : project,
            type               : type,
            title              : title,
            priority           : priority,
            assignTo           : assignTo,
            description        : description,
            additionalFields   : additionalFields,
            workItemsJSON      : workItemsJSON,
            resultPropertySheet: resultPropertySheet,
            resultFormat       : resultFormat
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobProperties = getJobProperties(result.jobId)

        then:
        println getJobLink(result.jobId)
        assert result.outcome == 'success'

        def newItemsHash = jobProperties[resultJobProperty]

        // Will contain comma-separated IDs
        String newItemIds = newItemsHash['workItemIds']

        newItemIds.split(/,\s/).each { it ->
            def newItem = newItemsHash[it]
            assert newItem['System.Description'] == description
        }

        cleanup:
        if (newItemIds) {
            newItemIds.split(/,\s/).each { id ->
                tfsClient.deleteWorkItem(id)
            }
        }

        where:
        caseId       | type                 | assignTo
        'CHANGEME_6' | types.valid          | assignees.empty
        'CHANGEME_7' | types.withDollarSign | assignees.empty
    }

    @Unroll
    def 'CHANGEME_8. Smoke. Create Multiple with different Additional Fields'() {
        given:
        project = getADOSProjectName()
        type = "User Story"
        title = randomize(procedureName + '_' + caseId + 'workItem')

        additionalFields = additionalFieldsJSON.storyPoints5

        def itemObjects = []
        [1, 2, 3].each { n ->
            itemObjects.push(["Title": randomize(procedureName + '_' + caseId + 'workItem' + n)])
        }
        itemObjects[1]['Additional Fields'] = escapeJSON(additionalFieldsJSON.storyPoints10)
        itemObjects[2]['Additional Fields'] = additionalFieldsJSON.empty

        workItemsJSON = objectToJson(itemObjects)

        description = "Delete me" + randomize("Something to identify the set")

        // Will be used later to get the result
        String resultJobProperty = 'newWorkItems'
        resultPropertySheet = '/myJob/' + resultJobProperty

        Map procedureParams = [
            config             : config,
            project            : project,
            type               : type,
            title              : title,
            priority           : priority,
            assignTo           : assignTo,
            description        : description,
            additionalFields   : additionalFields,
            workItemsJSON      : workItemsJSON,
            resultPropertySheet: resultPropertySheet,
            resultFormat       : resultFormat
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobProperties = getJobProperties(result.jobId)

        then:
        println getJobLink(result.jobId)
        assert result.outcome == 'success'

        def newItemsHash = jobProperties[resultJobProperty]

        // Will contain comma-separated IDs
        ArrayList<Integer> newItemIds = newItemsHash['workItemIds']?.split(/,\s/)

        newItemIds.each {  it ->
            def newItem = newItemsHash[it]
            assert newItem['System.Description'] == description
        }

        assert newItemsHash[newItemIds[0]]['Microsoft.VSTS.Scheduling.StoryPoints'] == '5'
        assert newItemsHash[newItemIds[1]]['Microsoft.VSTS.Scheduling.StoryPoints'] == '10'
        assert !newItemsHash[newItemIds[2]]['Microsoft.VSTS.Scheduling.StoryPoints']

        cleanup:
        if (newItemsHash && newItemsHash['workItemIds']) {
            newItemIds.each { id ->
                tfsClient.deleteWorkItem(id)
            }
        }
    }

    String escapeJSON(String jsonString){
        return jsonString.replaceAll('"', '\\\\"')
    }

}
