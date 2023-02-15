package dsl

def projName = args.projectName
def procName = args.procedureName
def filePath = args.filePath
def fileSize = args.fileSize

project projName, {
    procedure procName, {
    
    
        formalParameter 'filePath', defaultValue: filePath, {
            type = 'textarea'
        }
        formalParameter 'fileSize', defaultValue: fileSize, {
            type = 'textarea'
        }
        step procName, {
            description = ''
            shell = 'ec-groovy'
            timeLimitUnits = 'minutes'
            command = '''
                RandomAccessFile f = new RandomAccessFile(\'$[filePath]\', "rw")
                f.setLength($[fileSize]);
            '''
        }
    }
}
