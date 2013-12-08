package SeqGraph::Track::SequenceCoords;

=head1 NAME

SequenceCoords.pm

=head1 SYNOPSIS

A Track that shows the sequence coordinates at regular intervals.

=head1 DESCRIPTION

A Track that shows the sequence coordinates at regular intervals.

=cut

use strict;

use SeqGraph::Track::Track;

our @ISA = ('SeqGraph::Track::Track');

# ------------------------------------------------------------------
# Globals 
# ------------------------------------------------------------------

my $DEFAULTS = {
    # vertical padding above and below each base/AA, as a multiple of font height
    'tick_color' => 'black',
    'v_pad_fhm' => 0.3,
    'tick_h_fhm' => 0.2,
    'font_h_fhm' => 1.0,
    'stroke_color_fn' => sub { return 'black'; },
    'fill_color_fn' => sub { return 'black'; },
    'text_style' => 'font-weight: bold; font-family: monospaced',
    'coord_offset' => 0,
    'coord_interval' => 1,
    'tick_interval' => 10,
};

# ------------------------------------------------------------------
# Constructor
# ------------------------------------------------------------------

=head2 B<new>

 Usage   : my $seq_track = SeqGraph::Track::SequenceCoords->new();
 Function: Create a new sequence track.
 Returns : A new instance of SeqGraph::Track::SequenceCoords
 Args    : A hashref with the following fields:
            v_pad_fhm         - vertical padding above and below each seq coord, as a multiple of font height
            draw_ticks        - either 'top', 'bottom', or undef
            tick_color        - SVG stroke color for tick marks
            tick_h_fhm        - tick height as a multiple of font height
            font_h_fhm        - height of sequence font (default is to use parent font height)
            stroke_color_fn   - function that maps seq index to SVG stroke color (or undef for none)
            fill_color_fn     - function that maps seq index to SVG fill color (or undef for none)
            text_style        - SVG style for text/tspan element that contains the sequence coordinates
            tick_interval     - how often to display tick marks
            coord_offset      - index of the first character in the sequence
            coord_interval    - how often to display the sequence coordinate, in *number of ticks*
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

sub getLeftMargin {
    my($self) = @_;
    return 0;
}

sub getRightMargin {
    my($self) = @_;

    # set right margin to width of maximum coordinate label size
    # (this should be an upper bound)

    # TODO

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

    # y-coordinates of tick marks and font
    my($ty1, $ty2, $fy);

    if ($draw_ticks) {
	if ($draw_ticks eq 'top') {
	    $ty1 = 0;
	    $ty2 = $th;
	    $fy = $font_h + $v_pad;
	} else {
	    $ty1 = $h;
	    $ty2 = $ty1 - $th;
	    $fy = $h - $v_pad;
	}
    }

    # color functions
    my $bg_color_fn = $self->{'bg_color_fn'};
    my $stroke_color_fn = $self->{'stroke_color_fn'};
    my $fill_color_fn = $self->{'fill_color_fn'};
    my $tick_color = $self->{'tick_color'};
    # text style
    my $text_style = $self->{'text_style'};

    my $c_offset = $self->{'coord_offset'};
    my $c_int = $self->{'coord_interval'};
    my $t_int = $self->{'tick_interval'};

    # count number of ticks between coordinate labels
    my $num_ticks = 0;

    # loop over sequence positions
    for (my $c = $t_int-1; $c < $sl;$c += $t_int) {
	# draw tick -- in the center of the sequence base/AA
	my($x1,$x2,$x3,$x4) = @{$cc->[$c]};
	my $xmid = ($x2 + $x3)  / 2;
	if ($draw_ticks) {
	    $svg .= "<line stroke-width=\"1\" x1=\"$xmid\" x2=\"$xmid\" y1=\"$ty1\" y2=\"$ty2\" stroke=\"$tick_color\" />\n";
	}

	# draw coordinate label
	if (++$num_ticks == $c_int) {
	    my $index = $c + $c_offset;
	    my $sc = &$stroke_color_fn($c) || 'none';
	    my $fc = &$fill_color_fn($c) || 'none';
	    $svg .= "<text font-size=\"$font_h\" text-anchor=\"middle\" x=\"$xmid\" y=\"$fy\" stroke=\"$sc\" fill=\"$fc\">\n";
	    $svg .= " <tspan style=\"$text_style\">$index</tspan>\n";
	    $svg .= "</text>\n";
	    $num_ticks = 0;
	}
    }

    return $svg;
}

# ------------------------------------------------------------------
# SequenceCoords
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
