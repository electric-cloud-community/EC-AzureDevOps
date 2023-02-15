REM generate help
java -jar ..\PluginWizardHelp\build\libs\plugin-wizard-help-1.17-SNAPSHOT.jar -rd "May 07, 2019" --out pages\help.xml --pluginFolder .

ectool --timeout 10000 login admin changeme

REM generate plugin
CMD /C ..\plugin-tool\pluginbuilder build .

echo "Installing"
REM install and promote
ectool installPlugin build\EC-AzureDevOps.zip
ectool promotePlugin EC-AzureDevOps-1.0.1.0
