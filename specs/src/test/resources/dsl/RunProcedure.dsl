package dsl

def projName = args.projectName
def procName = args.procedureName
def params = args.params

project projName, {
    procedure procName, {

        step procName, {
            description = ''
            subprocedure = procName
            subproject = '/plugins/EC-AzureDevOps/project'

            params.each { name, defValue ->
                actualParameter name, '$[' + name + ']'
            }
        }

        params.each {name, defValue ->
        if (name != 'credential' && name != 'dataSourceConnectionCredentials') {
          formalParameter name, defaultValue: defValue, {
            type = 'textarea'
          }
        }
        else {
          hasCredentials = true
          formalParameter name, defaultValue: defValue, {
            type = 'credential'
             }
            }
        }
    }
}
