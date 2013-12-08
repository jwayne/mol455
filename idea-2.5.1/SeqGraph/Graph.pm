package SeqGraph::Graph;

=head1 NAME

Graph.pm

=head1 SYNOPSIS

Module for generating a figure of a DNA or AA sequence that has one or 
more graphs associated with it.

=head1 DESCRIPTION

Module for generating a figure of a DNA or AA sequence that has one or 
more graphs associated with it.

=cut

use strict;

# ------------------------------------------------------------------
# Globals 
# ------------------------------------------------------------------

my $DEFAULTS = {
    # default SVG font width; pretty much everything else is calculated relative to this
    'font_h' => 16,
    # font width expressed as a fraction of height
    'font_w_fhm' => 0.85,
    # horizontal padding to the left and right of each base/AA, in pixels
    'h_pad' => 1,
};

# ------------------------------------------------------------------
# Constructor
# ------------------------------------------------------------------

=head2 B<new>

 Usage   : my $graph = SeqGraph::Graph->new({ 'seqlen' => 6, 'tracks' => [$barGraphTrack, $seqTrack] });
 Function: Create a SeqGraph::Graph.
 Returns : A new instance of SeqGraph::Graph.
 Args    : A hashref with the following fields:
            seqlen       - length of the literal DNA or AA sequence
            tracks       - a listref of SeqGraph::Track::Track objects that describes what to draw
            font_h       - font height in pixels
            font_w_fhm   - font width as a multiple of font height
            h_pad        - horizontal padding to the left and right of each base/AA, in pixels

=cut

sub new {
    my($invocant, $args) = @_;

    # TODO - convert these classes to use standard Sybil argument and default-handling routines?

    my $class = ref($invocant) || $invocant;
    my $self = {};
    bless($self, $class);

    # copy $args (all of it) -> $self
    foreach my $key (keys %$args) {
	$self->{$key} = $args->{$key};
    }

    # set defaults
    foreach my $key (keys %$DEFAULTS) {
	$self->{$key} = $DEFAULTS->{$key} if (!defined($self->{$key}));
    }

    # call setParent() on each track, figure out left and right margins
    my $l_margin = 0;
    my $r_margin = 0;

    my $tracks = $self->{'tracks'};
    foreach my $track (@$tracks) {
	$track->setParent($self);
	my $lm = $track->getLeftMargin();
	my $rm = $track->getRightMargin();
	$l_margin = $lm if ($lm > $l_margin);
	$r_margin = $rm if ($rm > $r_margin);
    }

    $self->{'_l_margin'} = $l_margin;
    $self->{'_r_margin'} = $r_margin;

    return $self;
}

# ------------------------------------------------------------------
# Public methods
# ------------------------------------------------------------------

sub getSeqLen {
    my($self) = @_;
    return $self->{'seqlen'};
}

sub getFontH {
    my($self) = @_;
    return $self->{'font_h'};
}

sub getFontW {
    my($self) = @_;
    my $font_h = $self->{'font_h'};
    return int($font_h * $self->{'font_w_fhm'});
}

sub getHPad {
    my($self) = @_;
    return $self->{'h_pad'};
}

sub getCharCoords {
    my($self) = @_;

    if (!defined($self->{'_char_coords'})) {
	my $cc = $self->{'_char_coords'} = [];

	my $font_w = $self->getFontW();
	my $h_pad = $self->getHPad();
	my $total_font_w = $font_w + (2 * $h_pad);
	my $l_margin = $self->{'_l_margin'};

	my $x_posn = $l_margin;
	my $sl = $self->getSeqLen();

	for (my $s = 0;$s < $sl;++$s) {
	    my $x2 = $x_posn + $h_pad;
	    my $x3 = $x2 + $font_w;
	    my $x4 = $x3 + $h_pad;
	    push(@$cc, [$x_posn, $x2, $x3, $x4]);
	    $x_posn = $x4;
	}
    }

    return $self->{'_char_coords'};
}

=head2 B<svg>

 Usage   : my $svg = $graph->svg();
 Function: Generate an SVG-format figure for the graph.
 Returns : An XML <svg> element that displays the graph.
 Args    : None.

=cut

sub svg {
    my($self) = @_;

    my $seqlen = $self->{'seqlen'};
    my $tracks = $self->{'tracks'};

    # figure out left and right margin, image size
    my $l_margin = 0;
    my $r_margin = 0;
    my $svg_height = 0;
    my $track_heights = [];

    foreach my $track (@$tracks) {
	my $lm = $track->getLeftMargin();
	$l_margin = $lm if ($lm > $l_margin);
	my $th = $track->getHeight();
	push(@$track_heights, $th);
	$svg_height += $th;
    }

    my $font_w = $self->getFontW();
    my $h_pad = $self->getHPad();
    my $svg_width = $l_margin + $r_margin + ($seqlen * ($font_w + ($h_pad * 2)));

    my $svg = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    $svg .= "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.0//EN\" \"http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd'\">\n";
    $svg .= " <svg height=\"$svg_height\" width=\"$svg_width\" ";
    $svg .= "xml:xlink=\"http://www.w3.org/1999/xlink\" xmlns=\"http://www.w3.org/2000/svg\">\n";

    # DEBUG - draw bounding box
#    $svg .= "<rect stroke=\"rgb(0,0,255)\" fill=\"none\" stroke-width=\"2\" x=\"0\" y=\"0\" ";
#    $svg .= "width=\"$svg_width\" height=\"$svg_height\"/>";

    my $y_posn = 0;
    my $nt = scalar(@$tracks);
    
    for (my $t = 0;$t < $nt;++$t) {
	my $th = $track_heights->[$t];
	my $track = $tracks->[$t];
	my $ts = $track->svg();

	$svg .= "<g transform=\"translate(0,$y_posn)\">\n";
	$svg .= $ts;

	# DEBUG - draw track bounding box
#	$svg .= "<rect stroke=\"rgb(0,0,0)\" fill=\"none\" stroke-width=\"1\" x=\"0\" y=\"0\" ";
#	$svg .= "width=\"$svg_width\" height=\"$th\"/>";

	$svg .= "\n</g>\n";
	$y_posn += $th;
    }

    $svg .= "</svg>";
    return $svg;
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
