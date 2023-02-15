package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import spock.lang.*
import static org.junit.Assume.*

class CreateDeleteConfig extends PluginTestHelper {

    @Shared
    def procedureName = "CreateDeleteConfig",
        projectName = "Spec Tests $procedureName",
        deleteConfigParams = [
                config: "",
        ]

    @Shared
    TFSHelper tfsClient

    @Shared
    def TC = [
            C369518: [ ids: 'C369518, C369571, C369572', description: 'Create Basic configuration - use PAT'],
            C369539: [ ids: 'C369539', description: 'Create Basic configuration - use password '],
            C369538: [ ids: 'C369538', description: 'Auth Type: NTLM '],
            C369567: [ ids: 'C369567', description: 'Check Connection - True '],
            C369569: [ ids: 'C369569', description: 'Custom API Versions'],
            C369573: [ ids: 'C369573', description: 'Create Basic configuration - wrong url'],
            C369574: [ ids: 'C369574', description: 'Create Basic configuration - wrong creds'],
            C369568: [ ids: 'C369568', description: 'Check Connection - True (wrong url)'],
            C369570: [ ids: 'C369570', description: 'Custom API Versions, wrong values (workitems=6.0)'],
            C369575: [ ids: 'C369575', description: 'Wrong Proxy'],

            C369576: [ ids: 'C369576', description: 'Delete Config '],
            C369577: [ ids: 'C369577', description: 'Delete wrong Config '],

    ]

    def doSetupSpec() {
        tfsClient = getClient()
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "CreateWorkItems", params: createWorkItemsParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, procedureName: "DeleteConfiguration", params: deleteConfigParams]
    }

    def doCleanupSpec() {
    }

    @Unroll
    @Requires ({PluginTestHelper.automationTestsContextRun =~ "Sanity"})
    def 'CreateConfig Sanity #caseId.ids #caseId.description'() {
        def workItemWasCrated = false
        assumeFalse(auth == 'pat' && authType != 'pat')
        assumeFalse(auth == 'basic' && authType != 'basic')
        given:
        def credential = [
                credentialName: 'credential',
                username: credentialLogin,
                password: credentialPassword
        ]
        def proxy_credential = [
                credentialName: 'proxy_credential',
                username: proxy_credential_login,
                password: proxy_credential_pass
        ]
        def token_credential = [
                credentialName: 'token_credential',
                username: token_credential_login,
                password: token_credential_pass
        ]
        def procedureParams = [
                apiVersion         : apiTFSVersion,
                auth               : auth,
                checkConnection    : checkConnection,
                collection         : collection,
                debugLevel         : debugLevel,
                desc               : desc,
                endpoint           : endpoint,
                http_proxy         : http_proxy,
                credential         : 'credential',
                proxy_credential   : 'proxy_credential',
                token_credential   : 'token_credential'
        ]
        def createWorkItemsParameters = [
                config: configurationName,
                project: tfsProjectName,
                resultFormat: 'propertySheet',
                resultPropertySheet: '/myJob/newWorkItems',
                title: 'test',
                type: 'Bug',
        ]
        when:
        createPluginConfigurationWithProxy(configurationName, procedureParams, credential, token_credential, proxy_credential)
        workItemWasCrated = true
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        def itemId = getJobProperties(result.jobId)[createWorkItemsParameters.resultPropertySheet.split("/")[2]]['workItemIds']
        then:
        assert result.outcome == 'success'
        cleanup:
        if (workItemWasCrated) {
            tfsClient.deleteWorkItem(itemId)
        }
        deleteConfiguration('EC-AzureDevOps', configurationName)
        where:
        caseId       | apiTFSVersion | auth            | checkConnection | collection            | configurationName | credentialLogin             | credentialPassword | token_credential_login | token_credential_pass |  debugLevel | desc           | endpoint  | http_proxy | proxy_credential_login | proxy_credential_pass
        TC.C369518   | apiVersion    | 'pat'           | '0'             | collectionName        | 'test'            | ''                          | ''                 | ''                     | token                 |  '0'        | 'description'  | url       | proxy      | proxyUsername          | proxyPassword
        TC.C369539   | apiVersion    | 'basic'         | '0'             | collectionName        | 'test'            | domain_name+"\\\\"+userName | password           | ''                     | ''                    | '0'         | ''             | url       | ''         | ''                     | ''
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateConfig Positive #caseId.ids #caseId.description'() {
        def workItemWasCrated = false
        assumeFalse(caseId == TC.C369538 && url == 'https://dev.azure.com')
        assumeFalse(auth == 'pat' && authType != 'pat')
        assumeFalse(auth == 'basic' && authType != 'basic')
        given:
        def credential = [
                credentialName: 'credential',
                username: credentialLogin,
                password: credentialPassword
        ]
        def proxy_credential = [
                credentialName: 'proxy_credential',
                username: proxy_credential_login,
                password: proxy_credential_pass
        ]
        def token_credential = [
                credentialName: 'token_credential',
                username: token_credential_login,
                password: token_credential_pass
        ]
        def procedureParams = [
                apiVersion         : apiTFSVersion,
                auth               : auth,
                checkConnection    : checkConnection,
                collection         : collection,
                debugLevel         : debugLevel,
                desc               : desc,
                endpoint           : endpoint,
                http_proxy         : http_proxy,
                credential         : 'credential',
                proxy_credential   : 'proxy_credential',
                token_credential   : 'token_credential'
        ]
        def createWorkItemsParameters = [
                config: configurationName,
                project: tfsProjectName,
                resultFormat: 'propertySheet',
                resultPropertySheet: '/myJob/newWorkItems',
                title: 'test',
                type: 'Bug',
        ]
        when:
        createPluginConfigurationWithProxy(configurationName, procedureParams, credential, token_credential, proxy_credential)
        workItemWasCrated = true
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        def itemId = getJobProperties(result.jobId)[createWorkItemsParameters.resultPropertySheet.split("/")[2]]['workItemIds']
        then:
        assert result.outcome == 'success'
        cleanup:
        deleteConfiguration('EC-AzureDevOps', configurationName)
        if (workItemWasCrated)
        {
            tfsClient.deleteWorkItem(itemId)
        }
        where:
        caseId       | apiTFSVersion | auth            | checkConnection | collection            | configurationName | credentialLogin             | credentialPassword | token_credential_login | token_credential_pass |  debugLevel | desc     | endpoint  | http_proxy | proxy_credential_login | proxy_credential_pass
        TC.C369518   | apiVersion    | 'pat'           | '0'             | collectionName        | 'test'            | ''                          | ''                 | ''                     | token                 |  '0'        | ''       | url       | proxy      | proxyUsername          | proxyPassword
        TC.C369518   | apiVersion    | 'pat'           | '0'             | collectionName        | 'test'            | ''                          | ''                 | ''                     | token                 |  '0'        | 'desc'   | url       | proxy      | proxyUsername          | proxyPassword
//// https://github.com/Microsoft/tfs-cli/blob/master/docs/configureBasicAuth.md
        TC.C369539   | apiVersion    | 'basic'         | '0'             | collectionName        | 'test'            | domain_name+"\\\\"+userName | password           | ''                     | ''                    | '0'         | ''       | url       | ''         | ''                     | ''
        TC.C369538   | apiVersion    | 'ntlm'          | '0'             | collectionName        | 'test'            | userName                    | password           | ''                     | ''                    |  '0'        | ''       | url       | proxy      | proxyUsername          | proxyPassword
        TC.C369567   | apiVersion    | 'basic'         | '1'             | collectionName        | 'test'            | domain_name+"\\\\"+userName | password           | ''                     | ''                    |  '0'        | ''       | url       | proxy      | proxyUsername          | proxyPassword
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'CreateConfig Negative #caseId.ids #caseId.description'() {
        given:
        def credential = [
                credentialName: 'credential',
                username: credentialLogin,
                password: credentialPassword
        ]
        def proxy_credential = [
                credentialName: 'proxy_credential',
                username: proxy_credential_login,
                password: proxy_credential_pass
        ]
        def token_credential = [
                credentialName: 'token_credential',
                username: token_credential_login,
                password: token_credential_pass
        ]
        def procedureParams = [
                apiVersion         : apiTFSVersion,
                auth               : auth,
                checkConnection    : checkConnection,
                collection         : collection,
                debugLevel         : debugLevel,
                desc               : desc,
                endpoint           : endpoint,
                http_proxy         : http_proxy,
                credential         : 'credential',
                proxy_credential   : 'proxy_credential',
                token_credential   : 'token_credential'
        ]
        def createWorkItemsParameters = [
                config: configName,
                project: tfsProjectName,
                resultFormat: 'propertySheet',
                resultPropertySheet: '/myJob/newWorkItems',
                title: 'test',
                type: 'Bug',
        ]
        when:
        if (caseId == TC.C369568) {
            createPluginConfigurationWithProxy(configName, procedureParams, credential, token_credential, proxy_credential, 'error')
        }
        else {
            createPluginConfigurationWithProxy(configName, procedureParams, credential, token_credential, proxy_credential)
        }
        def result = runProcedure(projectName, "CreateWorkItems", createWorkItemsParameters)
        then:
        assert result.outcome == 'error'
        cleanup:
        deleteConfiguration('EC-AzureDevOps', configName)
        where:
        caseId       | apiTFSVersion | auth            | checkConnection | collection            | configName | credentialLogin             | credentialPassword | token_credential_login | token_credential_pass | debugLevel | desc     | endpoint  | http_proxy | proxy_credential_login | proxy_credential_pass
        TC.C369573   | apiVersion    | 'basic'         | '0'             | collectionName        | 'test'     | userName                    | token              | ''                     | ''                    | '0'        | ''       | 'wrong'   | proxy      | proxyUsername          | proxyPassword
        TC.C369574   | apiVersion    | 'basic'         | '0'             | collectionName        | 'test'     | userName                    | 'wrong'            | ''                     | ''                    | '0'        | ''       | url       | proxy      | proxyUsername          | proxyPassword
        TC.C369574   | apiVersion    | 'pat'           | '0'             | collectionName        | 'test'     | ''                          | ''                 | ''                     | 'wrong'               | '0'        | ''       | url       | proxy      | proxyUsername          | proxyPassword
        TC.C369568   | apiVersion    | 'basic'         | '1'             | collectionName        | 'test'     | userName                    | 'wrong'            | ''                     | ''                    | '0'        | ''       | 'wrong'   | proxy      | proxyUsername          | proxyPassword
        TC.C369575   | apiVersion    | 'basic'         | '0'             | collectionName        | 'test'     | userName                    | token              | ''                     | ''                    | '0'        | ''       | url       | 'wrong'    | '123'                  | '123'
    }

    @Unroll
    @Requires ({(PluginTestHelper.automationTestsContextRun =~ "NewFeature" && PluginTestHelper.pluginVersion == "1.0.0") || (PluginTestHelper.automationTestsContextRun =~ "Regression")})
    def 'Delete #caseId.ids #caseId.description'() {
        given:
        def credential = [
                credentialName: 'credential',
                username: credentialLogin,
                password: credentialPassword
        ]
        def proxy_credential = [
                credentialName: 'proxy_credential',
                username: proxy_credential_login,
                password: proxy_credential_pass
        ]
        def token_credential = [
                credentialName: 'token_credential',
                username: token_credential_login,
                password: token_credential_pass
        ]
        def procedureParams = [
                apiVersion         : apiTFSVersion,
                auth               : auth,
                checkConnection    : checkConnection,
                collection         : collection,
                debugLevel         : debugLevel,
                desc               : desc,
                endpoint           : endpoint,
                http_proxy         : http_proxy,
                credential         : 'credential',
                proxy_credential   : 'proxy_credential',
                token_credential   : 'token_credential'
        ]
        def deleteConfigParameters = [
                config: configName,
        ]
        when:
        if (caseId == TC.C369576) {
            createPluginConfigurationWithProxy(configName, procedureParams, credential, token_credential, proxy_credential)
        }
        then:
        def result = runProcedure(projectName, "DeleteConfiguration", deleteConfigParameters)
        assert result.outcome == outcome
        where:
        caseId       | apiTFSVersion | auth            | checkConnection | collection            | configName | credentialLogin             | credentialPassword | token_credential_login | token_credential_pass | debugLevel | desc     | endpoint  | http_proxy | proxy_credential_login | proxy_credential_pass | outcome
        TC.C369576   | apiVersion    | 'basic'         | '0'             | collectionName        | 'test'     | userName                    | token              | ''                     | ''                    | '0'        | ''       | url       | proxy      | proxyUsername          | proxyPassword         | 'success'
        TC.C369577   | apiVersion    | 'basic'         | '0'             | collectionName        | 'test'     | userName                    | token              | ''                     | ''                    | '0'        | ''       | url       | proxy      | proxyUsername          | proxyPassword         | 'error'
    }

}