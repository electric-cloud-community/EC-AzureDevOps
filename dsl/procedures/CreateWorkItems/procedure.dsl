

def procName = 'CreateWorkItems'
def stepName = 'create work items'
procedure procName, description: 'This procedure creates one or multiple new work items. In order to facilitate creation of multiple Work Items the parameters Work Items JSON is provided to specify Multiple Work items.', {

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::AzureDevOps::Plugin;
EC::AzureDevOps::Plugin->new->step_create_work_items();
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
