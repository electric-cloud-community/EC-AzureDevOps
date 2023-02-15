package com.electriccloud.plugin.spec

import groovy.json.JsonSlurper
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

import com.electriccloud.plugin.spec.tfs.TFSHelper
import spock.lang.*

import java.util.zip.GZIPInputStream


class UploadWorkItemAttachmentTest extends PluginTestHelper {

    @Shared
    def procedureName = "UploadWorkItemAttachment",
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
        fileUrl = "https://raw.githubusercontent.com/electric-cloud/hello-world-war/master/README.md",
        fileLocation = "/var/tmp/file",
        fileName = "file",
        fileChunkLocation = "/var/tmp/30MB.txt",
        fileChunkName = '30MB.txt',
        fileSize = 1024 * 1024 * 30,
        fileExtraChunkLocation = "/var/tmp/80MB.txt",
        fileExtraChunkName = '80MB.txt',
        fileExtraSize = 1024 * 1024 * 80

    @Shared
    def originalFile, originalChunkFile, originalExtraChunkFile

    @Shared
    def fileContents = [
            default: "Lorem ipsum dolor sit amet, consectetur adipiscing elit,\n" +
                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n" +
                    "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi\n" +
                    "ut aliquip ex ea commodo consequat. Duis aute irure dolor in\n" +
                    " reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
    ]

    @Shared
    def TC = [
            C369927: [ ids: 'C369927', description: 'Upload file'],
            C369928: [ ids: 'C369928', description: 'Upload file with comment '],
            C369929: [ ids: 'C369929', description: 'Upload file - file content '],
            C369930: [ ids: 'C369930', description: 'Upload file - Upload Type Chunked - file size > 130 mb'],
            C369941: [ ids: 'C369941', description: 'resultFormat - json (TO DO)'],
            C369942: [ ids: 'C369942', description: 'custom resultPropertySheet(TO DO) '],
            C369943: [ ids: 'C369943', description: 'empty required fields'],
            C369945: [ ids: 'C369945', description: 'empty File Path and FileContent'],
            C369946: [ ids: 'C369946', description: 'wrong values'],
            C369947: [ ids: 'C369947', description: 'Upload file - uploaded small file with chunked type'],
            C369950: [ ids: 'C369950', description: 'Upload file - uploaded large file with simple type '],
            C369978: [ ids: 'C369978', description: 'Upload file - uploaded large file which is higher than TFS limit'],
    ]

    @Shared
    def summaries = [
            default: "Attachment: $url/$collectionName/_apis/wit/attachments/.*?fileName=FILENAME",
            emptyConfig: 'Parameter "Configuration name" is mandatory',
            emptyFilename: 'Parameter "Attachment Filename" is mandatory',
            emptyID: 'Parameter "Work Item ID" is mandatory',
            emptyType: 'Parameter "Upload Type" is mandatory',
            emptyFormat: 'Parameter "Result Format" is mandatory',
            emptyFileAndContent: "Either 'File Path' or a 'File Content' should be specified.",
            emptySheet: 'Parameter "Result Property Sheet" is mandatory\n\n',
            emptyQueryIdAndText: "Either 'Query ID' or 'Query Text' should be present\n\n",
            emptyProject: "Your query contains reference to a project, but parameter 'Project' is not specified.\n\n",

            noWorkItems: "No work items was found for the query.",

            wrongConfig: 'Configuration "wrong" does not exist',
            wrongPath: "Parameter 'File Path' check failed: File '/wrong/Path' is not readable",
            error: "Upload failed. Check log for errors.",
            wrongType: "Upload type should be one of 'simple' or 'chunked'",
            wrongFormat: 'Cannot process format wrong: not implemented\n\n'
    ]

    @Shared
    TFSHelper tfsClient

    def doSetupSpec() {
        if (url == 'https://dev.azure.com'){
            userName = userEmail
        }

        tfsClient = getClient()
        createPluginConfigurationWithProxy(configName, configurationParams, credential, token_credential, proxy_credential)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: procedureName, params: uploadWorkItemAttachmentParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "CreateWorkItems", params: createWorkItemsParams]
        dslFile "dsl/GetResource.dsl",  [projectName: projectName, procedureName: "GetResource", fileURL: "", filePath: ""]
        dslFile "dsl/GenerateFile.dsl", [projectName: projectName, procedureName: "GenerateFile", filePath: fileChunkLocation, fileSize: fileSize ]
        generateFile()
        dslFile "dsl/GenerateFile.dsl", [projectName: projectName, procedureName: "GenerateFile", filePath: fileExtraChunkLocation, fileSize: fileExtraSize ]
        generateFile()
        runGetResourcesProcedure([fileURL: fileUrl, filePath: fileLocation])
//        runGetResourcesProcedure([fileURL: fileChunkUrl, filePath: fileChunkLocation])

        originalFile = new File(downloadFile(fileName, fileUrl))
//        originalChunkFile = new File(downloadFile(fileChunkName, fileChunkUrl))
//        originalChunkFile = new File(fileChunkLocation)
//        originalExtraChunkFile = new File(fileExtraChunkLocation)
    }

    def doCleanupSpec() {
        deleteConfiguration('EC-AzureDevOps', configName)
        originalFile.delete()
//        originalChunkFile.delete()
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'UploadWorkItemAttachment Sanity #caseId.ids #caseId.description'() {
        given:

        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue'  : defaultIssueFields, 'Epic': defaultFields,
                     'Feature': defaultFields, 'User Story': defaultFields]

        def workItemIds = ''
        def createItemsProperties
        for (def i = 0; i < itemCount; i++) {
            def param = [
                    config             : configuration,
                    title              : "TestItem ${caseId.ids}",
                    project            : tfsProjectName,
                    type               : types.keySet()[new Random().nextInt(types.keySet().size())],
                    priority           : priorities[new Random().nextInt(priorities.size())],
                    assignTo           : userName,
                    description        : "description for ${caseId.ids} $i",
                    additionalFields   : '',
                    workItemsJSON      : '',
                    resultFormat       : 'propertySheet',
                    resultPropertySheet: resultPropertySheet,]
            createWorkItemsParameters.add(param)
            if (i == 0) {
                createItemsProperties = getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]
                workItemIds += createItemsProperties['workItemIds']
            } else {
                workItemIds += ', ' + getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
            }
        }

        def uploadWorkItemAttachmentParameters = [
                "config" : configuration,
                "attachmentComment": comment,
                "fileContent": fileContent,
                "filename": filename,
                "filePath": filePath,
                "resultFormat": resultFormat,
                "resultPropertySheet": resultPropertySheet,
                "uploadType": uploadType,
                "workItemId": workItemIds,
        ]
        when:

        def result = runProcedure(projectName, procedureName, uploadWorkItemAttachmentParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        def attachmentLink = jobSummary.split(' ')[1]
        def downloadName = randomize('downloadFile')

        def attachedFile = getAttachedFile(workItemIds)
        def file2 = new File(downloadFile(downloadName, attachmentLink, token))

        def jsonResult = getUploadFileValue(workItemIds)

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary =~ summary.replace('FILENAME', filename)
            }
            if (apiVersion == '5.0'){
                attachedFile =~ attachmentLink.split("\\?")[0].replace('pluginsdev', 'pluginsdev/.*')
            }
            else {
                attachedFile == attachmentLink.split("\\?")[0]
            }

            jobProperties['id']
            jobProperties['url'] == attachmentLink

            if (caseId != TC.C369929) {
                if (uploadType == 'simple') {
                    originalFile.length() == file2.length()
                }
                if (uploadType == 'chunked') {
                    file2.length() == fileSize
                }
            }
            else {
                file2.text == fileContents.default
            }
            if (caseId == TC.C369928){
                jsonResult[1].'relations'.'added'.'attributes'.'comment'[0] == comment
            }
        }
        cleanup:
        tfsClient.deleteWorkItem(workItemIds)
        file2.delete()

        where:
        caseId     | configuration | comment    | fileContent                   | filename       | filePath                    | uploadType | resultFormat    | resultPropertySheet     | itemCount | summary           | outcome
        TC.C369928 | configName    | 'test123'  | ''                            | fileName       | fileLocation                | 'simple'   | 'propertySheet' | '/myJob/queryWorkItems' | 1         | summaries.default | 'success'
        // http://jira.electric-cloud.com/browse/ECTFS-144
        TC.C369930 | configName    | ''         | ''                            | fileChunkName  | fileChunkLocation           | 'chunked'   | 'propertySheet' | '/myJob/queryWorkItems' | 1         | summaries.default | 'success'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'UploadWorkItemAttachment Positive #caseId.ids #caseId.description'() {
        given:

        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue'  : defaultIssueFields, 'Epic': defaultFields,
                     'Feature': defaultFields, 'User Story': defaultFields]

        def workItemIds = ''
        def createItemsProperties
        for (def i = 0; i < itemCount; i++) {
            def param = [
                    config             : configuration,
                    title              : "TestItem ${caseId.ids}",
                    project            : tfsProjectName,
                    type               : types.keySet()[new Random().nextInt(types.keySet().size())],
                    priority           : priorities[new Random().nextInt(priorities.size())],
                    assignTo           : userName,
                    description        : "description for ${caseId.ids} $i",
                    additionalFields   : '',
                    workItemsJSON      : '',
                    resultFormat       : 'propertySheet',
                    resultPropertySheet: resultPropertySheet,]
            createWorkItemsParameters.add(param)
            if (i == 0) {
                createItemsProperties = getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]
                workItemIds += createItemsProperties['workItemIds']
            } else {
                workItemIds += ', ' + getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
            }
        }

        def uploadWorkItemAttachmentParameters = [
                "config" : configuration,
                "attachmentComment": comment,
                "fileContent": fileContent,
                "filename": filename,
                "filePath": filePath,
                "resultFormat": resultFormat,
                "resultPropertySheet": resultPropertySheet,
                "uploadType": uploadType,
                "workItemId": workItemIds,
        ]
        when:

        def result = runProcedure(projectName, procedureName, uploadWorkItemAttachmentParameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        def jobProperties = getJobProperties(result.jobId)[resultPropertySheet.split("/")[2]]
        if (resultFormat == 'json') {
            jobProperties = new JsonSlurper().parseText(jobProperties)
            for (j in jobProperties){
                j.value = j.value.toString()
            }
        }

        def attachmentLink = jobSummary.split(' ')[1]
        def downloadName = randomize('downloadFile')

        def attachedFile = getAttachedFile(workItemIds)
        def file2 = new File(downloadFile(downloadName, attachmentLink, token))

        def jsonResult = getUploadFileValue(workItemIds)
        //sleep a while after each test case to avoid 500 error from dev.azure.com
        sleep(30000)

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary =~ summary.replace('FILENAME', filename)
            }
            if (apiVersion == '5.0'){
                attachedFile =~ attachmentLink.split("\\?")[0].replace('pluginsdev', 'pluginsdev/.*')
            }
            else {
                attachedFile == attachmentLink.split("\\?")[0]
            }
            jobProperties['id']
            jobProperties['url'] == attachmentLink
            if (caseId != TC.C369929) {
                if (uploadType == 'simple') {
                    originalFile.length() == file2.length()
                }
                if (uploadType == 'chunked' && filename == fileChunkName) {
                    file2.length() == fileSize
                }
            }
            else {
                file2.text == fileContents.default
            }
            if (caseId == TC.C369928){
                jsonResult[1].'relations'.'added'.'attributes'.'comment'[0] == comment
            }
        }
        cleanup:
        tfsClient.deleteWorkItem(workItemIds)
        file2.delete()

        where:
        caseId     | configuration | comment    | fileContent                   | filename       | filePath                    | uploadType | resultFormat    | resultPropertySheet         | itemCount | summary           | outcome
        TC.C369927 | configName    | ''         | ''                            | fileName       | fileLocation                | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.default | 'success'
        // http://jira.electric-cloud.com/browse/ECTFS-142 Closed
        TC.C369928 | configName    | 'test123'  | ''                            | fileName       | fileLocation                | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.default | 'success'
        TC.C369929 | configName    | ''         | fileContents.default          | fileName       | ''                          | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.default | 'success'
        // http://jira.electric-cloud.com/browse/ECTFS-144
        TC.C369930 | configName    | ''         | ''                            | fileChunkName  | fileChunkLocation           | 'chunked'  | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.default | 'success'
        // http://jira.electric-cloud.com/browse/ECTFS-144
        TC.C369947 | configName    | ''         | ''                            | fileName       | fileLocation                | 'chunked'  | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.default | 'success'
        TC.C369927 | configName    | ''         | ''                            | fileName       | fileLocation                | 'simple'   | 'json'          | '/myJob/workItemAttachment' | 1         | summaries.default | 'success'
        TC.C369927 | configName    | ''         | ''                            | fileName       | fileLocation                | 'simple'   | 'propertySheet' | '/myJob/qa'                 | 1         | summaries.default | 'success'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'UploadWorkItemAttachment Negative Upload file - uploaded large file with simple type #caseId.ids '() {
        given:

        def createWorkItemsParameters = []
        def priorities = ['2', '3', '4']
        def defaultIssueFields = defaultFields - 'Microsoft.VSTS.Common.ValueArea' +
                'Microsoft.VSTS.Common.ActivatedBy' + 'Microsoft.VSTS.Common.ActivatedDate'
        def types = ['Issue'  : defaultIssueFields, 'Epic': defaultFields,
                     'Feature': defaultFields, 'User Story': defaultFields]

        def workItemIds = ''
        def createItemsProperties
        for (def i = 0; i < itemCount; i++) {
            def param = [
                    config             : configuration,
                    title              : "TestItem ${caseId.ids}",
                    project            : tfsProjectName,
                    type               : types.keySet()[new Random().nextInt(types.keySet().size())],
                    priority           : priorities[new Random().nextInt(priorities.size())],
                    assignTo           : userName,
                    description        : "description for ${caseId.ids} $i",
                    additionalFields   : '',
                    workItemsJSON      : '',
                    resultFormat       : 'propertySheet',
                    resultPropertySheet: resultPropertySheet,]
            createWorkItemsParameters.add(param)
            if (i == 0) {
                createItemsProperties = getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]
                workItemIds += createItemsProperties['workItemIds']
            } else {
                workItemIds += ', ' + getJobProperties(runProcedure(projectName, "CreateWorkItems", param).jobId)[resultPropertySheet.split("/")[2]]['workItemIds']
            }
        }

        def uploadWorkItemAttachmentParameters = [
                "config" : configuration,
                "attachmentComment": comment,
                "fileContent": fileContent,
                "filename": filename,
                "filePath": filePath,
                "resultFormat": resultFormat,
                "resultPropertySheet": resultPropertySheet,
                "uploadType": uploadType,
                "workItemId": workItemIds,
        ]
        when:

        def result = runProcedure(projectName, procedureName, uploadWorkItemAttachmentParameters)

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary =~ summary.replace('FILENAME', filename)
            }
        }
        cleanup:
        tfsClient.deleteWorkItem(workItemIds)

        where:
        caseId     | configuration | comment    | fileContent                   | filename           | filePath                    | uploadType | resultFormat    | resultPropertySheet         | itemCount | summary           | outcome
        TC.C369950 | configName    | ''         | ''                            | fileChunkName      | fileChunkLocation           | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | ''                | 'success'
        TC.C369978 | configName    | ''         | ''                            | fileExtraChunkName | fileExtraChunkLocation      | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | ''                | 'error'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'UploadWorkItemAttachment Negative #caseId.ids #caseId.description'() {
        given:
        def uploadWorkItemAttachmentParameters = [
                "config" : configuration,
                "attachmentComment": comment,
                "fileContent": fileContent,
                "filename": filename,
                "filePath": filePath,
                "resultFormat": resultFormat,
                "resultPropertySheet": resultPropertySheet,
                "uploadType": uploadType,
                "workItemId": workItemIds,
        ]
        when:

        def result = runProcedure(projectName, procedureName, uploadWorkItemAttachmentParameters)
        def jobSummary
        if (summary) {
            jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
        }

        then:
        verifyAll {
            result.outcome == outcome
            if (summary) {
                jobSummary =~ summary.replace('FILENAME', filename)
            }
        }
        cleanup:

        where:
        caseId     | configuration | workItemIds | comment    | fileContent                   | filename       | filePath               | uploadType | resultFormat    | resultPropertySheet         | itemCount | summary                       | outcome
        TC.C369943 | ''            | '100'       | ''         | ''                            | fileName       | fileLocation           | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.emptyConfig         | 'error'
        TC.C369943 | configName    | ''          | ''         | ''                            | fileName       | fileLocation           | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.emptyID             | 'error'
        TC.C369943 | configName    | '100'       | ''         | ''                            | ''             | fileLocation           | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.emptyFilename       | 'error'
        TC.C369943 | configName    | '100'       | ''         | ''                            | fileName       | fileLocation           | ''         | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.emptyType           | 'error'
        TC.C369943 | configName    | '100'       | ''         | ''                            | fileName       | fileLocation           | 'simple'   | ''              | '/myJob/workItemAttachment' | 1         | summaries.emptyFormat         | 'error'
        TC.C369945 | configName    | '100'       | ''         | ''                            | fileName       | ''                     | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.emptyFileAndContent | 'error'
        TC.C369946 | 'wrong'       | '100'       | ''         | ''                            | fileName       | fileLocation           | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.wrongConfig         | 'error'
        TC.C369946 | configName    | '999999999' | ''         | ''                            | fileName       | fileLocation           | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | ''                            | 'error'
        TC.C369946 | configName    | '100'       | ''         | ''                            | fileName       | '/wrong/Path'          | 'simple'   | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.wrongPath           | 'error'
        TC.C369946 | configName    | '100'       | ''         | ''                            | fileName       | fileLocation           | 'wrong'    | 'propertySheet' | '/myJob/workItemAttachment' | 1         | summaries.wrongType           | 'error'

    }

    def getUploadFileValue(def workItemIds){
        def http = new HTTPBuilder("$url/$collectionName/_apis/wit/workitems/$workItemIds/updates")
        http.ignoreSSLIssues()
        def authData = ':' + token
        if (authType != 'pat'){
            authData = "$domain_name\\$userName:$password"
        }
        def authHeaderValue = "Basic ${authData.bytes.encodeBase64().toString()}"
        return http.request(GET, JSON){
            println uri.toString()
            headers.'Authorization' = authHeaderValue
            response.success = { resp, json ->
                return json.get('value')
            }
        }
    }


    def downloadFile(def filePath, def fileDownloadUrl, def userToken=null){
        new File(filePath).withOutputStream { out ->
            def url = new URL(fileDownloadUrl).openConnection()
            if (userToken) {
                def remoteAuth = "Basic " + ":$userToken".bytes.encodeBase64()
                if (authType != 'pat') {
                    remoteAuth = "Basic " + "$domain_name\\$userName:$password".bytes.encodeBase64()
                }
                url.setRequestProperty("Authorization", remoteAuth);
            }
            out << url.inputStream
        }
        def tmpFile = new File(filePath)
        println filePath
        if (userToken) {
            try {
                def outputName = filePath + 'Output'
                FileInputStream fis = new FileInputStream(filePath);
                GZIPInputStream gis = new GZIPInputStream(fis);
                FileOutputStream fos = new FileOutputStream(outputName);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gis.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                //close resources
                fos.close();
                gis.close();
                tmpFile.delete()
                return outputName
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return filePath
    }

    def getAttachedFile(def workItemID){
        def http = new HTTPBuilder("$url/$collectionName/_apis/wit/workItems/$workItemID/updates?api-version=$apiVersion")
        http.ignoreSSLIssues()
        def authData = ':' + token
        if (authType != 'pat'){
            authData = "$domain_name\\$userName:$password"
        }
        def authHeaderValue = "Basic ${authData.bytes.encodeBase64().toString()}"
        def jsonResult = http.request(GET, JSON){
            println uri.toString()
            headers.'Authorization' = authHeaderValue
            response.success = { resp, json ->
                return json['value'][1]['relations']['added'][0]['url']
            }
        }
        return jsonResult
    }

    def generateFile(){
        def code = """
            runProcedure(
                projectName: '$projectName',
                procedureName: 'GenerateFile',
            )
        """
        def result = dsl(code)
        waitUntil {
            try {
                jobCompleted(result)
            } catch (Exception e) {
                println e.getMessage()
            }
        }
        return result
    }

    def runGetResourcesProcedure(def params) {
        def code = """
            runProcedure(
                projectName: '$projectName',
                procedureName: 'GetResource',
                actualParameter: [
                    filePath: '$params.filePath',
                    fileURL: '$params.fileURL',
                    token: '$params.token',
                ]
            )
        """
        def result = dsl(code)
        waitUntil {
            try {
                jobCompleted(result)
            } catch (Exception e) {
                println e.getMessage()
            }
        }
        return result
    }

}
