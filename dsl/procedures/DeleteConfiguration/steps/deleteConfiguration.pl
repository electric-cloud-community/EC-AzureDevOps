use ElectricCommander;

# get an EC object
my $ec = ElectricCommander->new();
$ec->abortOnError(0);

my $config = '$[config]';

my $PLUGIN_NAME = '@PLUGIN_KEY@';
my $project_name = '/plugins/@PLUGIN_NAME@/project';

if (! defined $PLUGIN_NAME || $PLUGIN_NAME eq "\@PLUGIN_KEY\@") {
    exit_with_error("PLUGIN_NAME must be defined\n");
}

if (! defined $config || $config eq '') {
    exit_with_error("You have to supply non-empty configuration name\n");
}

# check to see if a config with this name already exists before we do anything else
my $xpath = $ec->getProperty("$project_name/ec_plugin_cfgs/$config");
my $property = $xpath->findvalue('//response/property/propertyName');

if (! defined $property || $property eq '') {
    exit_with_error("Error: A configuration named '$config' does not exist.");
}

# Delete configuration property sheet
$ec->deleteProperty("$project_name/ec_plugin_cfgs/$config");

# Delete credentials
my @credentials = (
    $config,
    $config . '_proxy_credential',
    $config . '_token_credential',
);

for my $credName (@credentials) {
    print "Removing credential: '$credName'.\n";
    $ec->deleteCredential($project_name, $credName);
}

print "Successfully removed configuration '$config'.\n";

exit 0;

sub exit_with_error {
    my ( $error_message ) = @_;
    $ec->setProperty('/myJob/configError', $error_message);
    print $error_message;
    exit 1;
}

1;