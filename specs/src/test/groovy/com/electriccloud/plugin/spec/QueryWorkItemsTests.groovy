package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import groovy.json.JsonSlurper
import spock.lang.*


class QueryWorkItemsTests extends PluginTestHelper {

    @Shared
    def procedureName = "QueryWorkItems",
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
                    'url',]

    @Shared
    def TC = [
            C369849: [ ids: 'C369849', description: 'Simple query: Query Text - Get One Item'],
            C369850: [ ids: 'C369850', description: 'Simple query: Query Text - Get One Item (System.State)'],
            C369851: [ ids: 'C369851', description: 'Simple query: Query Text - Get Some Items (System.ID, System.Title) '],
            C369852: [ ids: 'C369852', description: 'Simple query: Query Text - Get Some Items (System.WorkItemType)'],
            C369853: [ ids: 'C369853', description: 'Simple query: Query Text - Get all (System.ID, System.Title) '],
            C369854: [ ids: 'C369854', description: 'Simple query: Query Text - Get One Item (System.ID, System.Title)'],
            C369862: [ ids: 'C369862', description: 'Simple query: Query Text - timePrecision - true '],
            C369863: [ ids: 'C369863', description: 'use Query ID'],
            C369864: [ ids: 'C369864', description: 'Simple query: Query Text - json format'],
            C369865: [ ids: 'C369865', description: 'Custom resultPropertySheet'],
            C369866: [ ids: 'C369866', description: 'use Query ID and timePrecision '],
            C369867: [ ids: 'C369867', description: 'use query with @project '],
            C369868: [ ids: 'C369868', description: 'use query without @project'],
            C369869: [ ids: 'C369869', description: 'use query with macros @Me '],
            C369870: [ ids: 'C369870', description: 'use query with macros @today '],
            C369871: [ ids: 'C369871', description: 'use query with date in format MM/dd/yyyy'],
            C369872: [ ids: 'C369872', description: 'special symbols /"&^@#?;.'],
            C369874: [ ids: 'C369874', description: 'empty required fields'],
            C369876: [ ids: 'C369876', description: 'empty queryId and queryText '],
            C369877: [ ids: 'C369877', description: 'empty project : use macros with @project '],
            C369878: [ ids: 'C369878', description: 'empty project : use @project as part of query'],
            C369879: [ ids: 'C369879', description: 'wrong values'],
            C369880: [ ids: 'C369880', description: 'timePrecision -false, use date format 2019-02-19T10:33:53.303Z'],
            C369881: [ ids: 'C369881', description: 'empty result'],
    ]

    @Shared
    def summaries = [
            default: 'Got work items: COUNT, titles: TITLES',
            withoutTitle: 'Got work items: COUNT',
            emptyConfig: 'Parameter "Configuration name" is mandatory\n\n',
            emptyFormat: 'Parameter "Result Format" is mandatory\n\n',
            emptySheet: 'Parameter "Result Property Sheet" is mandatory\n\n',
            emptyQueryIdAndText: "Either 'Query ID' or 'Query Text' should be present\n\n",
            emptyProject: "Your query contains reference to a project, but parameter 'Project' is not specified.\n\n",

            noWorkItems: "No work items was found for the query.",

            wrongConfig: 'Configuration "wrong" does not exist\n\n',
            wrongFormat: 'Cannot process format wrong: not implemented\n\n'
    ]

    @Shared
    queryTexts = [
            simpleOneItem: "SELECT System.ID, System.Title from workitems where System.ID=IDS",
            macrosProject: "SELECT [System.ID], [System.Title] from workitems where [System.ID]=IDS and [System.TeamProject] = @project",
            macrosMe: "SELECT [System.ID], [System.Title] from workitems where [System.ID]=IDS and [System.AssignedTo] = @Me",
            macrosToday: "SELECT [System.ID], [System.Title] from workitems where [System.ID]=IDS and [System.CreatedDate] = @today",
            simpleOneItemType: "SELECT System.WorkItemType from workitems where System.ID=IDS",
            simpleOneItemWithDate: "SELECT [System.ID], [System.Title] from workitems where [System.ID]=IDS and [System.CreatedDate] = \'${new Date().format('MM/dd/yyyy')}\'",
            simpleSomeItems: "SELECT System.ID, System.Title from workitems where System.ID in (IDS)",
            simpleSomeItemsType: "SELECT System.WorkItemType from workitems where System.ID in (IDS)",
            allFieldsOneItem: "SELECT * from workitems where System.ID=IDS",
            allFieldsSomeItems: "SELECT * from workitems where System.ID in (IDS)",
            manyItems: "SELECT * from workitems where System.ID=IDS",
            timePrecision: "SELECT System.ID, System.Title from workitems where System.CreatedDate = \'TIME\'",
            timePrecision2: "SELECT System.ID, System.Title from workitems where System.CreatedDate = 'TIME'",
            specialSymbols: "SELECT System.ID, System.Title from workitems where System.Title = 'SPECIAL_TITLE'",
            macrosAsText: " SELECT System.ID, System.Title from workitems where System.Title = 'SPECIAL_TITLE'",
            emptyResult: "SELECT System.ID, System.Title from workitems where System.ID=999999",

    ]

    @Shared
    TFSHelper tfsClient

    def doSetupSpec() {
        if (url == 'https://dev.azure.com'){
            userName = userEmail
        }
        tfsClient = getClient()
        createPluginConfigurationWithProxy(configName, configurationParams, credential, token_credential, proxy_credential)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: procedureName, params: queryWorkItemsParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "CreateWorkItems", params: createWorkItemsParams]
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'QueryWorkItemsTests Sanity #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue' :defaultIssueFields, 'Epic' : defaultFields,
                     'Feature':defaultFields, 'User Story': defaultFields]

        def createdDate = ''
        def workItemIds = ''
        def titles = ''
        def createItemsProperties
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
                    resultFormat: 'propertySheet',
                    resultPropertySheet: resultPropertySheet,]
            if (caseId == TC.C369872){
                param.title += randomize('/"&^@#?;.')
            }
            if (caseId == TC.C369878){
                param.title += randomize(' @project ')
            }
            createWorkItemsParameters.add(param)
            if (i == 0) {
                createItemsProperties = getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]
                workItemIds += createItemsProperties['workItemIds']
                createdDate = createItemsProperties[workItemIds]['System.CreatedDate']
                titles += param.title
            }
            else {
                workItemIds += ', ' + getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
                titles += ', ' +  param.title
            }
        }

        if (caseId == TC.C369863) {
            def queryJSON = tfsClient.createWorkItemQuery(randomize("Query ${caseId.ids}"), queryTexts.simpleOneItem.replace('IDS', workItemIds),  [queryType: "flat"])
            queryId = queryJSON.id
        }
        if (caseId == TC.C369866) {
            def queryJSON = tfsClient.createWorkItemQuery(randomize("Query ${caseId.ids}"), queryTexts.timePrecision2.replace('IDS', workItemIds).replace('TIME', createdDate),  [queryType: "flat"])
            queryId = queryJSON.id
        }

        def queryWorkItemsTestsParameters = [
                "config" : configuration,
                "project" : project,
                "queryId" : queryId,
                "queryText" : queryText.replace('IDS', workItemIds).replace('TIME', createdDate).replace('SPECIAL_TITLE', titles),
                "resultFormat" : resultFormat,
                "resultPropertySheet" : resultPropertySheet,
                "timePrecision" : timePrecision,
        ]
        when:
        def result = runProcedure(projectName, procedureName, queryWorkItemsTestsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]

        if (resultFormat == 'json') {
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
            }
        }

        summary = summary.replace('TITLES', titles).replace('COUNT', itemCount.toString())
        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary == summary
            }
            jobProperties['workItemIds'] == workItemIds
            for (def i = 0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                jobProperties[itemId]['id'].toString() == itemId
                jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/workItems/$itemId"
                switch (queryText){
                    case queryTexts.simpleOneItem || queryTexts.simpleSomeItems || timePrecision:
                        jobProperties[itemId]['fields']['System.Title'] == createWorkItemsParameters[i].title
                        jobProperties[itemId]['fields']['System.Id'] == itemId
                        break
                    case queryTexts.simpleOneItemType || queryTexts.simpleSomeItemsType:
                        jobProperties[itemId]['fields']['System.WorkItemType'] == createWorkItemsParameters[i].type
                        break
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
        caseId     |  configuration  | project          | queryId | queryText                       | timePrecision | resultFormat     | resultPropertySheet        | itemCount  | summary             | outcome
        TC.C369849 |  configName     | tfsProjectName   | ''      | queryTexts.simpleOneItem        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369863 |  configName     | tfsProjectName   | ''      | ''                              | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'QueryWorkItemsTests Positive #caseId.ids #caseId.description'() {
        given:
        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue' :defaultIssueFields, 'Epic' : defaultFields,
                     'Feature':defaultFields, 'User Story': defaultFields]

        def createdDate = ''
        def workItemIds = ''
        def titles = ''
        def createItemsProperties
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
                    resultFormat: 'propertySheet',
                    resultPropertySheet: resultPropertySheet,]
            if (caseId == TC.C369872){
                param.title += randomize('/"&^@#?;.')
            }
            if (caseId == TC.C369878){
                param.title += randomize(' @project ')
            }
            createWorkItemsParameters.add(param)
            if (i == 0) {
                createItemsProperties = getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]
                workItemIds += createItemsProperties['workItemIds']
                createdDate = createItemsProperties[workItemIds]['System.CreatedDate']
                titles += param.title
            }
            else {
                workItemIds += ', ' + getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
                titles += ', ' +  param.title
            }
        }

        if (caseId == TC.C369863) {
            def queryJSON = tfsClient.createWorkItemQuery(randomize("Query ${caseId.ids}"), queryTexts.simpleOneItem.replace('IDS', workItemIds),  [queryType: "flat"])
            queryId = queryJSON.id
        }
        if (caseId == TC.C369866) {
            def queryJSON = tfsClient.createWorkItemQuery(randomize("Query ${caseId.ids}"), queryTexts.timePrecision2.replace('IDS', workItemIds).replace('TIME', createdDate),  [queryType: "flat"])
            queryId = queryJSON.id
        }

        def queryWorkItemsTestsParameters = [
                "config" : configuration,
                "project" : project,
                "queryId" : queryId,
                "queryText" : queryText.replace('IDS', workItemIds).replace('TIME', createdDate).replace('SPECIAL_TITLE', titles),
                "resultFormat" : resultFormat,
                "resultPropertySheet" : resultPropertySheet,
                "timePrecision" : timePrecision,
        ]
        when:
        def result = runProcedure(projectName, procedureName, queryWorkItemsTestsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]

        if (resultFormat == 'json') {
            for (def i=0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
            }
        }

        summary = summary.replace('TITLES', titles).replace('COUNT', itemCount.toString())
        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary == summary
            }
            jobProperties['workItemIds'] == workItemIds
            for (def i = 0; i < itemCount; i++) {
                def itemId = jobProperties['workItemIds'].split(', ')[i]
                jobProperties[itemId]['id'].toString() == itemId
                jobProperties[itemId]['url'] == "$url/$collectionName/_apis/wit/workItems/$itemId"
                switch (queryText){
                    case queryTexts.simpleOneItem || queryTexts.simpleSomeItems || timePrecision:
                        jobProperties[itemId]['fields']['System.Title'] == createWorkItemsParameters[i].title
                        jobProperties[itemId]['fields']['System.Id'] == itemId
                        break
                    case queryTexts.simpleOneItemType || queryTexts.simpleSomeItemsType:
                        jobProperties[itemId]['fields']['System.WorkItemType'] == createWorkItemsParameters[i].type
                        break
                    case queryTexts.manyItems:
                        jobProperties[itemId]['fields']['System.Title'] == createWorkItemsParameters[i].title
                        jobProperties[itemId]['fields']['System.Id'] == itemId
                        jobProperties[itemId]['fields']['System.WorkItemType'] == createWorkItemsParameters[i].type
                        jobProperties[itemId]['fields']['System.ChangedBy']
                        jobProperties[itemId]['fields']['System.CreatedBy']
                        jobProperties[itemId]['fields']['System.Description']
                        jobProperties[itemId]['fields']['System.State']
                        break
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
        caseId     |  configuration  | project          | queryId | queryText                       | timePrecision | resultFormat     | resultPropertySheet        | itemCount  | summary             | outcome
        TC.C369849 |  configName     | tfsProjectName   | ''      | queryTexts.simpleOneItem        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
// http://jira.electric-cloud.com/browse/ECTFS-135
        TC.C369850 |  configName     | tfsProjectName   | ''      | queryTexts.simpleOneItemType    | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.withoutTitle   | 'success'
        TC.C369851 |  configName     | tfsProjectName   | ''      | queryTexts.simpleSomeItems      | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 2          | summaries.default   | 'success'
// http://jira.electric-cloud.com/browse/ECTFS-135
        TC.C369852 |  configName     | tfsProjectName   | ''      | queryTexts.simpleSomeItemsType  | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 2          | summaries.withoutTitle   | 'success'
// http://jira.electric-cloud.com/browse/ECTFS-134 closed
        TC.C369853 |  configName     | tfsProjectName   | ''      | queryTexts.manyItems            | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
// http://jira.electric-cloud.com/browse/ECTFS-136
        TC.C369862 |  configName     | tfsProjectName   | ''      | queryTexts.timePrecision        | '1'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369863 |  configName     | tfsProjectName   | ''      | ''                              | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
// doesn't work        TC.C369866 |  configName     | tfsProjectName   | ''      | ''                              | '1'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369864 |  configName     | tfsProjectName   | ''      | queryTexts.simpleOneItem        | '0'           | 'json'           | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369864 |  configName     | tfsProjectName   | ''      | queryTexts.simpleSomeItems      | '0'           | 'json'           | '/myJob/queryWorkItems'    | 2          | summaries.default   | 'success'
        TC.C369865 |  configName     | tfsProjectName   | ''      | queryTexts.simpleOneItem        | '0'           | 'propertySheet'  | '/myJob/QA'                | 1          | summaries.default   | 'success'
// http://jira.electric-cloud.com/browse/ECTFS-137
        TC.C369867 |  configName     | tfsProjectName   | ''      | queryTexts.macrosProject        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369868 |  configName     | ''               | ''      | queryTexts.simpleOneItem        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369869 |  configName     | tfsProjectName   | ''      | queryTexts.macrosMe             | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369870 |  configName     | tfsProjectName   | ''      | queryTexts.macrosToday          | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369871 |  configName     | tfsProjectName   | ''      | queryTexts.simpleOneItemWithDate| '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369872 |  configName     | tfsProjectName   | ''      | queryTexts.specialSymbols       | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'
        TC.C369878 |  configName     | ''               | ''      | queryTexts.specialSymbols       | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.default   | 'success'

    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'QueryWorkItemsTests Negative #caseId.ids #caseId.description'() {
        given:
        def workItemIds = '100'
        def queryWorkItemsTestsParameters = [
                "config" : configuration,
                "project" : project,
                "queryId" : queryId,
                "queryText" : queryText.replace('IDS', workItemIds).replace('TIME', '2019-02-19T10:33:53.303Z'),
                "resultFormat" : resultFormat,
                "resultPropertySheet" : resultPropertySheet,
                "timePrecision" : timePrecision,
        ]
        when:
        def result = runProcedure(projectName, procedureName, queryWorkItemsTestsParameters)
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
        caseId     |  configuration  | project          | queryId | queryText                       | timePrecision | resultFormat     | resultPropertySheet        | itemCount  | summary                         | outcome
        TC.C369874 |  ''             | ''               | ''      | queryTexts.simpleOneItem        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.emptyConfig           | 'error'
        TC.C369874 |  configName     | ''               | ''      | queryTexts.simpleOneItem        | '0'           | ''               | '/myJob/queryWorkItems'    | 1          | summaries.emptyFormat           | 'error'
        TC.C369874 |  configName     | ''               | ''      | queryTexts.simpleOneItem        | '0'           | 'propertySheet'  | ''                         | 1          | summaries.emptySheet            | 'error'
        TC.C369876 |  configName     | ''               | ''      | ''                              | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.emptyQueryIdAndText   | 'error'
        TC.C369877 |  configName     | ''               | ''      | queryTexts.macrosProject        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.emptyProject          | 'error'
        TC.C369879 |  'wrong'        | ''               | ''      | queryTexts.simpleOneItem        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.wrongConfig           | 'error'
        TC.C369879 |  configName     | 'wrongProject'   | ''      | queryTexts.macrosProject        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | ''                              | 'error'
        TC.C369879 |  configName     | ''               | '999999'| ''                              | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | ''                              | 'error'
        TC.C369879 |  configName     | tfsProjectName   | ''      | 'wrong'                         | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | ''                              | 'error'
        TC.C369880 |  configName     | tfsProjectName   | ''      | queryTexts.timePrecision        | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | ''                              | 'error'
        TC.C369881 |  configName     | tfsProjectName   | ''      | queryTexts.emptyResult          | '0'           | 'propertySheet'  | '/myJob/queryWorkItems'    | 1          | summaries.noWorkItems           | 'warning'
    }


}