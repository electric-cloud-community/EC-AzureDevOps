package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import groovy.json.JsonSlurper
import spock.lang.*

@Stepwise
class DeleteWorkItemsTests extends PluginTestHelper {

    @Shared
    def procedureName = "DeleteWorkItems",
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
           oneItem: "Successfully deleted COUNT work item.",
           someItems: "Successfully deleted COUNT work items.",
           emptyConfig: 'Parameter "Configuration name" is mandatory\n\n',
           emptyID: 'Parameter "Work Item Id(s)" is mandatory\n\n',
           emptyFormat: 'Parameter "Result Format" is mandatory\n\n',
           wrongConfig: 'Configuration "wrongConfig" does not exist\n\n',
           wrongIDs: 'Work item(s)  was not found',
           wrongFormat: 'Cannot process format wrong: not implemented\n\n'
            ]

    @Shared
    def TC = [
            C369823: [ ids: 'C369823', description: 'Delete one work item '],
            C369824: [ ids: 'C369824', description: 'Delete some workitems'],
            C369825: [ ids: 'C369825', description: 'Delete one work item -json format'],
            C369826: [ ids: 'C369826', description: 'Delete some workitems -json format'],
            C369827: [ ids: 'C369827', description: 'Delete one work item - don\'t save result'],
            C369828: [ ids: 'C369828', description: 'Delete some workitems - don\'t save result'],
            C369829: [ ids: 'C369829', description: 'Delete one work item - custom resultPropertySheet '],
            C369830: [ ids: 'C369830', description: 'empty fields '],
            C369831: [ ids: 'C369831', description: 'wrong values '],
    ]

    @Shared
    TFSHelper tfsClient

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

        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: procedureName, params: deleteWorkItemsParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "CreateWorkItems", params: createWorkItemsParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "GetWorkItems", params: getWorkItemsParams]
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
//        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['url'])
//        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['token'])
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'DeleteWorkItems Sanity #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue' :defaultIssueFields, 'Epic' : defaultFields,
                     'Feature':defaultFields, 'User Story': defaultFields]
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

        def deleteWorkItemsParameters = [
                config : configuration,
                workItemIds : workItemIds,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        def getWorkItemsParameters = [
                asOf: '',
                config: configuration,
                expandRelations: '',
                fields: '',
                resultFormat: resultFormat,
                resultPropertySheet: '/myJob/workItemsList',
                workItemIds: workItemIds,
        ]

        when:
        def result = runProcedure(projectName, procedureName, deleteWorkItemsParameters)
        def resultGetWorkItems = runProcedure(projectName, 'GetWorkItems', getWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobSummaryGetWorkItems = getJobProperty("/myJob/jobSteps/GetWorkItems/summary", resultGetWorkItems.jobId)

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
            resultGetWorkItems.outcome == 'warning'
            jobSummary == summary.replaceAll('COUNT', itemCount.toString())
            jobSummaryGetWorkItems == "Work Item(s) with the following IDs were not found: $workItemIds"
            for (def i = 0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                if (resultFormat == 'none'){
                    !jobProperties[itemId]
                }
                else {
                    if (url == 'https://dev.azure.com') {
                        jobProperties[itemId].keySet().containsAll(types[createWorkItemsParameters[i].type])
                    }
                    else{
                        jobProperties[itemId].keySet().sort() == types[createWorkItemsParameters[i].type].sort()
                    }
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'].toString() == createWorkItemsParameters[i].priority
                    jobProperties[itemId]['System.AreaPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.AssignedTo'] =~ userName
                    jobProperties[itemId]['System.ChangedBy'] =~ userName
                    jobProperties[itemId]['System.CreatedBy'] =~ userName
                    jobProperties[itemId]['System.Description'] == createWorkItemsParameters[i].description
                    jobProperties[itemId]['System.IterationPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.Title'] == createWorkItemsParameters[i].title
                    jobProperties[itemId]['System.WorkItemType'] == createWorkItemsParameters[i].type
                    jobProperties[itemId]['id'].toString() == itemId
                    if (url == 'https://dev.azure.com' && apiVersion == '5.0') {
                        jobProperties[itemId]['url'] =~ "$url/$collectionName/.*/_apis/wit/recyclebin/$itemId"
                    }
                    else {
                        jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/recyclebin/$itemId"
                    }
                }
            }

        }
        cleanup:

        where:
        caseId     | configuration  | resultFormat     | resultPropertySheet        | itemCount | summary             | outcome
        TC.C369823 | configName     | 'propertySheet'  | '/myJob/deletedWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369826 | configName     | 'json'           | '/myJob/deletedWorkItems'  | 2         | summaries.someItems | 'success'
    }




    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'DeleteWorkItems Positive #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue' :defaultIssueFields, 'Epic' : defaultFields,
                     'Feature':defaultFields, 'User Story': defaultFields]
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

        def deleteWorkItemsParameters = [
                config : configuration,
                workItemIds : workItemIds,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        def getWorkItemsParameters = [
                asOf: '',
                config: configuration,
                expandRelations: '',
                fields: '',
                resultFormat: resultFormat,
                resultPropertySheet: '/myJob/workItemsList',
                workItemIds: workItemIds,
        ]

        when:
        def result = runProcedure(projectName, procedureName, deleteWorkItemsParameters)
        def resultGetWorkItems = runProcedure(projectName, 'GetWorkItems', getWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobSummaryGetWorkItems = getJobProperty("/myJob/jobSteps/GetWorkItems/summary", resultGetWorkItems.jobId)

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
            resultGetWorkItems.outcome == 'warning'
            jobSummary == summary.replaceAll('COUNT', itemCount.toString())
            jobSummaryGetWorkItems == "Work Item(s) with the following IDs were not found: $workItemIds"
            for (def i = 0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                if (resultFormat == 'none'){
                    !jobProperties[itemId]
                }
                else {
                    if (url == 'https://dev.azure.com') {
                        jobProperties[itemId].keySet().containsAll(types[createWorkItemsParameters[i].type])
                    }
                    else{
                        jobProperties[itemId].keySet().sort() == types[createWorkItemsParameters[i].type].sort()
                    }
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'].toString() == createWorkItemsParameters[i].priority
                    jobProperties[itemId]['System.AreaPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.AssignedTo'] =~ userName
                    jobProperties[itemId]['System.ChangedBy'] =~ userName
                    jobProperties[itemId]['System.CreatedBy'] =~ userName
                    jobProperties[itemId]['System.Description'] == createWorkItemsParameters[i].description
                    jobProperties[itemId]['System.IterationPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.Title'] == createWorkItemsParameters[i].title
                    jobProperties[itemId]['System.WorkItemType'] == createWorkItemsParameters[i].type
                    jobProperties[itemId]['id'].toString() == itemId
                    if (url == 'https://dev.azure.com' && apiVersion == '5.0') {
                        jobProperties[itemId]['url'] =~ "$url/$collectionName/.*/_apis/wit/recyclebin/$itemId"
                    }
                    else {
                        jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/recyclebin/$itemId"
                    }
                }
            }

        }
        cleanup:

        where:
        caseId     | configuration  | resultFormat     | resultPropertySheet        | itemCount | summary             | outcome
        TC.C369823 | configName     | 'propertySheet'  | '/myJob/deletedWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369824 | configName     | 'propertySheet'  | '/myJob/deletedWorkItems'  | 2         | summaries.someItems | 'success'
        TC.C369825 | configName     | 'json'           | '/myJob/deletedWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369826 | configName     | 'json'           | '/myJob/deletedWorkItems'  | 2         | summaries.someItems | 'success'
        TC.C369827 | configName     | 'none'           | '/myJob/deletedWorkItems'  | 1         | summaries.oneItem   | 'success'
        TC.C369828 | configName     | 'none'           | '/myJob/deletedWorkItems'  | 2         | summaries.someItems | 'success'
        TC.C369829 | configName     | 'propertySheet'  | '/myJob/QA'                | 1         | summaries.oneItem   | 'success'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'DeleteWorkItems Negative #caseId.ids #caseId.description'() {
        given:
        if (itemCount) {
            def priorities = ['2', '3', '4']
            def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                    'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
            def types = ['Issue' :defaultIssueFields, 'Epic' : defaultFields,
                         'Feature':defaultFields, 'User Story': defaultFields]
            def param = [
                config             : configuration,
                title              : "TestItem ${caseId.ids}",
                project            : tfsProjectName,
                type               : types.keySet()[new Random().nextInt(types.keySet().size())],
                priority           : priorities[new Random().nextInt(priorities.size())],
                assignTo           : userName,
                description        : "description for ${caseId.ids}",
                additionalFields   : '',
                workItemsJSON      : '',
                resultFormat       : 'propertySheet',
                resultPropertySheet: resultPropertySheet,]
            workItemIds = getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
        }


        def deleteWorkItemsParameters = [
                config : configuration,
                workItemIds : workItemIds,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, procedureName, deleteWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary == summary
            }

        }
        where:
        caseId     |  configuration  | workItemIds | resultFormat     | resultPropertySheet        | summary                | itemCount | outcome
        TC.C369830 |  ''             | '100'       | 'propertySheet'  | '/myJob/deletedWorkItems'  | summaries.emptyConfig  | 0         | 'error'
        TC.C369830 |  configName     | ''          | 'propertySheet'  | '/myJob/deletedWorkItems'  | summaries.emptyID      | 0         | 'error'
        TC.C369830 |  configName     | '100'       | ''               | '/myJob/deletedWorkItems'  | summaries.emptyFormat  | 0         | 'error'
        TC.C369831 |  'wrongConfig'  | '100'       | 'propertySheet'  | '/myJob/deletedWorkItems'  | summaries.wrongConfig  | 0         | 'error'
        TC.C369831 |  configName     | '999999999' | 'propertySheet'  | '/myJob/deletedWorkItems'  | summaries.wrongIDs     | 0         | 'warning'
        TC.C369831 |  configName     | '100'       | 'wrong'          | '/myJob/deletedWorkItems'  | summaries.wrongFormat  | 1         | 'error'
    }

}
