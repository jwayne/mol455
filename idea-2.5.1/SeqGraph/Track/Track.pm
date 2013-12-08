package SeqGraph::Track::Track;

=head1 NAME

Track.pm

=head1 SYNOPSIS

Superclass for the tracks used to determine what the SeqGraph displays.

=head1 DESCRIPTION

Superclass for the tracks used to determine what the SeqGraph displays.

=cut

use strict;

# ------------------------------------------------------------------
# Constructor
# ------------------------------------------------------------------

sub new {
    my($invocant) = @_;
    my $class = ref($invocant) || $invocant;
    my $self = {};
    bless($self, $class);
    return $self;
}

# ------------------------------------------------------------------
# Track - public methods
# ------------------------------------------------------------------

sub setParent {
    my($self, $parent) = @_;
    $self->{'_parent'} = $parent;
    
}

sub getParent {
    my($self) = @_;
    return $self->{'_parent'};
}

sub getLeftMargin  {
    return undef;
}

sub getRightMargin  {
    return undef;
}

sub getHeight {
    return undef;
}

sub svg {
    return undef;
}

# ------------------------------------------------------------------
# Track - private methods
# ------------------------------------------------------------------

sub _copyArgs {
    my($self, $args, $defaults) = @_;
        
    # copy $args (all of it) -> $self
    foreach my $key (keys %$args) {
	$self->{$key} = $args->{$key};
    }

    # set defaults
    foreach my $key (keys %$defaults) {
	$self->{$key} = $defaults->{$key} if (!defined($self->{$key}));
    }
}

1;

__END__

=head1 BUGS

Please e-mail any bug reports to crabtree@tigr.org.

=head1 SEE ALSO

=over 4

=item o
http://sybil.sourceforge.net

=back

=head1 AUTHOR(S)

 The Institute for Genomic Research
 9712 Medical Center Drive
 Rockville, MD 20850

=head1 COPYRIGHT

Copyright (c) 2005, The Institute for Genomic Research. 
All Rights Reserved.

=cut
