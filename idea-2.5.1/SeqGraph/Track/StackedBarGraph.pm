package SeqGraph::Track::StackedBarGraph;

=head1 NAME

StackedBarGraph.pm

=head1 SYNOPSIS

A Track that draws a bar graph with multiple bars in each column stacked atop one another.

=head1 DESCRIPTION

A Track that draws a bar graph with multiple bars in each column stacked atop one another.

=cut

use strict;

use SeqGraph::Track::Track;

our @ISA = ('SeqGraph::Track::Track');

# ------------------------------------------------------------------
# Globals 
# ------------------------------------------------------------------

my $DEFAULTS = {
    'bar_h_fhm' => 10,
    'x_axis_h' => 1,
    'y_axis_w' => 1,
    'font_h_fhm' => 1,
    'font_w_fhm' => 0.85,
    'tick_w_fhm' => 0.2,
    'tick_color' => 'black',
    'tick_h' => 2,
    'h_pad_fhm' => 0.35,
    'text_style' => 'font-weight: bold',
};

# ------------------------------------------------------------------
# Constructor
# ------------------------------------------------------------------

=head2 B<new>

 Usage   : my $seq_track = SeqGraph::Track::StackedBarGraph->new();
 Function: Create a new bar graph.
 Returns : A new instance of SeqGraph::Track::StackedBarGraph
 Args    : A hashref with the following fields:
            bar_h_fhm         - total bar graph height as a multiple of parent font height
            min_y_val         - minimum y-value (usually 0) - appears at bottom of y-axis unless invert_y
            max_y_val         - maximum y-value - appears at top of y-axis unless invert_y
            invert_y          - reverse min/max positions on y-axis
            y_val_fn          - function that maps seq index to a listref of values at that position
            colors            - listref of SVG colors that is as long as the longest listref returned by y_val_fn
            x_axis            - either 'top', 'bottom', or undef
            x_axis_h          - width/thickness of x-axis in pixels
            y_axis_w          - width/thickness of y-axis in pixels
            y_axis_labels     - listref of [y_val, label] pairs
            font_h_fhm        - height of label font expressed as multiple of parent font size
            font_w_fhm        - width of label font expressed as multiple of font height
            tick_h_fhm        - tick width as a multiple of parent font height
            tick_color        - SVG stroke color for tick marks
            tick_h            - tick height/thickness in pixels
            h_pad_fhm         - horizontal padding to the right of each label
            text_style        - SVG style for text/tspan element that contains the axis labels
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
    my $y_axis_w = $self->{'y_axis_w'};

    # left margin depends on max. y-axis label length
    my $parent = $self->getParent();
    my $font_w = $self->getFontW();
    my $max_label_len = 0;

    my $labels = $self->{'y_axis_labels'};
    foreach my $lbl (@$labels) {
	my($yval, $label_str) = @$lbl;
	my $ll = defined($label_str) ? length($label_str) : 0;
	$max_label_len = $ll if ($ll > $max_label_len);
    }

    # take into account horizontal spacing
    my $h_pad = $self->getHPad();
    my $width = $y_axis_w + ($max_label_len * $font_w) + $h_pad;

    # unlikely, but check for it anyway:
    my $tick_width = $self->getTickWidth();
    $width = $tick_width if ($tick_width > $width);

    return int($width);
}

sub getRightMargin  {
    my($self) = @_;
    return 0;
}

sub getHeight {
    my($self) = @_;
    my $parent = $self->getParent();
    my $bar_h = $self->getBarHeight();
    my $x_axis = $self->{'x_axis'};
    my $x_axis_h = $self->{'x_axis_h'};
    $bar_h += $x_axis_h if (defined($x_axis));

    return int($bar_h);
}

sub svg {
    my($self) = @_;
    my $parent = $self->getParent();
    my $cc = $parent->getCharCoords();
    my $sl = $parent->getSeqLen();

    my $graph_xmin = $cc->[0]->[0];
    my $graph_xmax = $cc->[scalar(@$cc)-1]->[3];

    my $parent_font_h = $parent->getFontH();
    my $bar_h_fhm = $self->{'bar_h_fhm'};
    my $bar_h = int($bar_h_fhm * $parent_font_h);

    my $svg = "";
    my $x_axis = $self->{'x_axis'};
    my $x_axis_h = $self->{'x_axis_h'};
    my $y_axis_w = $self->{'y_axis_w'};

    my $graph_ymin = 0;
    my $graph_ymax = $bar_h;
    my $x_axis_y;
    my $bar_h = $self->getBarHeight();

    # x-axis
    if (defined($x_axis)) {
	if ($x_axis eq 'top') {
	    $graph_ymin = $x_axis_h;
	    $graph_ymax = $x_axis_h + $bar_h;
	    $x_axis_y = 0;
	} else {
	    $x_axis_y = $graph_ymax + $x_axis_h;
	}
	my $x_axis_xmin = $graph_xmin - $y_axis_w;
	$svg .= "<line x1=\"$x_axis_xmin\" y1=\"$x_axis_y\" x2=\"$graph_xmax\" y2=\"$x_axis_y\" ";
	$svg .= "stroke-width=\"$x_axis_h\" stroke=\"black\" />\n";
    }

    # y-axis
    my $y_axis_ymin = $graph_ymin;
    my $y_axis_ymax = $graph_ymax;

    # adjust y-axis coords slightly to ensure overlap with x-axis
    if (defined($x_axis)) {
	if ($x_axis eq 'top') { 
	    $y_axis_ymin -= $x_axis_h;
	} else {
	    $y_axis_ymax += $x_axis_h;
	}
    }

    my $y_axis_x = $graph_xmin - $y_axis_w;
    $svg .= "<line x1=\"$y_axis_x\" y1=\"$y_axis_ymin\" x2=\"$y_axis_x\" y2=\"$y_axis_ymax\" ";
    $svg .= "stroke-width=\"$y_axis_w\" stroke=\"black\" />\n";

    # y-axis labels
    my $min_y_val = $self->{'min_y_val'};
    my $max_y_val = $self->{'max_y_val'};
    my $y_val_diff = $max_y_val - $min_y_val;

    my $tw = $self->getTickWidth();
    my $th = $self->{'tick_h'};
    my $tick_color = $self->{'tick_color'};
    my $h_pad = $self->getHPad();

    my $ylx = $y_axis_x - $h_pad;
    my $y_axis_labels = $self->{'y_axis_labels'};
    my $font_h = $self->getFontH();
    my $text_style = $self->{'text_style'};
    my $invert_y = $self->{'invert_y'};

    foreach my $lbl (@$y_axis_labels) {
	my($lbl_y, $lbl_str) = @$lbl;
	my $ylyfrac = ($lbl_y - $min_y_val) / $y_val_diff;
	my $ty1 = $invert_y ? ($ylyfrac * $bar_h) + $graph_ymin : $graph_ymax - ($ylyfrac * $bar_h);
	my $yly =  + $ty1 + ($font_h/2);

	my $tx2 = $y_axis_x;
	my $tx1 = $tx2 - $tw;

	# draw tick mark
	$svg .= "<line stroke-width=\"$th\" x1=\"$tx1\" x2=\"$tx2\" y1=\"$ty1\" y2=\"$ty1\" stroke=\"$tick_color\" />\n";

	# draw label
	if (defined($lbl_str)) {
	    $svg .= "<text font-size=\"$font_h\" text-anchor=\"end\" x=\"$ylx\" y=\"$yly\">\n";
	    $svg .= " <tspan style=\"$text_style\">$lbl_str</tspan>\n";
	    $svg .= "</text>\n";
	}
    }

    # draw bars at each sequence position
    my $y_val_fn = $self->{'y_val_fn'};
    my $colors = $self->{'colors'};

    my $ncc = scalar(@$cc);
    for (my $c = 0;$c < $ncc;++$c) {
	my($x1,$x2,$x3,$x4) = @{$cc->[$c]};	
	my $yvals = &$y_val_fn($c);
	my $y_sum = 0;
	my $nyv = scalar(@$yvals);
	
	for (my $y = 0;$y < $nyv;++$y) {
	    # each value gets graphed on top of (or below) the previous one
	    my $yval = $yvals->[$y];
	    my $y_sum_new = $y_sum + $yval;

	    if ($yval > 0) {
		# convert $y_sum and $y_sum_new to pixel positions
		my $y1frac = ($y_sum - $min_y_val) / $y_val_diff;

		my $ht = (($yval / $y_val_diff) * $bar_h);
		my $y1 = $invert_y ? ($y1frac * $bar_h) + $graph_ymin : $graph_ymax - ($y1frac * $bar_h) - $ht;

		my $color = $colors->[$y];
		my $rw = $x3-$x2;
		$svg .= "<rect stroke=\"$color\" fill=\"$color\" x=\"$x2\" y=\"$y1\" width=\"$rw\" height=\"$ht\" />\n";
		$y_sum = $y_sum_new;
	    }
	}
    }

    return $svg;
}

# ------------------------------------------------------------------
# StackedBarGraph
# ------------------------------------------------------------------

sub getBarHeight {
    my($self) = @_;
    my $parent = $self->getParent();
    my $font_h = $parent->getFontH();
    my $bar_h_fhm = $self->{'bar_h_fhm'};
    return $font_h * $bar_h_fhm;
}

sub getFontH {
    my($self) = @_;
    my $parent = $self->getParent();
    my $parent_font_h = $parent->getFontH();
    my $font_h_fhm = $self->{'font_h_fhm'};
    return int($parent_font_h * $font_h_fhm);
}

sub getFontW {
    my($self) = @_;
    my $font_h = $self->getFontH();
    return int($font_h * $self->{'font_w_fhm'});
}

sub getHPad {
    my($self) = @_;
    my $parent = $self->getParent();
    my $font_h = $parent->getFontH();
    return int($self->{'h_pad_fhm'} * $font_h);
}

sub getTickWidth {
    my($self) = @_;
    my $parent = $self->getParent();
    my $font_h = $parent->getFontH();
    return int($self->{'tick_w_fhm'} * $font_h);
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
