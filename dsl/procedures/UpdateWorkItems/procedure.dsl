

def procName = 'UpdateWorkItems'
def stepName = 'update work items'
procedure procName, description: 'Updates field values for one or more Work Items based on specified ids. ', {

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::AzureDevOps::Plugin;
EC::AzureDevOps::Plugin->new->step_update_work_items();
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
