

def procName = 'GetWorkItems'
def stepName = 'get work items'
procedure procName, description: 'Retrieves work items based on specified IDs. The fields per work item are returned based on a specified list. This procedure allows you to retrieve the specified fields per work item (or list of basic fields if no field is specified).', {

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::AzureDevOps::Plugin;
EC::AzureDevOps::Plugin->new->step_get_work_items();
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
