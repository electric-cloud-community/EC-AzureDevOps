package dsl

def projName = args.projectName
def procName = args.procedureName
def fileURL = args.fileURL
def filePath = args.filePath
def token = args.token

project projName, {
    procedure procName, {

        formalParameter 'fileURL', defaultValue: fileURL, {
            type = 'textarea'
        }
        formalParameter 'filePath', defaultValue: filePath, {
            type = 'textarea'
        }
        formalParameter 'token', defaultValue: token, {
            type = 'textarea'
        }
        step procName, {
            description = ''
            shell = 'ec-groovy'
            timeLimitUnits = 'minutes'
            command = '''
new File(\'$[filePath]\').withOutputStream { out ->
    def url = new URL(\'$[fileURL]\').openConnection()
    if (\'$[token]\') {
        def remoteAuth = "Basic " + ":$[token]".bytes.encodeBase64()
        url.setRequestProperty("Authorization", remoteAuth);
    }
    out << url.inputStream
}
'''
        }

    }
}
