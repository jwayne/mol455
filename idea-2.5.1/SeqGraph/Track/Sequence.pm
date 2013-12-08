package SeqGraph::Track::Sequence;

=head1 NAME

Sequence.pm

=head1 SYNOPSIS

A Track that draws the literal sequence.

=head1 DESCRIPTION

A Track that draws the literal sequence.

=cut

use strict;

use SeqGraph::Track::Track;

our @ISA = ('SeqGraph::Track::Track');

# ------------------------------------------------------------------
# Globals 
# ------------------------------------------------------------------

my $DEFAULTS = {
    # vertical padding above and below each base/AA, as a multiple of font height
    'v_pad_fhm' => 0.2,
    'tick_h_fhm' => 0.2,
    'font_h_fhm' => 1,
    'bg_color_fn' => sub { return undef; },
    'stroke_color_fn' => sub { return 'black'; },
    'fill_color_fn' => sub { return 'black'; },
    'tick_color' => 'black',
    'text_style' => 'font-weight: bold; font-family: monospaced',
};

# ------------------------------------------------------------------
# Constructor
# ------------------------------------------------------------------

=head2 B<new>

 Usage   : my $seq_track = SeqGraph::Track::Sequence->new();
 Function: Create a new sequence track.
 Returns : A new instance of SeqGraph::Track::Sequence
 Args    : A hashref with the following fields:
            v_pad_fhm         - vertical padding above and below each base/AA, as a multiple of font height
            draw_ticks        - either 'top', 'bottom', or undef
            tick_h_fhm        - tick height as a multiple of font height
            font_h_fhm        - height of sequence font (default is to use parent font height)
            seq_fn            - function that maps seq index to the character(s) to display at that position
            bg_color_fn       - function that maps seq index to SVG background color (or undef for none)
            stroke_color_fn   - function that maps seq index to SVG stroke color (or undef for none)
            fill_color_fn     - function that maps seq index to SVG fill color (or undef for none)
            tick_color        - SVG stroke color for tick marks
            text_style        - SVG style for text/tspan element that contains the sequence

=cut

sub new {
    my($invocant, $args) = @_;
    my $self = $invocant->SUPER::new($invocant);
    $self->_copyArgs($args, $DEFAULTS);
    return $self;
}

# ------------------------------------------------------------------
# Track
# ------------------------------------------------------------------

sub getLeftMargin  {
    my($self) = @_;
    return 0;
}

sub getRightMargin  {
    my($self) = @_;
    return 0;
}

sub getHeight {
    my($self) = @_;
    my $parent = $self->getParent();
    my $font_h = $parent->getFontH();
    my $font_h_fhm = $self->{'font_h_fhm'};
    my $ht = $font_h_fhm * $font_h;
    my $v_pad = $self->getVPad();
    return int($ht + ($v_pad * 2));
}

sub svg {
    my($self) = @_;
    my $parent = $self->getParent();
    my $cc = $parent->getCharCoords();
    my $sl = $parent->getSeqLen();

    my $parent_font_h = $parent->getFontH();
    my $font_h_fhm = $self->{'font_h_fhm'};
    my $font_h = int($font_h_fhm * $parent_font_h);

    my $th = $self->getTickHeight();
    my $h = $self->getHeight();
    my $v_pad = $self->getVPad();
    my $draw_ticks = $self->{'draw_ticks'};

    my $svg = "";

    # y-coordinates of tick marks
    my($ty1, $ty2);
    if ($draw_ticks) {
	if ($draw_ticks eq 'top') {
	    $ty1 = 0;
	    $ty2 = $th;
	} else {
	    $ty1 = $h;
	    $ty2 = $ty1 - $th;
	}
    }

    my $seq_fn = $self->{'seq_fn'};

    # color functions
    my $bg_color_fn = $self->{'bg_color_fn'};
    my $stroke_color_fn = $self->{'stroke_color_fn'};
    my $fill_color_fn = $self->{'fill_color_fn'};
    my $tick_color = $self->{'tick_color'};
    # text style
    my $text_style = $self->{'text_style'};

    my $f_offset = ($h - ($v_pad * 2) - $font_h)/2;
    my $fy = $h - $v_pad - $f_offset;

    # loop over sequence positions
    for (my $s = 0;$s < $sl;++$s) {
	my($x1,$x2,$x3,$x4) = @{$cc->[$s]};

	# draw filled background (optional)
	my $bg_color = &$bg_color_fn($s);
	if ($bg_color) {
	    my $w = $x3 - $x2;
	    my $rh = $h - $v_pad;
	    $svg .= "<rect stroke=\"$bg_color\" fill=\"$bg_color\" x=\"$x2\" y=\"$v_pad\" width=\"$w\" height=\"$rh\" />\n";
	}

	# draw tick mark on the left edge of the character
	if ($draw_ticks) {
	    $svg .= "<line stroke-width=\"1\" x1=\"$x1\" x2=\"$x1\" y1=\"$ty1\" y2=\"$ty2\" stroke=\"$tick_color\" />\n";
	}

	# draw sequence character
	my $char = &$seq_fn($s);
	my $fx = ($x1 + $x4) / 2.0;

	my $sc = &$stroke_color_fn($s) || 'none';
	my $fc = &$fill_color_fn($s) || 'none';

	$svg .= "<text font-size=\"$font_h\" text-anchor=\"middle\" x=\"$fx\" y=\"$fy\" stroke=\"$sc\" fill=\"$fc\">\n";
	$svg .= " <tspan style=\"$text_style\">$char</tspan>\n";
	$svg .= "</text>\n";
    }
    return $svg;
}

# ------------------------------------------------------------------
# Sequence
# ------------------------------------------------------------------

sub getVPad {
    my($self) = @_;
    my $parent = $self->getParent();
    my $font_h = $parent->getFontH();
    return int($self->{'v_pad_fhm'} * $font_h);
}

sub getTickHeight {
    my($self) = @_;
    my $parent = $self->getParent();
    my $font_h = $parent->getFontH();
    return int($self->{'tick_h_fhm'} * $font_h);
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
