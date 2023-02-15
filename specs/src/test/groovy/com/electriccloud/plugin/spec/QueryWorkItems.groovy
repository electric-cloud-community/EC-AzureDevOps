package com.electriccloud.plugin.spec

import com.electriccloud.plugin.spec.tfs.TFSHelper
import spock.lang.*

@Stepwise
@IgnoreIf({System.getenv("DEV_TESTS") != 'true'})
class QueryWorkItems extends PluginTestHelper {

    static String procedureName = "QueryWorkItems"
    static String projectName = "Spec Tests $procedureName"
    static String configName = "config_${procedureName}"

    @Shared
    TFSHelper tfsClient

    /// Procedure parameters
    // Mandatory
    @Shared
    def resultPropertySheet = '/myJob/workItems'
    @Shared
    def resultFormat = 'propertySheet'

    // Optional
    @Shared
    def project

    @Shared
    def asOf = ''

    @Shared
    def queryId

    @Shared
    def queryText

    @Shared
    def timePrecision

    /// Specs parameters
    @Shared
    def caseId,
        expectedSummary,
        expectedOutcome

    @Shared
    def queries = [
        flat         : [
            name : randomize("flat"),
            query: "Select [System.Id], [System.Title], [System.State] From WorkItems Where [System.WorkItemType] = 'Feature' and [System.TeamProject] = @project",
            ref  : null
        ],
        oneHop       : [
            name : randomize("oneHop"),
            query: "Select [System.Id], [System.Title], [System.State] From WorkItems Where [System.WorkItemType] = 'Feature' and [System.TeamProject] = @project",
            ref  : null
        ],
        tree         : [
            name : randomize("tree"),
            query: "Select [System.Id], [System.Title], [System.State] From WorkItems Where [System.WorkItemType] = 'Feature' and [System.TeamProject] = @project",
            ref  : null
        ],
        noTitle : [
            name : randomize("noTitle"),
            query: "Select [System.Id], [System.State] From WorkItems Where [System.WorkItemType] = 'Feature' and [System.TeamProject] = @project",
            type : 'flat',
            ref  : null
        ],
        withAsterisk : [
            name : randomize("withAsterisk"),
            query: "Select * From WorkItems Where [System.WorkItemType] = 'Feature' and [System.TeamProject] = @project",
            type : 'flat',
            ref  : null,
            doNotCreate: true
        ],
        empty        : [
            name : randomize("empty"),
            query: "Select [System.Id], [System.Title], [System.State] From WorkItems Where [System.Title] = '${randomize('unexisting')}'",
            type : 'flat',
            ref  : null
        ],
        invalid        : [
            name : randomize("invalid"),
            query: "Give me an error",
            type : 'flat',
            ref  : null,
            doNotCreate: true
        ]
    ]

    def doSetupSpec() {
        createConfiguration(configName)
        dslFile "dsl/$procedureName/procedure.dsl", [projectName: projectName]

        tfsClient = getClient()
        assert tfsClient

        // Create an instance of every query
        queries.each { String type, Map parameters ->
            if (parameters != null && !parameters['doNotCreate']) {
                // QueryType can be different from the name
                String queryType = type
                if (queries[type]['type']){
                    queryType = queries[type]['type']
                }

                def queryJSON = tfsClient.createWorkItemQuery(
                    (String) queries[type]['name'],
                    (String) queries[type]['query'],
                    [queryType: queryType]
                )

                queries[type]['ref'] = queryJSON
            }
        }
    }

    def doCleanupSpec() {
        // Clean the queries
        queries.each { String type, Map parameters ->
            if (parameters != null && parameters['ref'] != null)
                tfsClient.deleteWorkItemQuery(parameters['ref']['id'])
        }

        deleteConfiguration('EC-AzureDevOps', configName)
        conditionallyDeleteProject(projectName)
    }

    @Unroll
    def '#caseId. Smoke. Query by ID'() {
        given:
        def resultFormat = 'propertySheet'
        def resultSheet = '/myJob/queryWorkItems'

        assert queries[queryType] && queries[queryType]['ref']

        queryId = queries[queryType]['ref']['id']
        project = getADOSProjectName()

        Map procedureParams = [
            config             : configName,
            project            : project,
            queryId            : queryId,
            queryText          : '',
            timePrecision      : '',
            resultPropertySheet: resultSheet,
            resultFormat       : resultFormat,
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)

        then:
        println getJobLink(result.jobId)
        println(result.logs)

        assert result.outcome == 'success'

        where:
        caseId       | queryType
        'CHANGEME_1' | 'flat'
        'CHANGEME_2' | 'oneHop'
        'CHANGEME_3' | 'tree'
        'CHANGEME_4' | 'noTitle'
    }

    @Unroll
    def '#caseId. Smoke. Query by WIQL'() {
        given:
        def resultFormat = 'propertySheet'
        def resultSheet = '/myJob/queryWorkItems'

        assert queries[queryType] && queries[queryType]['query']

        queryText = queries[queryType]['query']
        project = getADOSProjectName()

        Map procedureParams = [
            config             : configName,
            project            : project,
            queryId            : '',
            queryText          : queryText,
            timePrecision      : '',
            resultPropertySheet: resultSheet,
            resultFormat       : resultFormat,
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)

        then:
        println "[JOB LINK] " + getJobLink(result.jobId)
        println(result.logs)

        assert result.outcome == 'success'

        where:
        caseId       | queryType
        'CHANGEME_5' | 'flat'
        'CHANGEME_6' | 'oneHop'
        'CHANGEME_7' | 'tree'
        'CHANGEME_8' | 'noTitle'
        'CHANGEME_9' | 'withAsterisk'
    }

    @Unroll
    def '#caseId. Smoke. Warning for empty query result'() {
        given:
        def resultFormat = 'propertySheet'
        def resultSheet = '/myJob/queryWorkItems'

        assert queries[queryType] && queries[queryType]['query']

        queryText = queries[queryType]['query']
        project = getADOSProjectName()

        Map procedureParams = [
            config             : configName,
            project            : project,
            queryId            : '',
            queryText          : queryText,
            timePrecision      : '',
            resultPropertySheet: resultSheet,
            resultFormat       : resultFormat,
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)

        then:
        println getJobLink(result.jobId)
        println(result.logs)

        assert result.outcome == 'warning'

        where:
        caseId       | queryType
        'CHANGEME_10' | 'empty'
    }

    @Unroll
    def '#caseId. Smoke. Error for unexisting query ID'() {
        given:
        def resultFormat = 'propertySheet'
        def resultSheet = '/myJob/queryWorkItems'

        queryId = randomize("nonexisting")
        project = getADOSProjectName()

        Map procedureParams = [
            config             : configName,
            project            : project,
            queryId            : queryId,
            queryText          : '',
            timePrecision      : '',
            resultPropertySheet: resultSheet,
            resultFormat       : resultFormat,
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)

        then:
        println getJobLink(result.jobId)
        println(result.logs)

        assert result.outcome == 'error'

        where:
        caseId << ['CHANGEME_11']
    }
}