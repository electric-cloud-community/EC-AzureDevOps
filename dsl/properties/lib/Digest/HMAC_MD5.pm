package Digest::HMAC_MD5;
our $VERSION="1.01";

use strict;
use Digest::MD5  qw(md5);
Digest::HMAC->import('hmac');

# OO interface
use vars qw(@ISA);
@ISA=qw(Digest::HMAC);
sub new
{
    my $class = shift;
    $class->SUPER::new($_[0], "Digest::MD5", 64);
}

# Functional interface
require Exporter;
*import = \&Exporter::import;
use vars qw(@EXPORT_OK);
@EXPORT_OK=qw(hmac_md5 hmac_md5_hex);

sub hmac_md5
{
    hmac($_[0], $_[1], \&md5, 64);
}

sub hmac_md5_hex
{
    unpack("H*", &hmac_md5)
}

1;
package Digest::HMAC;
our $VERSION = "1.03";

use strict;

# OO interface

sub new
{
    my($class, $key, $hasher, $block_size) =  @_;
    $block_size ||= 64;
    $key = $hasher->new->add($key)->digest if length($key) > $block_size;

    my $self = bless {}, $class;
    $self->{k_ipad} = $key ^ (chr(0x36) x $block_size);
    $self->{k_opad} = $key ^ (chr(0x5c) x $block_size);
    $self->{hasher} = $hasher->new->add($self->{k_ipad});
    $self;
}

sub reset
{
    my $self = shift;
    $self->{hasher}->reset->add($self->{k_ipad});
    $self;
}

sub add     { my $self = shift; $self->{hasher}->add(@_);     $self; }
sub addfile { my $self = shift; $self->{hasher}->addfile(@_); $self; }

sub _digest
{
    my $self = shift;
    my $inner_digest = $self->{hasher}->digest;
    $self->{hasher}->reset->add($self->{k_opad}, $inner_digest);
}

sub digest    { shift->_digest->digest;    }
sub hexdigest { shift->_digest->hexdigest; }
sub b64digest { shift->_digest->b64digest; }


# Functional interface

require Exporter;
*import = \&Exporter::import;
use vars qw(@EXPORT_OK);
@EXPORT_OK = qw(hmac hmac_hex);

sub hmac
{
    my($data, $key, $hash_func, $block_size) = @_;
    $block_size ||= 64;
    $key = &$hash_func($key) if length($key) > $block_size;

    my $k_ipad = $key ^ (chr(0x36) x $block_size);
    my $k_opad = $key ^ (chr(0x5c) x $block_size);

    &$hash_func($k_opad, &$hash_func($k_ipad, $data));
}

sub hmac_hex { unpack("H*", &hmac); }

1;
