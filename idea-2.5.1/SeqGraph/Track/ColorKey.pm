package SeqGraph::Track::ColorKey;

=head1 NAME

ColorKey.pm

=head1 SYNOPSIS

A Track that draws a color key (e.g., for the colors used in the Sequence track).

=head1 DESCRIPTION

A Track that draws a color key (e.g., for the colors used in the Sequence track).
    
=cut

use strict;

use SeqGraph::Track::ColorKey;

our @ISA = ('SeqGraph::Track::Track');

# ------------------------------------------------------------------
# Globals 
# ------------------------------------------------------------------

my $DEFAULTS = {
    'font_h_fhm' => 1, 
    'v_pad_fhm' => 0.2,
    'h_align' => 'middle',
};

# ------------------------------------------------------------------
# Constructor
# ------------------------------------------------------------------

=head2 B<new>

 Usage   : my $seq_track = SeqGraph::Track::ColorKey->new();
 Function: Create a new color key track.
 Returns : A new instance of SeqGraph::Track::ColorKey
 Args    : A hashref with the following fields:
            font_h_fhm        - height of font (default is to use parent font height)
            v_pad_fhm         - vertical padding above and below each base/AA, as a multiple of font height
            h_align           - horizontal alignment: either 'start', 'end', or 'middle' (default)
            colors            - listref of hashrefs with the following fields
              color           - color (may be undefined)
              text            - text that should be overlaid on a rectangle filled with color (may be undefined)
              text_color      - text foreground color
              text_style      - SVG style for text
              caption         - caption that will appear to the right of the filled rectangle
              caption_color   - caption foreground color
              caption_style   - SVG style for caption

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

    # determine number of lines that will be needed to fit the key
    my $posns = $self->getColorsWithPositions();
    my $max_y = 0;

    foreach my $posn (@$posns) {
	my $y4 = $posn->{'y4'};
	$max_y = $y4 if ($y4 > $max_y);
    }
    
    return $max_y;
}

sub svg {
    my($self) = @_;
    my $parent = $self->getParent();
    my $svg = '';

    my $parent_font_h = $parent->getFontH();
    my $font_h_fhm = $self->{'font_h_fhm'};
    my $font_h = int($font_h_fhm * $parent_font_h);

    my $posns = $self->getColorsWithPositions();

    foreach my $posn (@$posns) {
	my($text_x1,$text_x2,$caption_x1,$caption_x2,$y1,$y2,$y3,$color) = 
	    map {$posn->{$_}} ('text_x1', 'text_x2', 'caption_x1', 'caption_x2', 'y1', 'y2', 'y3', 'color');
	my($color, $text, $text_color, $text_sty, $caption, $caption_color, $caption_sty) = 
	    map {$color->{$_}} ('color', 'text', 'text_color', 'text_style', 'caption', 'caption_color', 'caption_style');

	# shaded rectangle 
	my $rh = $y3 - $y1;
	my $rw = $text_x2 - $text_x1;

	if (defined($color)) {
	    $svg .= "<rect stroke=\"$color\" fill=\"$color\" x=\"$text_x1\" y=\"$y1\" width=\"$rw\" height=\"$rh\" />\n";

	    my $r_mid = ($text_x2 + $text_x1) / 2;

	    # text overlaid on shaded rectangle
	    if (defined($text)) {
		$svg .= "<text font-size=\"$font_h\" text-anchor=\"middle\" x=\"$r_mid\" y=\"$y2\" stroke=\"none\" fill=\"$text_color\">\n";
		$svg .= " <tspan style=\"$text_sty\">$text</tspan>\n";
		$svg .= "</text>\n";
	    }
	}

	# caption
 	$svg .= "<text font-size=\"$font_h\" text-anchor=\"start\" x=\"$caption_x1\" y=\"$y2\" stroke=\"none\" fill=\"$caption_color\">\n";
	$svg .= " <tspan style=\"$caption_sty\">$caption</tspan>\n";
 	$svg .= "</text>\n";
    }

    return $svg;
}

# ------------------------------------------------------------------
# ColorKey
# ------------------------------------------------------------------

sub getVPad {
    my($self) = @_;
    my $parent = $self->getParent();
    my $font_h = $parent->getFontH();
    return int($self->{'v_pad_fhm'} * $font_h);
}

# Compute height of an individual line in the key
sub getLineHeight {
    my($self) = @_;
    my $parent = $self->getParent();
    my $font_h = $parent->getFontH();
    my $font_h_fhm = $self->{'font_h_fhm'};
    my $ht = $font_h_fhm * $font_h;
    my $v_pad = $self->getVPad();
    return int($ht + ($v_pad * 4));
}

# Figure out relative x and y position of each color key.
sub getColorsWithPositions {
    my($self) = @_;

    # determine overall width
    my $parent = $self->getParent();
    my $coords = $parent->getCharCoords();
    my $max_x = 0;
    foreach my $coord (@$coords) {
	my($x1,$x2,$x3,$x4) = @$coord;
	$max_x = $x4 if ($x4 > $max_x);
    }

    # font width - average width of an individual character
    my $pfw = $parent->getFontW();
    my $font_h_fhm = $self->{'font_h_fhm'};
    my $font_w = $font_h_fhm * $pfw;

    # vertical padding 
    my $v_pad = $self->getVPad();
    my $line_height = $self->getLineHeight() - $v_pad;
    my $initial_x = 0;
    
    # compute positions
    my $colors = $self->{'colors'};
    my $x = $initial_x;
    my $y = 0;

    my $rows = [];
    my $cols = [];
    my $nc = scalar(@$colors);

    for (my $c = 0;$c < $nc;++$c) {
	my $color = $colors->[$c];
	my($text, $caption) = map {$color->{$_}} ('text', 'caption');
	my $textLen = length($text);
	my $captionLen = length($caption);

	# allow 0-width rectangle/text area only if color not defined
	$textLen = 1 if ($textLen < 1 && defined($color->{'color'}));

	# compute width 
	my $textWidth = $textLen * $font_w;
	my $captionWidth = $captionLen * $font_w;
	my $h_gap = $font_w * 0.5;
	my $width = $textWidth + $captionWidth + $h_gap;

	# start a new line?
	if (($x + $width) > $max_x) {
	    $x = $initial_x;
	    $y += $line_height;
	    push(@$rows, $cols) if (scalar(@$cols) > 0);
	    $cols = [];
	}

	push(@$cols, 	     
	     { 'text_x1' => $x,
	       'text_x2' => $x + $textWidth,
	       'caption_x1' => $x + $textWidth + $h_gap,
	       'caption_x2' => $x + $textWidth + $h_gap + $captionWidth,
	       'y1' => $y + $v_pad,
	       'y2' => $y + $line_height - (2 * $v_pad),
	       'y3' => $y + $line_height - $v_pad,
	       'y4' => $y + $line_height,
	       'color' => $color,
	       });

	$x += $width;
    }
    push(@$rows, $cols) if (scalar(@$cols) > 0);
    my $positions = [];

    # align each row
    my $align = $self->{'h_align'};

    foreach my $row (@$rows) {
	my $row_x2 = 0;
	foreach my $col (@$row) {
	    $row_x2 = $col->{'caption_x2'} if ($col->{'caption_x2'} > $row_x2);
	}

	# default = left-justify (align = start)
	my $offset = 0;

	if ($align eq 'middle') {
	    $offset = ($max_x - $row_x2) / 2;
	} elsif ($align eq 'end') {
	    $offset = $max_x - $row_x2;
	}

	foreach my $row (@$rows) {
	    foreach my $col (@$row) {
		foreach my $key (keys %$col) {
		    if ($key =~ /x\d$/) {
			$col->{$key} += $offset;
		    }
		}
		push(@$positions, $col);
	    }
	}
    }

    return $positions;
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

Copyright (c) 2006, The Institute for Genomic Research. 
All Rights Reserved.

=cut
