package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import groovy.json.JsonSlurper
import spock.lang.*



@Stepwise
class UpdateWorkItemsTests extends PluginTestHelper {

    @Shared
    def procedureName = "UpdateWorkItems",
        projectName = "Spec Tests $procedureName",
        defaultFields = [
            'Microsoft.VSTS.Common.Priority',
            'Microsoft.VSTS.Common.StateChangeDate',
            'Microsoft.VSTS.Common.ValueArea',
            'System.AreaPath',
            'System.AssignedTo',
            'System.ChangedBy',
            'System.ChangedDate',
            'System.CreatedBy',
            'System.CreatedDate',
            'System.Description',
            'System.IterationPath',
            'System.Reason',
            'System.State',
            'System.TeamProject',
            'System.Title',
            'System.WorkItemType',
            'id',
            'rev',
            'url',],
        summaries = [
                nothing: "Nothing to update.",
                oneItem: "Successfully updated COUNT work item.",
                someItem: "Successfully updated COUNT work items.",
                emptyConfig: 'Parameter "Configuration name" is mandatory\n\n',
                emptyID: 'Parameter "Work Item ID(s)" is mandatory\n\n',
                emptyFormat: 'Parameter "Result Format" is mandatory\n\n',
                wrongConfig: 'Configuration "wrongConfig" does not exist',
                wrongAddFormat: 'Failed to parse Additional Fields',
                wrongFormat: 'Cannot process format wrong: not implemented'
        ]

    @Shared
    TFSHelper tfsClient

    @Shared
    def TC = [
            C369670: [ ids: 'C369670', description: 'Update Work Items - only required fields ( Nothing to update. )'],
            C369671: [ ids: 'C369671', description: 'Update some Work Items - only required fields ( Nothing to update. )'],
            C369672: [ ids: 'C369672', description: 'Update one Work Item - update title and descrption'],
            C369673: [ ids: 'C369673', description: 'Update some Work Items - update title and descrption'],
            C369676: [ ids: 'C369676', description: 'Update some Work Items - priority and assignTO'],
            C369677: [ ids: 'C369677', description: 'Update one Work Item - update priority'],
            C369678: [ ids: 'C369678', description: 'Update one Work Item - update assignTo'],
            C369679: [ ids: 'C369679', description: 'Update one Work Item - add comment '],
            C369680: [ ids: 'C369680', description: 'Update one Work Item - update Additional Fields'],
            C369681: [ ids: 'C369681', description: 'Update one Work Item - all fields'],
            C369682: [ ids: 'C369682', description: 'Update one Work Item - all fields - json format '],
            C369683: [ ids: 'C369683', description: 'Update one Work Item - custom resultPropertySheet'],
            C369684: [ ids: 'C369684', description: 'Empty required fields'],
            C369685: [ ids: 'C369685', description: 'wrongValues'],
    ],
            addValue = [
                SystemInfo: ["Microsoft.VSTS.TCM.SystemInfo", "QA: Windows 2016"],
//                ValueArea: ["Microsoft.VSTS.Common.ValueArea", "Architectural"]
            ],
            addFields = [
                SystemInfoReproSteps: '[' +
                '{' +
                "\"path\":\"/fields/${addValue.SystemInfo[0]}\"," +
                "\"value\": \"${addValue.SystemInfo[1]}\"" +
//                '},' +
//                '{' +
//                "\"path\":\"/fields/${addValue.ValueArea[0]}\"," +
//                "\"value\": \"${addValue.ValueArea[1]}\"" +
                '}' +
                ']',
                wrongParameters: '[ { "path":"/wrong", "value": "value" }]',
                wrongFormat: '[ { "path":"/fields/Microsoft.VSTS.TCM.SystemInfo", "value: QA: Windows 2016" } ]'
            ]


    def doSetupSpec() {
        if (url == 'https://dev.azure.com'){
            userName = userEmail
            secondUserName = userEmail
        }

        tfsClient = getClient()
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

        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: procedureName, params: updateWorkItemsParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "CreateWorkItems", params: createWorkItemsParams]
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
//        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['url'])
//        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['token'])
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'UpdateWorkItems Sanity #caseId.ids #caseId.description'() {
        given:
        def defaultField = defaultFields.clone()
        defaultField += (commentBody ? 'System.History' : [])
        if (additionalFields){
            for (property in addValue){
                if (!(property.value[0] in defaultField)){
                    defaultField += property.value[0]
                }
            }
        }

        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultField - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue' :defaultIssueFields, 'Epic' : defaultField,
                     'Feature':defaultField, 'User Story': defaultField]
        def workItemIds = ''
        for (def i=0; i < itemCount; i++){
            def param = [
                    config: configuration,
                    title: "TestItem ${caseId.ids} $i",
                    project: tfsProjectName,
                    type: types.keySet()[new Random().nextInt(types.keySet().size())],
                    priority: priorities[new Random().nextInt(priorities.size())],
                    assignTo: userName,
                    description: "description for ${caseId.ids} $i",
                    additionalFields: '',
                    workItemsJSON: '',
                    resultFormat: resultFormat,
                    resultPropertySheet: resultPropertySheet,]
            createWorkItemsParameters.add(param)
            if (i == 0) {
                workItemIds += getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
            }
            else {
                workItemIds += ', ' + getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
            }
        }
//        workItemIds = workItemIds.split(', ').reverse().join(', ')
//        createWorkItemsParameters = createWorkItemsParameters.reverse()

        def updateWorkItemsParameters = [
                config : configuration,
                title : title,
                workItemIds : workItemIds,
                priority : priority,
                assignTo : assignTo,
                description : description,
                additionalFields : additionalFields,
                commentBody : commentBody,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, procedureName, updateWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]

        if (resultFormat == 'json') {
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
            }
        }


        then:
        verifyAll {
            result.outcome == outcome
            jobSummary == summary.replaceAll('COUNT', itemCount.toString())

            if ( [title, priority, assignTo, description, additionalFields, commentBody].any() ){
                for (def i=0; i < itemCount; i++) {
                    def itemId = jobProperties['workItemIds'].split(', ')[i]
                    if (url == 'https://dev.azure.com') {
                        jobProperties[itemId].keySet().containsAll(types[createWorkItemsParameters[i].type])
                    }
                    else{
                        jobProperties[itemId].keySet().sort() == types[createWorkItemsParameters[i].type].sort()
                    }
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'].toString() == (priority ?: createWorkItemsParameters[i].priority)
                    jobProperties[itemId]['System.AreaPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.AssignedTo'] =~ (!assignTo ? userName : assignTo)
                    jobProperties[itemId]['System.ChangedBy'] =~ userName
                    jobProperties[itemId]['System.CreatedBy'] =~ userName
                    jobProperties[itemId]['System.Description'] == (description ?: createWorkItemsParameters[i].description)
                    jobProperties[itemId]['System.IterationPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.Title'] == (title ?: createWorkItemsParameters[i].title)
                    jobProperties[itemId]['System.WorkItemType'] == createWorkItemsParameters[i].type
                    jobProperties[itemId]['id'].toString() == itemId
                    if (apiVersion == '5.0'){
                        jobProperties[itemId]['url'] =~ "$url/$collectionName/.*/_apis/wit/workItems/$itemId"
                    }
                    else {
                        jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/workItems/$itemId"
                    }
                    jobProperties[itemId]['System.History'] == (commentBody ?: null)
                    if (additionalFields) {
                        for (value in addValue) {
                            jobProperties[itemId][value.value[0]] == value.value[1]
                        }
                    }
                }
            }
        }
        cleanup:
        for (def i=0; i < itemCount; i++) {
            if (outcome == 'success') {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                tfsClient.deleteWorkItem(itemId)
            }
        }
        where:
        caseId     | configuration | title              | priority | assignTo       | description          | additionalFields                 | commentBody   | resultFormat     | resultPropertySheet    | itemCount | summary             | outcome
        TC.C369670 | configName    | ''                 | ''       | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 1         | summaries.nothing   | 'warning'
        TC.C369682 | configName    | 'updated title'    | '1'      | secondUserName | 'update description' | addFields.SystemInfoReproSteps   | 'new comment' | 'json'           | '/myJob/newWorkItems'  | 2         | summaries.someItem  | 'success'
    }


    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'UpdateWorkItems Positive #caseId.ids #caseId.description'() {
        given:
        def defaultField = defaultFields.clone()
        defaultField += (commentBody ? 'System.History' : [])
        if (additionalFields){
            for (property in addValue){
                if (!(property.value[0] in defaultField)){
                    defaultField += property.value[0]
                }
            }
        }

        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultField - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue' :defaultIssueFields, 'Epic' : defaultField,
                     'Feature':defaultField, 'User Story': defaultField]
        def workItemIds = ''
        for (def i=0; i < itemCount; i++){
            def param = [
                    config: configuration,
                    title: "TestItem ${caseId.ids} $i",
                    project: tfsProjectName,
                    type: types.keySet()[new Random().nextInt(types.keySet().size())],
                    priority: priorities[new Random().nextInt(priorities.size())],
                    assignTo: userName,
                    description: "description for ${caseId.ids} $i",
                    additionalFields: '',
                    workItemsJSON: '',
                    resultFormat: resultFormat,
                    resultPropertySheet: resultPropertySheet,]
            createWorkItemsParameters.add(param)
            if (i == 0) {
                workItemIds += getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
            }
            else {
                workItemIds += ', ' + getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
            }
        }
//        workItemIds = workItemIds.split(', ').reverse().join(', ')
//        createWorkItemsParameters = createWorkItemsParameters.reverse()

        def updateWorkItemsParameters = [
                config : configuration,
                title : title,
                workItemIds : workItemIds,
                priority : priority,
                assignTo : assignTo,
                description : description,
                additionalFields : additionalFields,
                commentBody : commentBody,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, procedureName, updateWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]

        if (resultFormat == 'json') {
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
            }
        }


        then:
        verifyAll {
            result.outcome == outcome
            jobSummary == summary.replaceAll('COUNT', itemCount.toString())

            if ( [title, priority, assignTo, description, additionalFields, commentBody].any() ){
                for (def i=0; i < itemCount; i++) {
                    def itemId = jobProperties['workItemIds'].split(', ')[i]
                    if (url == 'https://dev.azure.com') {
                        jobProperties[itemId].keySet().containsAll(types[createWorkItemsParameters[i].type])
                    }
                    else{
                        jobProperties[itemId].keySet().sort() == types[createWorkItemsParameters[i].type].sort()
                    }
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'].toString() == (priority ?: createWorkItemsParameters[i].priority)
                    jobProperties[itemId]['System.AreaPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.AssignedTo'] =~ (!assignTo ? userName : assignTo)
                    jobProperties[itemId]['System.ChangedBy'] =~ userName
                    jobProperties[itemId]['System.CreatedBy'] =~ userName
                    jobProperties[itemId]['System.Description'] == (description ?: createWorkItemsParameters[i].description)
                    jobProperties[itemId]['System.IterationPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.Title'] == (title ?: createWorkItemsParameters[i].title)
                    jobProperties[itemId]['System.WorkItemType'] == createWorkItemsParameters[i].type
                    jobProperties[itemId]['id'].toString() == itemId
                    if (apiVersion == '5.0'){
                        jobProperties[itemId]['url'] =~ "$url/$collectionName/.*/_apis/wit/workItems/$itemId"
                    }
                    else {
                        jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/workItems/$itemId"
                    }
                    jobProperties[itemId]['System.History'] == (commentBody ?: null)
                    if (additionalFields) {
                        for (value in addValue) {
                            jobProperties[itemId][value.value[0]] == value.value[1]
                        }
                    }
                }
            }
        }
        cleanup:
        for (def i=0; i < itemCount; i++) {
            if (outcome == 'success') {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                tfsClient.deleteWorkItem(itemId)
            }
        }
            where:
        caseId     | configuration | title              | priority | assignTo       | description          | additionalFields                 | commentBody   | resultFormat     | resultPropertySheet    | itemCount | summary             | outcome
        TC.C369670 | configName    | ''                 | ''       | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 1         | summaries.nothing   | 'warning'
        TC.C369671 | configName    | ''                 | ''       | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 2         | summaries.nothing   | 'warning'
        TC.C369672 | configName    | 'updated title'    | ''       | ''             | 'update description' | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369673 | configName    | 'updated title'    | ''       | ''             | 'update description' | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 3         | summaries.someItem  | 'success'
        TC.C369677 | configName    | ''                 | '1'      | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369678 | configName    | ''                 | ''       | secondUserName | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369676 | configName    | ''                 | '1'      | secondUserName | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 2         | summaries.someItem  | 'success'
        TC.C369679 | configName    | ''                 | ''       | ''             | ''                   | ''                               | 'new comment' | 'propertySheet'  | '/myJob/newWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369680 | configName    | ''                 | ''       | ''             | ''                   | addFields.SystemInfoReproSteps   | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369681 | configName    | 'updated title'    | '1'      | secondUserName | 'update description' | addFields.SystemInfoReproSteps   | 'new comment' | 'propertySheet'  | '/myJob/newWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369682 | configName    | 'updated title'    | '1'      | secondUserName | 'update description' | addFields.SystemInfoReproSteps   | 'new comment' | 'json'           | '/myJob/newWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369682 | configName    | 'updated title'    | '1'      | secondUserName | 'update description' | addFields.SystemInfoReproSteps   | 'new comment' | 'json'           | '/myJob/newWorkItems'  | 2         | summaries.someItem  | 'success'
        TC.C369683 | configName    | 'updated title'    | '1'      | secondUserName | 'update description' | addFields.SystemInfoReproSteps   | 'new comment' | 'propertySheet'  | '/myJob/QA'            | 1         | summaries.oneItem   | 'success'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'UpdateWorkItems Negative #caseId.ids #caseId.description'() {
        given:
        def updateWorkItemsParameters = [
                config : configuration,
                title : title,
                workItemIds : workItemIds,
                priority : priority,
                assignTo : assignTo,
                description : description,
                additionalFields : additionalFields,
                commentBody : commentBody,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, procedureName, updateWorkItemsParameters)
        def jobSummary
        if (summary) {
            jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        }
//        def debugLog = getJobLogs(result.jobId)

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary.contains(summary)
            }
        }
        cleanup:

        where:
        caseId     | configuration | workItemIds | title              | priority | assignTo       | description          | additionalFields                 | commentBody   | resultFormat     | resultPropertySheet    | summary                  | outcome | logs
        TC.C369684 | ''            | '100'       | ''                 | ''       | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | summaries.emptyConfig    | 'error' | ''
        TC.C369684 | configName    | ''          | ''                 | ''       | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | summaries.emptyID        | 'error' | ''
        TC.C369684 | configName    | '100'       | ''                 | ''       | ''             | ''                   | ''                               | ''            | ''               | '/myJob/newWorkItems'  | summaries.emptyFormat    | 'error' | ''
        TC.C369685 | 'wrongConfig' | '100'       | ''                 | ''       | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | ''                       | 'error' | ''
        TC.C369685 | configName    | 'wrong'     | ''                 | ''       | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | ''                         | 'error' | ''
        TC.C369685 | configName    | '999999999' | 'test'             | ''       | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | ''                       | 'error' | ''
        TC.C369685 | configName    | '100'       | ''                 | '999'    | ''             | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | ''                       | 'error' | ''
        TC.C369685 | configName    | '100'       | ''                 | ''       | 'wrong'        | ''                   | ''                               | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | ''                       | 'error' | ''
        TC.C369685 | configName    | '100'       | ''                 | ''       | ''             | ''                   | addFields.wrongParameters        | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | ''                       | 'error' | ''
        TC.C369685 | configName    | '100'       | ''                 | ''       | ''             | ''                   | addFields.wrongFormat            | ''            | 'propertySheet'  | '/myJob/newWorkItems'  | ''                       | 'error' | ''
   }

}