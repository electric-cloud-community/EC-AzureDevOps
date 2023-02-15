#
#  Copyright 2019 Electric Cloud, Inc.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

##########################
# createAndAttachCredential.pl
##########################
use strict;
use warnings;

use ElectricCommander;
use JSON::XS qw/decode_json encode_json/;
use Data::Dumper;

## get an EC object
my ElectricCommander $ec = ElectricCommander->new();
$ec->abortOnError(0);

my $config = '$[/myJob/config]';
my $auth_type = '$[/myJob/auth]';

my $credential = $config;
my $proxyCredential = $config . '_proxy_credential';
my $tokenCredential = $config . '_token_credential';

my $projName = '$[/myProject/projectName]';
my $user = '$[/myJob/launchedByUser]';
my $configPath = "/projects/$projName/ec_plugin_cfgs/$config";

my @credentials = (
    {name => $credential, parameter => 'credential'},
    {name => $proxyCredential, parameter => 'proxy_credential'},
    {name => $tokenCredential, parameter => 'token_credential'}
);

if (! defined $config || $config eq '') {
    exit_with_error("You have to supply non-empty configuration name\n");
}

# check to see if a config with this name already exists before we do anything else
my $config_xpath = $ec->getProperty($configPath);
my $property = $config_xpath->findvalue('//response/property/propertyName');
if (! defined $property || $property eq '') {
    exit_with_error("Error: A configuration named '$config' does not exist.");
}

sub try_to_attach_the_credential {
    my ( $credName, $credValue ) = @_;

    my $credential_xpath;
    eval {
        $credential_xpath = $ec->getFullCredential($credValue);
        1;
    } or do {
        print "Failed to get credential $credName, next.\n";
        return;
    };
    $ec->abortOnError(0);

    my $userName = $credential_xpath->findvalue("//userName");
    my $password = $credential_xpath->findvalue("//password");

    if ($credName eq $tokenCredential && ($auth_type eq 'pat') && !$password){
        exit_with_error("Token value is required for the PAT authentication.\n");
    }
    elsif ($credName eq $credential && ($auth_type =~ /basic|ntlm/) && !($userName && $password)){
        exit_with_error("Both username and password are required.\n");
    }

    my $errors = $ec->checkAllErrors($credential_xpath);

    if ($errors){
        # This will allow to skip missing credentials
        print "Skipping the credential '$credName'. $errors\n";
        return;
    }

    # Create credential
    $ec->deleteCredential($projName, $credName);
    $credential_xpath = $ec->createCredential($projName, $credName, $userName, $password);
    $errors .= $ec->checkAllErrors($credential_xpath);

    # Give config the credential's real name
    print "Creating credential $credName in project $projName with user '$userName'\n";
    $errors .= $ec->checkAllErrors($credential_xpath);
    $credential_xpath = $ec->setProperty($configPath . '/' . $credValue, $credName);
    $errors .= $ec->checkAllErrors($credential_xpath);

    # Give job launcher full permissions on the credential
    $credential_xpath = $ec->createAclEntry("user", $user, {
        projectName                => $projName,
        credentialName             => $credName,
        readPrivilege              => "allow",
        modifyPrivilege            => "allow",
        executePrivilege           => "allow",
        changePermissionsPrivilege => "allow"
    });
    $errors .= $ec->checkAllErrors($credential_xpath);

    # Attach credential to steps that will need it
    my $stepsJSON = $ec->getPropertyValue("/projects/$projName/procedures/CreateConfiguration/ec_stepsWithAttachedCredentials");
    if (defined $stepsJSON && $stepsJSON ne "") {
        #parse as json
        my $steps = decode_json($stepsJSON);
        foreach my $step (@$steps) {
            print "Attaching credential to procedure " . $step->{procedureName} . " at step " . $step->{stepName} . "\n";
            my $apath = $ec->attachCredential($projName, $credName, {
                procedureName => $step->{procedureName},
                stepName      => $step->{stepName}
            });
            $errors .= $ec->checkAllErrors($apath);
        }
    }

    if ($errors ne "") {
        exit_with_error("Error creating configuration credential: " . $errors);
    }
}

for my $cred (@credentials){
    $ec->abortOnError(0);
    print "CredName: $cred->{name}\n";
    try_to_attach_the_credential($cred->{name}, $cred->{parameter});
}

sub exit_with_error {
    my ( $error_message ) = @_;

    # Delete all the credentials and partially created configuration
    $ec->abortOnError(0);

    $ec->deleteProperty($configPath);
    for my $cred (@credentials){
        $ec->deleteCredential($projName, $cred->{name});
    }

    $ec->setProperty('/myJob/configError', $error_message);
    print $error_message;
    exit 1;
}

1;