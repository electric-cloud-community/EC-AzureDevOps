package com.electriccloud.plugin.spec

import spock.lang.*

import static org.junit.Assume.assumeFalse

class CreateConfiguration extends PluginTestHelper {


    @Shared
    def endpoint

    @Shared
    def collection

    @Shared
    def apiVersion

    @Shared
    def auth

    @Shared
    def debugLevel

    @Shared
    def http_proxy

    @Shared
    def checkConnection

    @Shared
    def expectedOutcome,
        caseId

    static def urls = [
        valid  : getADOSURL(),
        empty  : '',
        invalid: randomize('hhh')
    ]


    @Unroll
    def "#caseId. Create with valid username and password"() {
        given:
        String configName = randomize("specConfig")
        String auth = 'basic'

        String username = (authType == 'basic' ? getADOSDomainName() + '\\\\' : '') + getADOSUsername()
        String password =  authType == 'pat' ? getADOSToken() : getADOSPassword()

        def collectionName = getADOSCollectionName()
        def logLevel = 2

        when:
        def result = createPluginConfiguration(
            configName,
            [
                desc           : 'Spec Tests Config',
                endpoint       : endpoint,
                collection     : collectionName,
                apiVersion     : apiVersion,
                auth           : auth,
                debugLevel     : logLevel,
                http_proxy     : '',
                checkConnection: checkConnection
            ],
            [
                username: username,
                password: password
            ]
        )

        then:
        println getJobLink(result.jobId)
        assert result

        assert result.outcome == expectedOutcome

        cleanup:
        deleteConfiguration(PLUGIN_NAME, configName)

        where:
        caseId     | endpoint     | apiVersion          | checkConnection | expectedOutcome
        'CHNGME_1' | urls.valid   | getADOSApiVersion() | 1               | 'success'
        'CHNGME_2' | urls.valid   | '1.0'               | 1               | 'success'
        'CHNGME_3' | urls.empty   | getADOSApiVersion() | 0               | 'error'
        'CHNGME_4' | urls.empty   | getADOSApiVersion() | 1               | 'error'
        'CHNGME_5' | urls.invalid | getADOSApiVersion() | 1               | 'error'

    }

    @Unroll
    def "#caseId. Create with wrong username and password"() {
        given:
        String configName = randomize("specConfig")
        String auth = 'basic'

        endpoint = urls.valid
        apiVersion = getADOSApiVersion()

        def collectionName = getADOSCollectionName()
        def logLevel = 2

        when:
        def result = createPluginConfiguration(
            configName,
            [
                desc           : 'Spec Tests Config',
                endpoint       : endpoint,
                collection     : collectionName,
                apiVersion     : apiVersion,
                auth           : auth,
                debugLevel     : logLevel,
                http_proxy     : '',
                checkConnection: checkConnection
            ],
            [
                username: username,
                password: password
            ]
        )

        then:
        println getJobLink(result.jobId)
        assert result

        assert result.outcome == expectedOutcome

        cleanup:
        deleteConfiguration(PLUGIN_NAME, configName)

        where:
        caseId      | username          | password          | checkConnection | expectedOutcome
        // https://github.com/Microsoft/tfs-cli/blob/master/docs/configureBasicAuth.md
        // 'CHNGME_6'  | getADOSUsername() | getADOSPassword() | 1               | 'success'
        'CHNGME_7'  | ''                | ''                | 1               | 'error'
        'CHNGME_8'  | getADOSUsername() | 'invalidPassw0rd' | 1               | 'error'
        'CHNGME_9'  | getADOSUsername() | ''                | 1               | 'error'
        'CHNGME_10' | ''                | getADOSPassword() | 1               | 'error'
        'CHNGME_11' | getADOSUsername() | ''                | 0               | 'error'
        'CHNGME_12' | ''                | getADOSPassword() | 0               | 'error'

    }

    @Unroll
    def "#caseId. Create with PAT"() {
        given:
        assumeFalse(expectedOutcome == 'success' && authType != 'pat')

        String configName = randomize("specConfig")
        String auth = 'pat'

        String token = getADOSToken()

        def collectionName = getADOSCollectionName()
        def logLevel = 2

        when:
        def result = createPluginConfigurationWithPat(
            configName,
            [
                desc           : 'Spec Tests Config',
                endpoint       : endpoint,
                collection     : collectionName,
                apiVersion     : apiVersion,
                auth           : auth,
                debugLevel     : logLevel,
                http_proxy     : '',
                checkConnection: checkConnection
            ],
            [
                password: token
            ]
        )

        then:
        println getJobLink(result.jobId)
        assert result

        assert result.outcome == expectedOutcome

        cleanup:
        deleteConfiguration(PLUGIN_NAME, configName)

        where:
        caseId      | endpoint     | apiVersion          | checkConnection | expectedOutcome
        'CHNGME_13'  | urls.valid   | getADOSApiVersion() | 1               | 'success'
        'CHNGME_14'  | urls.valid   | '1.0'               | 1               | 'success'
        'CHNGME_15'  | urls.empty   | getADOSApiVersion() | 0               | 'error'
        'CHNGME_16'  | urls.empty   | getADOSApiVersion() | 1               | 'error'
        'CHNGME_17' | urls.invalid | getADOSApiVersion() | 1               | 'error'

    }

    @Unroll
    def "#caseId. Delete config that exists: #false"() {
        given:
        if (createConfig) {
            createConfiguration(configName)
        }

        when:
        def result = deleteConfiguration(configName)

        then:
        println getJobLink(result.jobId)
        assert result.outcome == expectedOutcome

        where:
        caseId      | configName               | createConfig | expectedOutcome
        'CHNGME_18' | randomize("spec config") | true         | 'success'
        'CHNGME_19' | randomize("spec config") | false        | 'error'
    }


    Object createPluginConfiguration(String configName, Map params, Map credential) {
        assert configName

        def result = runProcedureDsl("""
            runProcedure(
                projectName: '/plugins/EC-AzureDevOps/project',
                procedureName: 'CreateConfiguration',
                credential: [
                    [
                        credentialName: 'proxy_credential',
                        userName: '',
                        password: ''
                    ],
                    [
                        credentialName: 'credential',
                        userName: '${credential.username}',
                        password: '${credential.password}'
                    ],
                ],
                actualParameter: [
                    config           : '${configName}', 
                    desc             : '${params.desc}',
                    endpoint         : '${params.endpoint}',
                    collection       : '${params.collection}',
                    apiVersion       : '${params.apiVersion}',
                    auth             : '${params.auth}',
                    debugLevel       : '${params.debugLevel}',
                    http_proxy       : '',
                    checkConnection  : '${params.checkConnection}',
                    credential       : 'credential',
                    proxy_credential : 'proxy_credential'
                ]
            )
            """)

        assert result?.jobId
        waitUntil {
            jobCompleted(result)
        }

        return result
    }

    Object createPluginConfigurationWithPat(String configName, Map params, Map credential) {
        assert configName

        def result = runProcedureDsl("""
            runProcedure(
                projectName: '/plugins/EC-AzureDevOps/project',
                procedureName: 'CreateConfiguration',
                credential: [
                    [
                        credentialName: 'proxy_credential',
                        userName: '',
                        password: ''
                    ],
                    [
                        credentialName: 'credential',
                        userName: '',
                        password: ''
                    ],
                    [
                        credentialName: 'token_credential',
                        password: '${credential.password}'
                    ],
                ],
                actualParameter: [
                    config           : '${configName}', 
                    desc             : '${params.desc}',
                    endpoint         : '${params.endpoint}',
                    collection       : '${params.collection}',
                    apiVersion       : '${params.apiVersion}',
                    auth             : '${params.auth}',
                    debugLevel       : '${params.debugLevel}',
                    http_proxy       : '',
                    checkConnection  : '${params.checkConnection}',
                    credential       : 'credential',
                    token_credential : 'token_credential',
                    proxy_credential : 'proxy_credential'
                ]
            )
            """)

        assert result?.jobId
        waitUntil {
            jobCompleted(result)
        }

        return result
    }

    Object deleteConfiguration(String configName) {
        assert configName

        def result = runProcedureDsl("""
            runProcedure(
                projectName: '/plugins/EC-AzureDevOps/project',
                procedureName: 'DeleteConfiguration',
                actualParameter: [
                    config          : '${configName}' 
                ]
            )
        """)

        assert result?.jobId
        waitUntil {
            jobCompleted(result)
        }

        return result

    }
}