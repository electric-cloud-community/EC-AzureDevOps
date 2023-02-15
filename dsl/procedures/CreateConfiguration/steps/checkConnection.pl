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

#########################
## checkConnection.pl
#########################
$[/myProject/scripts/preamble]

use ElectricCommander;
use ElectricCommander::PropDB;

use EC::AzureDevOps::Plugin;

my $ec = ElectricCommander->new();
$ec->abortOnError(0);

my $endpoint = get_step_property_value('endpoint');
my $collection = get_step_property_value('collection');
my $api_version = get_step_property_value('apiVersion');
my $proxy = get_step_property_value('http_proxy');
my $auth = get_step_property_value('auth');

my $xpath = $ec->getFullCredential('credential');
my $client_id = $xpath->findvalue("//userName") || '';
my $client_secret = $xpath->findvalue("//password") || '';

my $token_xpath = ($auth eq 'pat' ? $ec->getFullCredential('token_credential') : '');
my $token_secret = ($token_xpath ? $token_xpath->findvalue("//password") : '');

my $proxy_xpath = ( $proxy ) ? $ec->getFullCredential('proxy_credential') : '';
my $proxy_user = ( $proxy_xpath ) ? $proxy_xpath->findvalue("//userName") : '';
my $proxy_password = ( $proxy_xpath ) ? $proxy_xpath->findvalue("//password") : '';

# Set a placeholder for the Username if
if ($auth eq 'pat'){
    if (!$token_secret){
        exit_with_error("You should provide the 'Access Token' for 'Personal Access Token' authentication.")
    }

    # This is placeholder, value does not matter as long as it is not empty
    $client_id = 'pat';
    $client_secret = $token_secret;

    # Changing the auth type for Microrest to use Basic auth
    $auth = 'basic';
}

if (!$client_id && !$client_secret){
    exit_with_error("Both username and password are required for the NTLM or Basic authentication.");
}

my $plugin = EC::AzureDevOps::Plugin->new(
    # Enable the full debug
    debug_level => 3
);

my %config = (
    endpoint          => $endpoint,
    collection        => $collection,
    userName          => $client_id,
    password          => $client_secret,
    debugLevel        => 3,

    # Auth type params
    auth              => $auth,
    apiVersion        => $api_version,

    # Proxy params
    http_proxy        => $proxy,
    proxy_username    => $proxy_user,
    proxy_password    => $proxy_password,
);
$plugin->{_config} = \%config;

my $errors = $plugin->check_connection(\%config);
if ($errors) {
    print $errors;
    exit_with_error("Failed to check connection with given parameters. See logs for more info.\n");
}

exit 0;

sub get_step_property_value {
    my $param_name = shift;
    return $ec->getProperty($param_name)->findvalue('//value')->string_value;
}

sub exit_with_error {
    my ( $error_message ) = @_;
    $ec->setProperty('/myJob/configError', $error_message);
    $ec->setProperty('/myJobStep/summary', $error_message);
    print $error_message;
    exit 1;
}