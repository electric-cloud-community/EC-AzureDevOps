procedure 'UploadWorkItemAttachment', description: 'Use this procedure to upload an attachment to a Work item.', {
    step 'upload a work item attachment',
        command: """
\$[/myProject/scripts/preamble]
use EC::AzureDevOps::Plugin;
EC::AzureDevOps::Plugin->new->step_upload_work_item_attachment();
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'
}
