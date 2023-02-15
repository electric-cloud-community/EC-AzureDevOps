package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import groovy.json.JsonSlurper
import spock.lang.*

import static org.junit.Assume.assumeFalse


class CreateWorkItemsTests extends PluginTestHelper {

    @Shared
    def procedureName = "CreateWorkItems",
        projectName = "Spec Tests $procedureName",
        descriptions = [
                oneLine: "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                multiLines: "Lorem ipsum dolor sit amet, consectetur adipiscing elit,\n" +
                        "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n" +
                        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi\n" +
                        "ut aliquip ex ea commodo consequat. Duis aute irure dolor in\n" +
                        " reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
                htmlTags: "<span style=\"font-weight:bold;\">Lorem ipsum dolor sit amet</span>,<div><ol><li>" +
                        "consectetur adipiscing elit, sed do eiusmod tempor incididunt</li><li>ut labore et dolore magna aliqua. " +
                        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip&nbsp;</li><li>ex ea commodo consequat." +
                        " Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.</li></ol></div>"
        ],

        addValue = [
                    SystemInfo: ["Microsoft.VSTS.TCM.SystemInfo", "QA: Windows 2016"],
                    ReproSteps: ["Microsoft.VSTS.TCM.ReproSteps", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."]
        ],

        addFields = [
                SystemInfoReproSteps: '[' +
                        '{' +
                        "\"path\":\"/fields/${addValue.SystemInfo[0]}\"," +
                        "\"value\": \"${addValue.SystemInfo[1]}\"" +
                        '},' +
                        '{' +
                        "\"path\":\"/fields/${addValue.ReproSteps[0]}\"," +
                        "\"value\": \"${addValue.ReproSteps[1]}\"" +
                        '}' +
                        ']',
                wrongParameters: '[ { "path":"/wrong", "value": "value" }]',
                wrongFormat: '[ { "path":"/fields/Microsoft.VSTS.TCM.SystemInfo", "value: QA: Windows 2016" } ]'
        ],
        workItemJson = '[{"Title": "First", "Type": "Bug"}, {"Priority": "1", "Assign To": "USER_TO_CHANGE"}, {"Description": "third description", "Additional Fields": [{"path": "/fields/System.State", "value": "Active"}]}]'.replace("USER_TO_CHANGE", (url == 'https://dev.azure.com') ? userEmail : userName),
        wrongItemJson = [
                value: '[{"wrong": "value}]',
                format: '[{"Title": "First", "Type": "Bug"}'
        ]
//        errorLogs = [
//                'wrongConfig': ['Configuration "wrongConfig" does not exist'],
//                'wrongConfigUrl': ['Content: 500 Can\'t connect to wrong:8080'],
//                'wrongConfigToken': ['Unauthorized. Check your credentials'],
//        ]

    @Shared
    summaries = [
            wrongConfig : 'Configuration "wrongConfig" does not exist',
            wrongHostmame : System.getenv('EF_PROXY_URL') ? '503 Service Unavailable' : "500 Can't connect to wrong:8080 (Bad hostname 'wrong')",
            unauthorized : '401 Unauthorized',
            wrongProject : 'The following project does not exist: wrongProject',
            wrongType : 'Work item type wrongType does not exist in project',
            wrongPriority : "The field 'Priority' contains the value '55' ",
            wrongUser : "The identity value 'wrongUser' for field 'Assigned To' is an unknown identity",
            wrongpath : 'Unable to evaluate path /wrong.',
            wrongAddFiels: "Parameter 'Additional Fields' check failed",
            wrongJson1: "Parameter 'Work Items JSON' check failed",
            wrongJson2: "Parameter 'Work Items JSON' check failed",
    ]

    @Shared
    TFSHelper tfsClient

    @Shared
    def TC = [
            C369581: [ ids: 'C369581', description: 'Create Simple work item - only required field'],
            C369592: [ ids: 'C369592', description: 'Create all types of workItems'],
            C369593: [ ids: 'C369593', description: 'Create Simple work item - add priority'],
            C369594: [ ids: 'C369594', description: 'Create Simple work item (Bug) - Assign to'],
            C369595: [ ids: 'C369595', description: 'Create Simple work item (Issue) - Assign to'],
            C369596: [ ids: 'C369596', description: 'Create Simple work item (Bug) - Assign to, non default user'],
            C369597: [ ids: 'C369597', description: 'Create Simple work item - one line description'],
            C369598: [ ids: 'C369598', description: 'Create Simple work item - multiline description'],
            C369601: [ ids: 'C369601', description: 'Create Simple work item - use html tags'],
            C369609: [ ids: 'C369609', description: 'not default type (Task) of work item'],
            C369610: [ ids: 'C369610', description: 'additional fields'],
            C369611: [ ids: 'C369611', description: 'Create some Works equal Items - Issue with all fields'],
            C369612: [ ids: 'C369612', description: 'Create some Works Items - 3 different Items '],
            C369613: [ ids: 'C369613', description: 'property sheet - json'],
            C369614: [ ids: 'C369614', description: 'Create some Works equal Items - Issues with all fields - property sheet - json'],
            C369615: [ ids: 'C369615', description: 'custom resultPropertySheet '],
            C369616: [ ids: 'C369616', description: 'resultFormat - None'],
            C369623: [ ids: 'C369623', description: 'Use wrong config'],
            C369624: [ ids: 'C369624', description: 'Use config with wrong url'],
            C369625: [ ids: 'C369625', description: 'Use config with wrong token'],
            C369633: [ ids: 'C369633', description: 'non existing project name'],
            C369634: [ ids: 'C369634', description: 'wrong type of issue'],
            C369635: [ ids: 'C369635', description: 'wrong priority'],
            C369636: [ ids: 'C369636', description: 'wrong user for "Assign to" '],
            C369637: [ ids: 'C369637', description: 'wrong Additional Fields'],
            C369638: [ ids: 'C369638', description: 'wrong format of Additional Fields'],
            C369639: [ ids: 'C369639', description: 'wrong field in "Work Items JSON:"'],
            C369640: [ ids: 'C369640', description: 'wrong format of "Work Items JSON:"'],
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
        configWrongToken.token_credential = 'wrongToken'
        wrongCreds.credentialName = 'wrongToken'
        wrongCreds.password = 'wrongToken123'
        createPluginConfigurationWithProxy(wrongConfigNames['url'], configWrongUrl, credential, token_credential, proxy_credential)
        createPluginConfigurationWithProxy(wrongConfigNames['token'], configWrongToken, credential, wrongCreds, proxy_credential)

        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "CreateWorkItems", params: createWorkItemsParams]
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['url'])
        deleteConfiguration('EC-AzureDevOps', wrongConfigNames['token'])
    }


    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'CreateWorkItem Sanity 1 #caseId.ids #caseId.description'() {
        given:

        def createWorkItemsParameters = [
                config: configuration,
                title: title,
                project: project,
                type: type,
                priority: priority,
                assignTo: assignTo,
                description: description,
                additionalFields: additionalFields,
                workItemsJSON: workItemsJSON,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def itemId = jobProperties['workItemIds']
        // if priority is empty , default value is 2
        priority = priority ?: '2'
        // if assignTo is empty it will use default user
        if (!assignTo && type=="Issue"){
            assignTo = userName
        }
        if (resultFormat == 'json') {
            jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
        }
        then:
        verifyAll {
            if (resultFormat == 'none'){
                !jobProperties[itemId]
            }
            else {
                result.outcome == 'success'
                jobSummary == 'Successfully created 1 work item.'
                jobProperties[itemId]['id']
                jobProperties[itemId]['System.TeamProject'] == project
                jobProperties[itemId]['System.Title'] == title
                jobProperties[itemId]['System.WorkItemType'] == type
                jobProperties[itemId]['Microsoft.VSTS.Common.Priority'].toString() == priority
                if (assignTo) {
                    jobProperties[itemId]['System.AssignedTo'] =~ assignTo
                }
                if (description) {
                    jobProperties[itemId]['System.Description'] == description
                }
                if (additionalFields) {
                    for (value in addValue) {
                        jobProperties[itemId][value.value[0]] == value.value[1]
                    }
                }
            }
        }
        cleanup:
        tfsClient.deleteWorkItem(itemId)

        where:
        caseId     | configuration | title  | project        | type         | priority | assignTo         | description              | additionalFields                | workItemsJSON | resultFormat     | resultPropertySheet
        TC.C369581 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'CreateWorkItems - Sanity 2 Positive #caseId.ids #caseId.description'() {
        given:

        def createWorkItemsParameters = [
                config: configuration,
                title: title,
                project: project,
                type: type,
                priority: priority,
                assignTo: assignTo,
                description: description,
                additionalFields: additionalFields,
                workItemsJSON: workItemsJSON,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def itemIds = jobProperties['workItemIds']
        // if priority is empty , default value is 2
        priority = priority ?: '2'
        // if assignTo is empty it will use default user
        if (!assignTo && type=="Issue"){
            assignTo = userName
        }
        def splited = jobProperties['workItemIds'].split(', ')
        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == "Successfully created ${splited.length.toString()} work items."
            for (itemId in splited) {
                jobProperties[itemId]['id']
                jobProperties[itemId]['System.TeamProject'] == project
                if (itemId == splited[0]){
                    jobProperties[itemId]['System.Title'] == 'First'
                    jobProperties[itemId]['System.WorkItemType'] == 'Bug'
                } else {
                    jobProperties[itemId]['System.Title'] == title
                    jobProperties[itemId]['System.WorkItemType'] == type
                }

                if (itemId == splited[1]){
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'] == '1'
                    jobProperties[itemId]['System.AssignedTo'] =~ userName
                }
                else {
                    jobProperties[itemId]['System.AssignedTo'] =~ assignTo
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'] == priority
                }

                if (itemId == splited[2]){
                    jobProperties[itemId]['System.Description'] == "third description"
                    jobProperties[itemId]['System.State'] == "Active"
                }
                else {
                    jobProperties[itemId]['System.Description'] == description
                    if (additionalFields) {
                        for (value in addValue) {
                            jobProperties[itemId][value.value[0]] == value.value[1]
                            println jobProperties[itemId][value.value[0]]
                        }
                    }
                }
            }
        }
        cleanup:
        for (itemId in splited) {
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId     | configuration | title  | project        | type         | priority | assignTo         | description              | additionalFields                | workItemsJSON | resultFormat     | resultPropertySheet
        TC.C369612 | configName    | 'test' | tfsProjectName | "Issue"      | '4'      | secondUserName   | 'test'                   | addFields.SystemInfoReproSteps  | workItemJson  | 'propertySheet'  | '/myJob/newWorkItems'
    }


    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateWorkItem Positive #caseId.ids #caseId.description'() {
        given:

        def createWorkItemsParameters = [
                config: configuration,
                title: title,
                project: project,
                type: type,
                priority: priority,
                assignTo: assignTo,
                description: description,
                additionalFields: additionalFields,
                workItemsJSON: workItemsJSON,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def itemId = jobProperties['workItemIds']
        // if priority is empty , default value is 2
        priority = priority ?: '2'
        // if assignTo is empty it will use default user
        if (!assignTo && type=="Issue"){
            assignTo = userName
        }
        if (resultFormat == 'json') {
            jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
        }
        then:
        verifyAll {
            if (resultFormat == 'none'){
                !jobProperties[itemId]
            }
            else {
                result.outcome == 'success'
                jobSummary == 'Successfully created 1 work item.'
                jobProperties[itemId]['id']
                jobProperties[itemId]['System.TeamProject'] == project
                jobProperties[itemId]['System.Title'] == title
                jobProperties[itemId]['System.WorkItemType'] == type
                jobProperties[itemId]['Microsoft.VSTS.Common.Priority'].toString() == priority
                if (assignTo) {
                    jobProperties[itemId]['System.AssignedTo'] =~ assignTo
                }
                if (description) {
                    jobProperties[itemId]['System.Description'] == description
                }
                if (additionalFields) {
                    for (value in addValue) {
                        jobProperties[itemId][value.value[0]] == value.value[1]
                    }
                }
            }
        }
        cleanup:
        tfsClient.deleteWorkItem(itemId)

        where:
        caseId     | configuration | title  | project        | type         | priority | assignTo         | description              | additionalFields                | workItemsJSON | resultFormat     | resultPropertySheet
        TC.C369581 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369592 | configName    | 'test' | tfsProjectName | "Bug"        | ''       | ''               | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369592 | configName    | 'test' | tfsProjectName | "Epic"       | ''       | ''               | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369592 | configName    | 'test' | tfsProjectName | "Feature"    | ''       | ''               | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369592 | configName    | 'test' | tfsProjectName | "User Story" | ''       | ''               | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369593 | configName    | 'test' | tfsProjectName | "Bug"        | '4'      | ''               | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369594 | configName    | 'test' | tfsProjectName | "Bug"        | ''       | userName         | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369595 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | userName         | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369596 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | secondUserName   | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369597 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | descriptions.oneLine     | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369598 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | descriptions.multiLines  | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369601 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | descriptions.htmlTags    | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369609 | configName    | 'test' | tfsProjectName | "Task"       | ''       | ''               | 'test'                   | ''                              | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369610 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | addFields.SystemInfoReproSteps  | ''            | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369613 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | addFields.SystemInfoReproSteps  | ''            | 'json'           | '/myJob/newWorkItems'
        TC.C369615 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | addFields.SystemInfoReproSteps  | ''            | 'propertySheet'  | '/myJob/qaPath'
        TC.C369616 | configName    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | addFields.SystemInfoReproSteps  | ''            | 'none'           | '/myJob/newWorkItems'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateWorkItems Positive #caseId.ids #caseId.description'() {
        given:

        def createWorkItemsParameters = [
                config: configuration,
                title: title,
                project: project,
                type: type,
                priority: priority,
                assignTo: assignTo,
                description: description,
                additionalFields: additionalFields,
                workItemsJSON: workItemsJSON,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def itemIds = jobProperties['workItemIds']
        // if priority is empty , default value is 2
        priority = priority ?: '2'
        // if assignTo is empty it will use default user
        if (!assignTo && type=="Issue"){
            assignTo = userName
        }
        if (resultFormat == 'json') {
            for (itemId in jobProperties['workItemIds'].split(', ')) {
                jobProperties[itemId] = new JsonSlurper().parseText(jobProperties[itemId])
            }
        }
        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == "Successfully created ${jobProperties['workItemIds'].split(', ').length.toString()} work items."
            for (itemId in jobProperties['workItemIds'].split(', ')) {
                jobProperties[itemId]['id']
                jobProperties[itemId]['System.TeamProject'] == project
                jobProperties[itemId]['System.Title'] == title
                jobProperties[itemId]['System.WorkItemType'] == type
                jobProperties[itemId]['Microsoft.VSTS.Common.Priority'].toString() == priority
                if (assignTo) {
                    jobProperties[itemId]['System.AssignedTo'] =~ assignTo
                }
                if (description) {
                    jobProperties[itemId]['System.Description'] == description
                }
                if (additionalFields) {
                    for (value in addValue) {
                        jobProperties[itemId][value.value[0]] == value.value[1]
                        println jobProperties[itemId][value.value[0]]
                    }
                }
            }
        }
        cleanup:
        for (itemId in jobProperties['workItemIds'].split(', ')) {
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId     | configuration | title  | project        | type         | priority | assignTo         | description              | additionalFields                | workItemsJSON | resultFormat     | resultPropertySheet
        TC.C369611 | configName    | 'test' | tfsProjectName | "Issue"      | '4'      | secondUserName   | 'test'                   | addFields.SystemInfoReproSteps  | '[{}, {}]'    | 'propertySheet'  | '/myJob/newWorkItems'
        TC.C369614 | configName    | 'test' | tfsProjectName | "Issue"      | '4'      | secondUserName   | 'test'                   | addFields.SystemInfoReproSteps  | '[{}, {}]'    | 'json'           | '/myJob/newWorkItems'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateWorkItems - 3 different items, Positive #caseId.ids #caseId.description'() {
        given:

        def createWorkItemsParameters = [
                config: configuration,
                title: title,
                project: project,
                type: type,
                priority: priority,
                assignTo: assignTo,
                description: description,
                additionalFields: additionalFields,
                workItemsJSON: workItemsJSON,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def itemIds = jobProperties['workItemIds']
        // if priority is empty , default value is 2
        priority = priority ?: '2'
        // if assignTo is empty it will use default user
        if (!assignTo && type=="Issue"){
            assignTo = userName
        }
        def splited = jobProperties['workItemIds'].split(', ')
        then:
        verifyAll {
            result.outcome == 'success'
            jobSummary == "Successfully created ${splited.length.toString()} work items."
            for (itemId in splited) {
                jobProperties[itemId]['id']
                jobProperties[itemId]['System.TeamProject'] == project
                if (itemId == splited[0]){
                    jobProperties[itemId]['System.Title'] == 'First'
                    jobProperties[itemId]['System.WorkItemType'] == 'Bug'
                } else {
                    jobProperties[itemId]['System.Title'] == title
                    jobProperties[itemId]['System.WorkItemType'] == type
                }

                if (itemId == splited[1]){
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'] == '1'
                    jobProperties[itemId]['System.AssignedTo'] =~ userName
                }
                else {
                    jobProperties[itemId]['System.AssignedTo'] =~ assignTo
                    jobProperties[itemId]['Microsoft.VSTS.Common.Priority'] == priority
                }

                if (itemId == splited[2]){
                    jobProperties[itemId]['System.Description'] == "third description"
                    jobProperties[itemId]['System.State'] == "Active"
                }
                else {
                    jobProperties[itemId]['System.Description'] == description
                    if (additionalFields) {
                        for (value in addValue) {
                            jobProperties[itemId][value.value[0]] == value.value[1]
                            println jobProperties[itemId][value.value[0]]
                        }
                    }
                }
            }
        }
        cleanup:
        for (itemId in splited) {
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId     | configuration | title  | project        | type         | priority | assignTo         | description              | additionalFields                | workItemsJSON | resultFormat     | resultPropertySheet
        TC.C369612 | configName    | 'test' | tfsProjectName | "Issue"      | '4'      | secondUserName   | 'test'                   | addFields.SystemInfoReproSteps  | workItemJson  | 'propertySheet'  | '/myJob/newWorkItems'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateWorkItem Negative #caseId.ids #caseId.description'() {
        assumeFalse(caseId.ids == 'C369625' && authType != 'pat')
        given:
        def createWorkItemsParameters = [
                config: configuration,
                title: title,
                project: project,
                type: type,
                priority: priority,
                assignTo: assignTo,
                description: description,
                additionalFields: additionalFields,
                workItemsJSON: workItemsJSON,

                resultFormat: resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        when:
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        def jobSummary
        if (summary) {
            jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        }
        then:
        verifyAll {
            result.outcome == 'error'
            if (summary) {
                jobSummary.contains(summary)
            }
        }
        cleanup:

        where:
        caseId     | configuration              | title  | project        | type         | priority | assignTo         | description              | additionalFields                | workItemsJSON        | summary                                                                         |  resultFormat     | resultPropertySheet
        TC.C369623 | 'wrongConfig'              | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | ''                              | ''                   | summaries.wrongConfig  |  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369624 | wrongConfigNames['url']    | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | ''                              | ''                   | summaries.wrongHostmame|  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369625 | wrongConfigNames['token']  | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | ''                              | ''                   | summaries.unauthorized |  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369633 | configName                 | 'test' | 'wrongProject' | "Issue"      | ''       | ''               | 'test'                   | ''                              | ''                   | summaries.wrongProject |  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369634 | configName                 | 'test' | tfsProjectName | "wrongType"  | ''       | ''               | 'test'                   | ''                              | ''                   | summaries.wrongType    |  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369635 | configName                 | 'test' | tfsProjectName | "Issue"      | '55'     | ''               | 'test'                   | ''                              | ''                   | summaries.wrongPriority|  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369636 | configName                 | 'test' | tfsProjectName | "Issue"      | ''       | 'wrongUser'      | 'test'                   | ''                              | ''                   | summaries.wrongUser    |  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369637 | configName                 | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | addFields.wrongParameters       | ''                   | summaries.wrongpath    |  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369638 | configName                 | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | addFields.wrongFormat           | ''                   | summaries.wrongAddFiels|  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369639 | configName                 | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | ''                              | wrongItemJson.value  | summaries.wrongJson1   |  'propertySheet'  | '/myJob/newWorkItems'
        TC.C369640 | configName                 | 'test' | tfsProjectName | "Issue"      | ''       | ''               | 'test'                   | ''                              | wrongItemJson.format | summaries.wrongJson2   |  'propertySheet'  | '/myJob/newWorkItems'
    }

}