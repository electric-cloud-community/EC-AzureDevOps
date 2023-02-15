#########################
## createcfg.pl
#########################

use ElectricCommander;
use ElectricCommander::PropDB;

use constant {
    SUCCESS => 0,
    ERROR   => 1,
};

my $opts;

my $PLUGIN_NAME = '@PLUGIN_KEY@';

if (! defined $PLUGIN_NAME) {
    exit_with_error("PLUGIN_NAME must be defined\n");
}

my $ec = ElectricCommander->new();
$ec->abortOnError(0);

# load option list from procedure parameters
my $x = $ec->getJobDetails($ENV{COMMANDER_JOBID});
my $nodeset = $x->find("//actualParameter");
foreach my $node ($nodeset->get_nodelist) {
    my $parm = $node->findvalue("actualParameterName");
    my $val = $node->findvalue("value");
    $opts->{$parm} = "$val";
}

if (! defined $opts->{config} || "$opts->{config}" eq "") {
    exit_with_error("You have to supply non-empty configuration name\n");
}

# check to see if a config with this name already exists before we do anything else
my $xpath = $ec->getProperty("/myProject/ec_plugin_cfgs/$opts->{config}");
my $property = $xpath->findvalue("//response/property/propertyName");

if (defined $property && "$property" ne "") {
    exit_with_error("A configuration named '$opts->{config}' already exists.");
}

my $cfg = ElectricCommander::PropDB->new($ec, "/myProject/ec_plugin_cfgs");

my @required_fields = qw/endpoint collection auth apiVersion/;
# add all the options as properties
foreach my $key (keys % {$opts}) {
    if ($key eq "config") {
        next;
    }
    if (! $opts->{$key} && grep {$key eq $_} @required_fields) {
        exit_with_error("The parameter '$key' is required.")
    }

    $cfg->setCol($opts->{config}, $key, $opts->{$key});
}

print "Successfully created configuration '$opts->{config}'.\n";

exit 0;

sub exit_with_error {
    my ( $error_message ) = @_;
    $ec->setProperty('/myJob/configError', $error_message);
    $ec->setProperty('/myJobStep/summary', $error_message);
    print $error_message;
    exit 1;
}