import java.io.File

def procName = 'DeleteConfiguration'
procedure procName,
        description: 'Deletes an existing plugin configuration.', {

    step 'deleteConfiguration',
            command: new File(pluginDir, "dsl/procedures/$procName/steps/deleteConfiguration.pl").text,
            errorHandling: 'failProcedure',
            exclusiveMode: 'none',
            releaseMode: 'none',
            shell: 'ec-perl',
            timeLimit: '5',
            timeLimitUnits: 'minutes'

}
