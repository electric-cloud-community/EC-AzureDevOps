package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import groovy.json.JsonSlurper
import spock.lang.*

import static org.junit.Assume.assumeFalse


@Stepwise
class GetWorkItemsTests extends PluginTestHelper {

    @Shared
    def procedureName = "GetWorkItems",
        projectName = "Spec $procedureName",
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
            'url',]

    @Shared
    TFSHelper tfsClient

    @Shared
    def TC = [
            C369645: [ ids: 'C369645', description: 'Get one workItem - only required fields'],
            C369646: [ ids: 'C369646', description: 'Get more than one workItems - only required fields'],
            C369647: [ ids: 'C369647', description: 'Get one workItem - specify fields'],
            C369648: [ ids: 'C369648', description: 'Get one workItem - specify some fields'],
            C369649: [ ids: 'C369649', description: 'Get more the one workItem - specify field'],
            C369650: [ ids: 'C369650', description: 'Get more the one workItems - specify some fields'],
            C369651: [ ids: 'C369651', description: 'expandRelations: links'],
            C369652: [ ids: 'C369652', description: 'expandRelations: fields'],
            C369653: [ ids: 'C369653', description: 'expandRelations: relations '],
            C369654: [ ids: 'C369654', description: 'expandRelations: all '],
            C369655: [ ids: 'C369655', description: 'Get one workItem - As of (date)'],
            C369656: [ ids: 'C369656', description: 'Get some workItems - As of (date)'],
            C369657: [ ids: 'C369657', description: 'Get one workItem - Result Property Sheet'],
            C369658: [ ids: 'C369658', description: 'Get one workItem - Result Format - json'],
            C369659: [ ids: 'C369659', description: 'Empty required fields'],
            C369660: [ ids: 'C369660', description: 'Wrong fields'],
    ]

    def doSetupSpec() {
        if (url == 'https://dev.azure.com'){
            userName = userEmail
        }
        tfsClient = getClient()
        createPluginConfigurationWithProxy(configName, configurationParams, credential, token_credential, proxy_credential)
        def configWrongUrl = configurationParams.clone()
        def configWrongToken = configurationParams.clone()
        def wrongCreds = token_credential.clone()
        configWrongUrl.endpoint = 'http://wrong:8080/tfs'
        configWrongToken.token_credential = 'wrongToken'
        wrongCreds.credentialName = 'wrongToken'
        wrongCreds.password = 'wrongToken123'
        createPluginConfigurationWithProxy(wrongConfigNames['url'], configWrongUrl, credential, token_credential, proxy_credential)
        createPluginConfigurationWithProxy(wrongConfigNames['token'], configWrongToken, credential, wrongCreds, proxy_credential)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "$procedureName", params: getWorkItemsParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "CreateWorkItems", params: createWorkItemsParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "UpdateWorkItems", params: updateWorkItemsParams]

    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['url'])
        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['token'])
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'CreateWorkItem Sanity1 #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['1', '2', '3', '4']
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

        getWorkItemsParams = [
                asOf: asOf,
                config: configuration,
                expandRelations: expandRelations,
                fields: fields,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
                workItemIds: workItemIds,
        ]

        when:
        def result = runProcedure(projectName, procedureName, getWorkItemsParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def specifiedFields =fields.split(',')
        def systemName = [
                'System.AreaPath': 'project',
                'System.Title': 'title',
                'System.Description': 'description',
                'System.WorkItemType': 'type'
        ]

        if (resultFormat == 'json') {
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
            }
        }

        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == summary
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                println(jobProperties[itemId])
                if (!fields) {
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
                    if (apiVersion == '5.0'){
                        jobProperties[itemId]['url'] =~ "$url/$collectionName/.*/_apis/wit/workItems/$itemId"
                    }
                    else {
                        jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/workItems/$itemId"
                    }                }
                else {
                    jobProperties[itemId].keySet().sort() == (['id', 'rev', 'url'] + Arrays.asList(specifiedFields)).sort()
                    for (field in specifiedFields){
                        jobProperties[itemId][field] == createWorkItemsParameters[i][systemName[field]]
                    }
                }
            }
        }
        cleanup:
        for (def i=0; i < itemCount; i++) {
            def itemId = jobProperties['workItemIds'].split(', ')[i]
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId     | configuration | fields                                                                 | asOf | expandRelations | resultPropertySheet    | resultFormat    | itemCount | summary
        TC.C369645 | configName    | ''                                                                     | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | "Work items are saved to a property sheet."
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'CreateWorkItem Sanity2 #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['1', '2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def addAllFields = [
                'System.Id',
                'System.AreaId',
                'System.NodeName',
                'System.AreaLevel1',
                'System.Rev',
                'System.AuthorizedDate',
                'System.RevisedDate',
                'System.IterationId',
                'System.IterationLevel1',
                'System.AuthorizedAs',
                'System.PersonId',
                'System.Watermark',

        ]
        if (expandRelations in ['fields', 'all']){
            defaultIssueFields += addAllFields
        }
        if (expandRelations in ['relations', 'all']){
            defaultIssueFields += 'relations'
        }
        def types = ['Issue' :defaultIssueFields]
        def additionalFieldsAddRelations = ''
        def workItemIds = ''
        for (def i=0; i < itemCount; i++){
            if (i != 0){
                additionalFieldsAddRelations = "[{ \"op\": \"add\", \"path\": \"/relations/-\", \"value\": { \"rel\": \"System.LinkTypes.Dependency-forward\",  \"url\": \"$url/$collectionName/_apis/wit/$workItemIds\", \"attributes\": { \"comment\": \"Making a new link for the dependency\" } }}]"
            }
            def param = [
                    config: configuration,
                    title: "TestItem ${caseId.ids} $i",
                    project: tfsProjectName,
                    type: types.keySet()[new Random().nextInt(types.keySet().size())],
                    priority: priorities[new Random().nextInt(priorities.size())],
                    assignTo: userName,
                    description: "description for ${caseId.ids} $i",
                    additionalFields: additionalFieldsAddRelations,
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

        getWorkItemsParams = [
                asOf: asOf,
                config: configuration,
                expandRelations: expandRelations,
                fields: fields,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
                workItemIds: workItemIds,
        ]

        when:
        def result = runProcedure(projectName, procedureName, getWorkItemsParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def specifiedFields =fields.split(',')
        def systemName = [
                'System.AreaPath': 'project',
                'System.Title': 'title',
                'System.Description': 'description',
                'System.WorkItemType': 'type'
        ]
        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == summary
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                println(jobProperties[itemId])
                if (!fields) {
                    if (url == 'https://dev.azure.com') {
                        jobProperties[itemId].keySet().containsAll(types[createWorkItemsParameters[i].type])
                    }
                    else{
                        jobProperties[itemId].keySet().sort() == types[createWorkItemsParameters[i].type].sort()
                    }
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'] == createWorkItemsParameters[i].priority
                    jobProperties[itemId]['System.AreaPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.AssignedTo'] =~ userName
                    jobProperties[itemId]['System.ChangedBy'] =~ userName
                    jobProperties[itemId]['System.CreatedBy'] =~ userName
                    jobProperties[itemId]['System.Description'] == createWorkItemsParameters[i].description
                    jobProperties[itemId]['System.IterationPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.Title'] == createWorkItemsParameters[i].title
                    jobProperties[itemId]['System.WorkItemType'] == createWorkItemsParameters[i].type
                    jobProperties[itemId]['id'] == itemId
                    jobProperties[itemId]['url'] =~ "$url/$collectionName/${apiVersion == '5.0' ? '.*/': ''}_apis/wit/workItems/$itemId"
                    if (expandRelations in ['relations', 'all']){
                        jobProperties[itemId]['relations']['1']['rel'] == (i == 0 ? 'System.LinkTypes.Dependency-Reverse' : 'System.LinkTypes.Dependency-Forward')
                        jobProperties[itemId]['relations']['1']['url'] =~ "$url/$collectionName/${apiVersion == '5.0' ? '.*/': ''}_apis/wit/workItems/${workItemIds.split(', ')[i == 0 ? 1 : 0]}"
                        jobProperties[itemId]['relations']['1']['attributes']['comment'] == 'Making a new link for the dependency'
                        jobProperties[itemId]['relations']['1']['attributes']['isLocked'] == 'false'
                    }
                }
                else {
                    jobProperties[itemId].keySet().sort() == (['id', 'rev', 'url'] + Arrays.asList(specifiedFields)).sort()
                    for (field in specifiedFields){
                        jobProperties[itemId][field] == createWorkItemsParameters[i][systemName[field]]
                    }
                }
            }
        }
        cleanup:
        for (def i=0; i < itemCount; i++) {
            def itemId = jobProperties['workItemIds'].split(', ')[i]
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId     | configuration | fields                                                                 | asOf | expandRelations | resultPropertySheet    | resultFormat    | itemCount | summary
        TC.C369654 | configName    | ''                                                                     | ''   | 'all'           | '/myJob/workItemsList' | 'propertySheet' | 2         | "Work items are saved to a property sheet."
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateWorkItem Positive #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['1', '2', '3', '4']
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

        getWorkItemsParams = [
                asOf: asOf,
                config: configuration,
                expandRelations: expandRelations,
                fields: fields,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
                workItemIds: workItemIds,
        ]

        when:
        def result = runProcedure(projectName, procedureName, getWorkItemsParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def specifiedFields =fields.split(',')
        def systemName = [
                'System.AreaPath': 'project',
                'System.Title': 'title',
                'System.Description': 'description',
                'System.WorkItemType': 'type'
        ]

        if (resultFormat == 'json') {
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
            }
        }

        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == summary
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                println(jobProperties[itemId])
                if (!fields) {
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
                    if (apiVersion == '5.0'){
                        jobProperties[itemId]['url'] =~ "$url/$collectionName/.*/_apis/wit/workItems/$itemId"
                    }
                    else {
                        jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/workItems/$itemId"
                    }
                }
                else {
                    jobProperties[itemId].keySet().sort() == (['id', 'rev', 'url'] + Arrays.asList(specifiedFields)).sort()
                    for (field in specifiedFields){
                        jobProperties[itemId][field] == createWorkItemsParameters[i][systemName[field]]
                    }
                }
            }
        }
        cleanup:
        for (def i=0; i < itemCount; i++) {
            def itemId = jobProperties['workItemIds'].split(', ')[i]
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId     | configuration | fields                                                                 | asOf | expandRelations | resultPropertySheet    | resultFormat    | itemCount | summary
        TC.C369645 | configName    | ''                                                                     | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | "Work items are saved to a property sheet."
        TC.C369646 | configName    | ''                                                                     | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 4         | "Work items are saved to a property sheet."
        TC.C369647 | configName    | 'System.AreaPath'                                                      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | "Work items are saved to a property sheet."
        TC.C369648 | configName    | 'System.AreaPath,System.Title,System.Description,System.WorkItemType'  | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | "Work items are saved to a property sheet."
        TC.C369649 | configName    | 'System.AreaPath'                                                      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 4         | "Work items are saved to a property sheet."
        TC.C369650 | configName    | 'System.AreaPath,System.Title,System.Description,System.WorkItemType'  | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 4         | "Work items are saved to a property sheet."
        TC.C369657 | configName    | ''                                                                     | ''   | 'none'          | '/myJob/QAtest'        | 'propertySheet' | 1         | "Work items are saved to a property sheet."
        TC.C369658 | configName    | ''                                                                     | ''   | 'none'          | '/myJob/workItemsList' | 'json'          | 1         | "Work items are saved to a property sheet."
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateWorkItem Positive As of (date) #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['1', '2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue' :defaultIssueFields, 'Epic' : defaultFields,
                     'Feature':defaultFields, 'User Story': defaultFields]

        def workItemIds = ''
        def createdDates = []
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
            def tmpResult = getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]
            if (i == 0) {
                workItemIds += tmpResult['workItemIds']
            }
            else {
                workItemIds += ', ' + tmpResult['workItemIds']
            }
            def createdDate = tmpResult[tmpResult['workItemIds']]['System.CreatedDate']
            createdDates.add(createdDate)
        }

        sleep(10000)
        for (def i=0; i < itemCount; i++){
            def updateWorkItemsParameters = [
                    additionalFields : "",
                    assignTo : "",
                    commentBody : "",
                    config : configuration,
                    description : createWorkItemsParameters[i].description + ' updated',
                    priority : "",
                    resultFormat : resultFormat,
                    resultPropertySheet : resultPropertySheet,
                    title : createWorkItemsParameters[i].title + ' updated',
                    workItemIds : workItemIds.split(', ')[i],
            ]
            runProcedure(projectName, "UpdateWorkItems", updateWorkItemsParameters)
        }

        getWorkItemsParams = [
                asOf: createdDates[-1],
                config: configuration,
                expandRelations: expandRelations,
                fields: fields,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
                workItemIds: workItemIds,
        ]
        when:
        def result = runProcedure(projectName, procedureName, getWorkItemsParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def specifiedFields =fields.split(',')
        def systemName = [
                'System.AreaPath': 'project',
                'System.Title': 'title',
                'System.Description': 'description',
                'System.WorkItemType': 'type'
        ]
        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == summary
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                println(jobProperties[itemId])
                if (!fields) {
                    if (url == 'https://dev.azure.com') {
                        jobProperties[itemId].keySet().containsAll(types[createWorkItemsParameters[i].type])
                    }
                    else{
                        jobProperties[itemId].keySet().sort() == types[createWorkItemsParameters[i].type].sort()
                    }
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'] == createWorkItemsParameters[i].priority
                    jobProperties[itemId]['System.AreaPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.AssignedTo'] =~ userName
                    jobProperties[itemId]['System.ChangedBy'] =~ userName
                    jobProperties[itemId]['System.CreatedBy'] =~ userName
                    jobProperties[itemId]['System.Description'] == createWorkItemsParameters[i].description
                    jobProperties[itemId]['System.IterationPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.Title'] == createWorkItemsParameters[i].title
                    jobProperties[itemId]['System.WorkItemType'] == createWorkItemsParameters[i].type
                    jobProperties[itemId]['id'] == itemId
                    if (apiVersion == '5.0'){
                        jobProperties[itemId]['url'] =~ "$url/$collectionName/.*/_apis/wit/workItems/$itemId/revisions/1"
                    }
                    else {
                        jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/workItems/$itemId/revisions/1"
                    }
                }
                else {
                    jobProperties[itemId].keySet().sort() == (['id', 'rev', 'url'] + Arrays.asList(specifiedFields)).sort()
                    for (field in specifiedFields){
                        jobProperties[itemId][field] == createWorkItemsParameters[i][systemName[field]]
                    }
                }
            }
        }
        cleanup:
        for (def i=0; i < itemCount; i++) {
            def itemId = jobProperties['workItemIds'].split(', ')[i]
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId     | configuration | fields                                                                 |  expandRelations | resultPropertySheet    | resultFormat    | itemCount | summary
        TC.C369655 | configName    | ''                                                                     |  'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | "Work items are saved to a property sheet."
        TC.C369656 | configName    | ''                                                                     |  'none'          | '/myJob/workItemsList' | 'propertySheet' | 4         | "Work items are saved to a property sheet."
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateWorkItem Positive expandRelations #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['1', '2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def addAllFields = [
                'System.Id',
                'System.AreaId',
                'System.NodeName',
                'System.AreaLevel1',
                'System.Rev',
                'System.AuthorizedDate',
                'System.RevisedDate',
                'System.IterationId',
                'System.IterationLevel1',
                'System.AuthorizedAs',
                'System.PersonId',
                'System.Watermark',

        ]
        if (expandRelations in ['fields', 'all']){
            defaultIssueFields += addAllFields
        }
        if (expandRelations in ['relations', 'all']){
            defaultIssueFields += 'relations'
        }
        def types = ['Issue' :defaultIssueFields]
        def additionalFieldsAddRelations = ''
        def workItemIds = ''
        for (def i=0; i < itemCount; i++){
            if (i != 0){
                additionalFieldsAddRelations = "[{ \"op\": \"add\", \"path\": \"/relations/-\", \"value\": { \"rel\": \"System.LinkTypes.Dependency-forward\",  \"url\": \"$url/$collectionName/_apis/wit/$workItemIds\", \"attributes\": { \"comment\": \"Making a new link for the dependency\" } }}]"
            }
            def param = [
                    config: configuration,
                    title: "TestItem ${caseId.ids} $i",
                    project: tfsProjectName,
                    type: types.keySet()[new Random().nextInt(types.keySet().size())],
                    priority: priorities[new Random().nextInt(priorities.size())],
                    assignTo: userName,
                    description: "description for ${caseId.ids} $i",
                    additionalFields: additionalFieldsAddRelations,
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

        getWorkItemsParams = [
                asOf: asOf,
                config: configuration,
                expandRelations: expandRelations,
                fields: fields,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
                workItemIds: workItemIds,
        ]

        when:
        def result = runProcedure(projectName, procedureName, getWorkItemsParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def specifiedFields =fields.split(',')
        def systemName = [
                'System.AreaPath': 'project',
                'System.Title': 'title',
                'System.Description': 'description',
                'System.WorkItemType': 'type'
        ]
        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == summary
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                println(jobProperties[itemId])
                if (!fields) {
                    if (url == 'https://dev.azure.com') {
                        jobProperties[itemId].keySet().containsAll(types[createWorkItemsParameters[i].type])
                    }
                    else{
                        jobProperties[itemId].keySet().sort() == types[createWorkItemsParameters[i].type].sort()
                    }
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'] == createWorkItemsParameters[i].priority
                    jobProperties[itemId]['System.AreaPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.AssignedTo'] =~ userName
                    jobProperties[itemId]['System.ChangedBy'] =~ userName
                    jobProperties[itemId]['System.CreatedBy'] =~ userName
                    jobProperties[itemId]['System.Description'] == createWorkItemsParameters[i].description
                    jobProperties[itemId]['System.IterationPath'] == createWorkItemsParameters[i].project
                    jobProperties[itemId]['System.Title'] == createWorkItemsParameters[i].title
                    jobProperties[itemId]['System.WorkItemType'] == createWorkItemsParameters[i].type
                    jobProperties[itemId]['id'] == itemId
                    jobProperties[itemId]['url'] =~ "$url/$collectionName/${apiVersion == '5.0' ? '.*/': ''}_apis/wit/workItems/$itemId"
                    if (expandRelations in ['relations', 'all']){
                        jobProperties[itemId]['relations']['1']['rel'] == (i == 0 ? 'System.LinkTypes.Dependency-Reverse' : 'System.LinkTypes.Dependency-Forward')
                        jobProperties[itemId]['relations']['1']['url'] =~ "$url/$collectionName/${apiVersion == '5.0' ? '.*/': ''}_apis/wit/workItems/${workItemIds.split(', ')[i == 0 ? 1 : 0]}"
                        jobProperties[itemId]['relations']['1']['attributes']['comment'] == 'Making a new link for the dependency'
                        jobProperties[itemId]['relations']['1']['attributes']['isLocked'] == 'false'
                    }
                }
                else {
                    jobProperties[itemId].keySet().sort() == (['id', 'rev', 'url'] + Arrays.asList(specifiedFields)).sort()
                    for (field in specifiedFields){
                        jobProperties[itemId][field] == createWorkItemsParameters[i][systemName[field]]
                    }
                }
            }
        }
        cleanup:
        for (def i=0; i < itemCount; i++) {
            def itemId = jobProperties['workItemIds'].split(', ')[i]
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId     | configuration | fields                                                                 | asOf | expandRelations | resultPropertySheet    | resultFormat    | itemCount | summary
//        TC.C369651 | configName    | ''                                                                     | ''   | 'links'         | '/myJob/workItemsList' | 'propertySheet' | 1         | "Work items are saved to a property sheet."
        TC.C369652 | configName    | ''                                                                     | ''   | 'fields'        | '/myJob/workItemsList' | 'propertySheet' | 1         | "Work items are saved to a property sheet."
        TC.C369653 | configName    | ''                                                                     | ''   | 'relations'     | '/myJob/workItemsList' | 'propertySheet' | 2         | "Work items are saved to a property sheet."
        TC.C369654 | configName    | ''                                                                     | ''   | 'all'           | '/myJob/workItemsList' | 'propertySheet' | 2         | "Work items are saved to a property sheet."
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateWorkItem Negative #caseId.ids #caseId.description'() {
        assumeFalse(configuration == wrongConfigNames['token'] && authType != 'pat')
        given:
        def createWorkItemsParameters = []
        def priorities = ['1', '2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue' :defaultIssueFields, 'Epic' : defaultFields,
                     'Feature':defaultFields, 'User Story': defaultFields]

        getWorkItemsParams = [
                asOf: asOf,
                config: configuration,
                expandRelations: expandRelations,
                fields: fields,
                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
                workItemIds: workItemIds,
        ]

        when:
        def result = runProcedure(projectName, procedureName, getWorkItemsParams)

        then:
        verifyAll {
            result.outcome == outcome
        }
        where:
        caseId     | configuration                | workItemIds | fields  | asOf | expandRelations | resultPropertySheet    | resultFormat    | itemCount | outcome
        TC.C369659 | ''                           | '100'       | ''      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
        TC.C369659 | configName                   | ''          | ''      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
        TC.C369659 | configName                   | '100'       | ''      | ''   | 'none'          | ''                     | 'propertySheet' | 1         | 'error'
        TC.C369659 | configName                   | '100'       | ''      | ''   | 'none'          | '/myJob/workItemsList' | ''              | 1         | 'error'
        TC.C369660 | 'wrongName'                  | ''          | ''      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
        TC.C369660 | wrongConfigNames['url']      | '1'         | ''      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
        TC.C369660 | wrongConfigNames['token']    | '2'         | ''      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
        TC.C369660 | configName                   | 'wrong'     | ''      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
        TC.C369660 | configName                   | '999999999' | ''      | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'warning'
        TC.C369660 | configName                   | '1'         | 'wrong' | ''   | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
        TC.C369660 | configName                   | '2'         | ''      | '1'  | 'none'          | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
        TC.C369660 | configName                   | '1'         | ''      | ''   | 'wrong'         | '/myJob/workItemsList' | 'propertySheet' | 1         | 'error'
    }

}