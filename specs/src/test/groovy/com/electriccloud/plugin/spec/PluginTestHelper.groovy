package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper

/*

Copyright 2019 Electric Cloud, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/

import com.electriccloud.spec.*
import spock.lang.*

class PluginTestHelper extends PluginSpockTestSupport {

    static String PLUGIN_NAME = 'EC-AzureDevOps'
    static String automationTestsContextRun = System.getenv('AUTOMATION_TESTS_CONTEXT_RUN') ?: ''
    static String pluginVersion = System.getenv('PLUGIN_VERSION') ?: ''
    static String authType = System.getenv('ADOS_AUTH_TYPE')
    static String CONFIG_NAME = 'specConfig'

    @Shared
    def tfsProjectName = getADOSProjectName(),
        configName = "configForTests",
        wrongConfigNames = [
                'url': 'configWrongUrl',
                'token': 'configWrongToken'
        ],
        apiVersion = getADOSApiVersion(),
        collectionName = getADOSCollectionName(),
        url = getADOSURL(),
        userName = getADOSUsername(),
        secondUserName = getADOSSecondUsername(),
        userEmail = getADOSEmail(),
        token = getADOSToken(),
        password = getADOSPassword(),
        domain_name = getADOSDomainName(),
        proxy = System.getenv('EF_PROXY_URL') ?: '',
        proxyUsername = System.getenv('EF_PROXY_USERNAME') ?: '',
        proxyPassword = System.getenv('EF_PROXY_PASSWORD') ?: '',
        credential = [
                credentialName: 'credential',
                username: (authType == 'basic' ? domain_name + '\\\\' : '') + userName,
                password: password
        ],
        proxy_credential = [
                credentialName: 'proxy_credential',
                username: proxyUsername,
                password: proxyPassword
        ],
        token_credential = [
                    credentialName: 'token_credential',
                    username: '',
                    password: token
        ],
        configurationParams = [
                apiVersion         : apiVersion,
                auth               : authType,
                checkConnection    : '0',
                collection         : collectionName,
                debugLevel         : '2',
                desc               : '',
                endpoint           : url,
                http_proxy         : proxy,
                credential         : 'credential',
                token_credential   : 'token_credential',
                proxy_credential   : 'proxy_credential'
        ],
        createWorkItemsParams = [
                additionalFields: "",
                assignTo: "",
                config: "",
                description: "",
                priority: "",
                project: "",
                resultFormat: "",
                resultPropertySheet: "",
                title: "",
                type: "",
                workItemsJSON: "",
        ],
        getWorkItemsParams = [
                asOf: "",
                config: "",
                expandRelations: "",
                fields: "",
                resultFormat: "",
                resultPropertySheet: "",
                workItemIds: "",
        ],
        updateWorkItemsParams = [
                "additionalFields" : "",
                "assignTo" : "",
                "commentBody" : "",
                "config" : "",
                "description" : "",
                "priority" : "",
                "resultFormat" : "",
                "resultPropertySheet" : "",
                "title" : "",
                "workItemIds" : "",
            ],
        deleteWorkItemsParams = [
                "config" : "",
                "resultFormat" : "",
                "resultPropertySheet" : "",
                 "workItemIds" : "",
            ],
        getDefaultValuesParams = [
                "config" : "",
                "project" : "",
                "resultFormat" : "",
                "resultPropertySheet" : "",
                "workItemType" : "",
        ],
        queryWorkItemsParams = [
                "config" : "",
                "project" : "",
                "queryId" : "",
                "queryText" : "",
                "resultFormat" : "",
                "resultPropertySheet" : "",
                "timePrecision" : "",
        ],
        getBuild = [
                "config" : "",
                "project" : "",
                "buildId" : "",
                "buildDefinitionName" : "",
                "waitForBuild" : "",
                "waitTimeout" : "",
                "resultPropertySheet" : "",
                "resultFormat" : ""
        ],
        uploadWorkItemAttachmentParams = [
                "config" : "",
                "attachmentComment": "",
                "fileContent": "",
                "filename": "",
                "filePath": "",
                "resultFormat": "",
                "resultPropertySheet": "",
                "uploadType": "",
                "workItemId": "",
        ],
        triggerBuildParams = [
                "config": "",
                "definitionId": "",
                "parameters": "",
                "project": "",
                "queueId": "",
                "resultFormat": "",
                "resultPropertySheet": "",
                "sourceBranch": "",
        ]


    def createConfiguration(String configName = CONFIG_NAME, Map props = [:]) {

        String username = getADOSUsername()

        // TOKEN SHOULD BE USED INSTEAD OF A PASSWORD FOR BASIC AUTH
        String url = getADOSURL()
        String collectionName = getADOSCollectionName()
        String apiVersion = getADOSApiVersion()
        String logLevel = 10

        String password = authType == 'pat' ? getADOSToken() : getADOSPassword()
        if (authType == 'basic'){
            username = "$domain_name\\\\$userName"
        }

        def isProxyAvailable = System.getenv('IS_PROXY_AVAILABLE') ?: false

        if (System.getenv('RECREATE_CONFIG')) {
            props.recreate = true
        }

        def confPath = props.confPath ?: 'ec_plugin_cfgs'
        if (doesConfExist("/plugins/${PLUGIN_NAME}/project/$confPath", configName)) {
            if (props.recreate) {
                deleteConfiguration(PLUGIN_NAME, configName)
            } else {
                println "Configuration $configName exists"
                return
            }
        }

        if (!isProxyAvailable){
            createPluginConfigurationWithProxy(
                configName,
                [
                    desc      : 'Spec Tests Config',
                    endpoint  : url,
                    collection: collectionName,
                    apiVersion: apiVersion,
                    auth      : authType,
                    debugLevel: logLevel,
                    http_proxy: '',
                    checkConnection : 0
                ],
                [
                        username : username,
                        password : password,
                ],
                [
                        username : '',
                        password : password,
                ],
            )
        }
        else {
            def efProxyUrl = System.getenv('EF_PROXY_URL') ?: ''
            def efProxyUsername = System.getenv('EF_PROXY_USERNAME') ?: ''
            def efProxyPassword = System.getenv('EF_PROXY_PASSWORD') ?: ''

            createPluginConfigurationWithProxy(
                configName,
                [
                    desc      : 'Spec Tests Config',
                    endpoint  : url,
                    collection: collectionName,
                    apiVersion: apiVersion,
                    auth      : authType,
                    debugLevel: logLevel,
                    http_proxy: efProxyUrl,
                    checkConnection : 1
                ],
                [
                    username : username,
                    password : password,
                ],
                [
                    username : '',
                    password : password,
                ],
                [
                    username : efProxyUsername,
                    password : efProxyPassword,
                ]
            )
        }
    }

    static String getAssertedEnvVariable(String varName) {
        String varValue = System.getenv(varName)
        assert varValue
        return varValue
    }

    static String getADOSUsername() { getAssertedEnvVariable("ADOS_USERNAME") }

    static String getADOSSecondUsername() { getAssertedEnvVariable("ADOS_SECOND_USERNAME") }

    static String getADOSToken() { getAssertedEnvVariable("ADOS_TOKEN") }

    static String getADOSPassword() { getAssertedEnvVariable("ADOS_PASSWORD") }

    static String getADOSURL() { getAssertedEnvVariable("ADOS_URL") }

    static String getADOSCollectionName() { getAssertedEnvVariable("ADOS_COLLECTION") }

    static String getADOSProjectName() { getAssertedEnvVariable("ADOS_PROJECT_NAME") }

    static String getADOSApiVersion() { getAssertedEnvVariable("ADOS_API_VERSION") }

    static String getADOSDomainName() { getAssertedEnvVariable("ADOS_DOMAIN_NAME") }

    static def getBuildDefinitionName() { getAssertedEnvVariable("BUILD_DEFINITION_NAME") }

    static def getADOSEmail() { getAssertedEnvVariable("ADOS_EMAIL") }

    static TFSHelper getClient(String apiVersion = getADOSApiVersion()) {
        def username = (authType == 'basic' ? getADOSDomainName() + '\\' : '') + getADOSUsername()
        def password = authType == 'pat' ? getADOSToken() : getADOSPassword()

        TFSHelper helper = new TFSHelper(
            getADOSURL(),
            username,
            password,
            getADOSCollectionName(),
            getADOSProjectName(),
        )

        helper.setApiVersion(apiVersion)

        assert helper.isAuthenticated()
        return helper
    }

    def createWorkItems(TFSHelper tfsClient, int count) {
        def createdWorkItems = []

        for (def i = 0; i < count; i++) {
            String title = "Test Work Item ${i}"
            Map workItemParams = [title: title, description: 'If you see this, delete me']
            def item = tfsClient.createWorkItem('Bug', workItemParams)
            assert item, item.id
            logger.debug("Created Work Item #${item.id}")
            createdWorkItems.push(item)
        }

        return createdWorkItems
    }

    def redirectLogs(String parentProperty = '/myJob') {
        def propertyLogName = parentProperty + '/debug_logs'
        dsl """
            setProperty(
                propertyName: "/plugins/EC-AzureDevOps/project/ec_debug_logToProperty",
                value: "$propertyLogName"
            )
        """
        return propertyLogName
    }

    def redirectLogsToPipeline() {
        def propertyName = '/myPipelineRuntime/debugLogs'
        dsl """
            setProperty(
                propertyName: "/plugins/EC-AzureDevOps/project/ec_debug_logToProperty",
                value: "$propertyName"
            )
        """
        propertyName
    }

    def getJobLogs(def jobId) {
        assert jobId
        def logs
        try {
            logs = getJobProperty("/myJob/debug_logs", jobId)
        } catch (Throwable e) {
            logs = "Possible exception in logs; check job"
        }
        logs
    }

    def getPipelineLogs(flowRuntimeId) {
        assert flowRuntimeId
        getPipelineProperty('/myPipelineRuntime/debugLogs', flowRuntimeId)
    }

    def runProcedureDsl(dslString) {
        redirectLogs()
        assert dslString

        def result = dsl(dslString)
        assert result.jobId
        waitUntil {
            jobCompleted result.jobId
        }
        def logs = getJobLogs(result.jobId)
        def outcome = jobStatus(result.jobId).outcome
        logger.debug("DSL: $dslString")
        logger.debug("Logs: $logs")
        logger.debug("Outcome: $outcome")
        [logs: logs, outcome: outcome, jobId: result.jobId]
    }

    def getCurrentProcedureName(def jobId) {
        assert jobId
        def currentProcedureName = null
        def property = "/myJob/procedureName"
        try {
            currentProcedureName = getJobProperty(property, jobId)
            println("Current Procedure Name: " + currentProcedureName)
        } catch (Throwable e) {
            logger.debug("Can't retrieve Run Procedure Name from the property: '$property'; check job: " + jobId)
        }
        return currentProcedureName
    }

    def getJobUpperStepSummary(def jobId) {
        assert jobId
        def summary
        def currentProcedureName = getCurrentProcedureName(jobId)
        def property = "/myJob/jobSteps/$currentProcedureName/summary"
        println "Trying to get the summary for Procedure: $currentProcedureName, property: $property, jobId: $jobId"
        try {
            summary = getJobProperty(property, jobId)
        } catch (Throwable e) {
            logger.debug("Can't retrieve Upper Step Summary from the property: '$property'; check job: " + jobId)
        }
        return summary
    }

    def getStepSummary(def jobId, def stepName) {
        assert jobId
        def summary
        def property = "/myJob/jobSteps/$stepName/summary"
        println "Trying to get the summary for Procedure: checkConnection, property: $property, jobId: $jobId"
        try {
            summary = getJobProperty(property, jobId)
        } catch (Throwable e) {
            logger.debug("Can't retrieve Upper Step Summary from the property: '$property'; check job: " + jobId)
        }
        return summary
    }

    def getPipelineOutputParameters(def flowRuntimeId) {
        assert flowRuntimeId
        def prams = getPipelineProperty('/myPipelineRuntime/', flowRuntimeId)
    }

    def getJobOutputParameters(def jobId, def stepNumber) {
        assert jobId
        def outputParameters = []
        def stepId = getJobStepId(jobId, stepNumber)
        try {
            outputParameters = dsl """
            getOutputParameters(
                jobStepId: '$stepId'
                )
          """
        } catch (Throwable e) {
            logger.debug("Can't retrieve output parameters for job: " + jobId)
            e.printStackTrace()
        }

        def map = [:]
        for (i in outputParameters.outputParameter) {
            map[(String) i.outputParameterName] = i.value
        }
        return map
    }

    def createResource(def resName) {
        dsl """
            createResource(
                resourceName: '$resName',
                hostName: '127.0.0.1',
                port: '7800'
            )
        """
    }

    def conditionallyDeleteProject(String projectName) {
        if (System.getenv("LEAVE_TEST_PROJECTS")) {
            return
        }
        dsl "deleteProject(projectName: '$projectName')"
    }

    static boolean mapEquals(Map map1, Map map2) {
        return map1 == map2
    }

    static boolean arrEquals(def list1, def list2) {
        if (list1.size() != list2.size()) {
            return false
        }

        def sorted1 = list1.sort()
        def sorted2 = list2.sort()

        for (int i = 0; i < list1.size(); i++) {
            if (sorted1.getAt(i).toString() != sorted2.getAt(i).toString()) {
                return false
            }
        }

        return true
    }

    def runProcedure(String projectName, String procedureName, Map parameters) {

        // Skip undefined values
        def parametersString = parameters
            .findAll { k, v -> v != null }
            .collect { k, v ->
            v = ((String) v).replace('\'', '\\\'')
            "$k: '''$v'''"
        }.join(', ')

        def code = """
            runProcedure(
                projectName: '$projectName',
                procedureName: '$procedureName',
                actualParameter: [
                    $parametersString                 
                ]
            )
        """

        return runProcedureDsl(code)
    }

    def getJobLink(def jobId) {
        String serverHost = System.getProperty("COMMANDER_SERVER")
        return "https://" + serverHost + "/commander/link/jobDetails/jobs/" + jobId.toString()
    }

    def getResourceName() {
        return 'local'
    }

    Object createPluginConfigurationWithProxy(String configName, Map params, Map credential, Map tokenCredential, Map proxyCredential=[username:'', password:''], String expectedStatus='success') {
        assert configName


        def result = dsl """
            runProcedure(
                projectName: '/plugins/EC-AzureDevOps/project',
                procedureName: 'CreateConfiguration',
                credential: [
                    [
                        credentialName: 'proxy_credential',
                        userName: '${proxyCredential.username}',
                        password: '${proxyCredential.password}'
                    ],
                    [
                        credentialName: 'credential',
                        userName: '${credential.username}',
                        password: '${credential.password}'
                    ],
                    [
                        credentialName: 'token_credential',
                        userName: '${tokenCredential.username}',
                        password: '${tokenCredential.password}'
                    ],
                ],
                actualParameter: [
                    config          : '${configName}', 
                    desc            : '${params.desc}',
                    endpoint        : '${params.endpoint}',
                    collection      : '${params.collection}',
                    apiVersion      : '${params.apiVersion}',
                    auth            : '${params.auth}',
                    debugLevel      : '${params.debugLevel}',
                    http_proxy      : '${params.http_proxy}',
                    checkConnection : '${params.checkConnection}',
                    credential      : 'credential',
                    proxy_credential: 'proxy_credential',
                    token_credential: 'token_credential',
                ]
            )
            """

        assert result?.jobId
        waitUntil {
            jobCompleted(result)
        }
        assert jobStatus(result.jobId).outcome == expectedStatus
    }
}
