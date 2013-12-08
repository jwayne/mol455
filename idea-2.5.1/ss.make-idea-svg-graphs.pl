#!/usr/bin/perl

=head1 NAME

ss.make-idea-svg-graphs.pl - Generate a set of SVG graphs from MEPipe output files.

=head1 SYNOPSIS

ss.make-idea-svg-graphs.pl
      --file_prefix=gene5 
    [ --data_dir=. ]
    [ --model_pairs='3:2,8:7']
    [ --matching_probs_only ]
    [ --key_alignment=middle ]
    [ --verbose ]
    [ --help ]
    [ --man ]

=head1 PARAMETERS

--file_prefix
    Unique prefix that identifies all the files from a single IDEA run.

--data_dir
    Location of the directory in which the codeml/IDEA output files can be found.

--model_pairs
    Generate side-by-side comparison graphs for each of the pairs of models named
    in this parameter.  For example, a value of '3:2,8:7' will generate all pairwise
    graphs with model 3 on the top and model 2 on the bottom, and all pairwise graphs
    with model 8 on the top and model 7 on the bottom.

--matching_probs_only
    Only generate pairwise graphs/figures for matching sets of posterior probabilities
    (e.g. plot Naive Empirical Bayes against Naive Empirical Bayes, but not against
    Bayes Empirical Bayes.)

--key_alignment
    Horizontal alignment of the color key: either 'start', 'end', or 'middle'.

--verbose
    Whether to print status messages

--help
    Displays the usage statement.   

--man
    Displays this man page.

=head1 DESCRIPTION

Generate a comprehensive HTML report for a set of IDEA output files.  Uses the
Bioperl PAML parser.

TODO:
-deal with confidence intervals in Bayes Empirical Bayes posterior probabilities?

=cut

BEGIN {unshift(@INC, '<IDEA_BASE_DIR>');}

use strict;

use Carp;
use FileHandle;
use Getopt::Long;
use Pod::Usage;

# bioperl modules
use Bio::Tools::Phylo::PAML;

# IDEA/codeml-related modules
use RST;

# SVG drawing routines
use SeqGraph::Graph;
use SeqGraph::Track::ColorKey;
use SeqGraph::Track::Sequence;
use SeqGraph::Track::SequenceCoords;
use SeqGraph::Track::StackedBarGraph;

# ------------------------------------------------------------------
# Globals
# ------------------------------------------------------------------

# Here is a sample set of input/output files for a single IDEA run:
#
#-rw-r--r--  1 crabtree users    45 Oct 26 16:23 gene5.PAMLtree
#-rw-r--r--  1 crabtree users   645 Oct 26 16:23 gene5.aa
#-rw-r--r--  1 crabtree users  1222 Oct 26 16:23 gene5.aln
#-rw-r--r--  1 crabtree users   914 Oct 26 16:23 gene5.aln.nex
#-rw-r--r--  1 crabtree users  1849 Oct 26 16:23 gene5.nuc.aln
#-rw-r--r--  1 crabtree users  1856 Oct 26 16:23 gene5.nuc.aln.PAMLseq
#-rw-r--r--  1 crabtree users  1837 Oct 26 16:23 gene5.seq
#-rw-r--r--  1 crabtree users  1840 Oct 26 16:23 gene5.seq.original
#-rw-r--r--  1 crabtree users 14764 Oct 26 16:23 gene5.w0.1.PAMLout
#-rw-r--r--  1 crabtree users 74266 Oct 26 16:23 gene5.w01.rst
#-rw-r--r--  1 crabtree users 14825 Oct 26 16:23 gene5.w1.5.PAMLout
#-rw-r--r--  1 crabtree users 76473 Oct 26 16:23 gene5.w15.rst

# The following data structure records the file suffixes that the
# script should expect to find.  Those with 'w' => 1 indicate files
# that will be parameterized by w-value (e.g. 0.1, 1.5).
#
my $MEPIPE_FILE_SUFFIXES = {
    'PAMLtree' => {'suffix' => '.PAMLtree', 'optional' => 1, 'w' => 0 },
#    'PAUPtree' => {'suffix' => '.PAUPtree', 'optional' => 1, 'w' => 0 },
    'aa' => {'suffix' => '.aa', 'optional' => 1, 'w' => 0 },
    'aln' => {'suffix' => '.aln', 'optional' => 1, 'w' => 0 },
    'aln.PAUPtree' => {'suffix' => '.aln.PAUPtree', 'optional' => 1, 'w' => 0 },
    'dnd' => {'suffix' => '.dnd', 'optional' => 1, 'w' => 0 },
    'lnf' => {'suffix' => '.lnf', 'optional' => 1, 'w' => 1 },
    'aln.nex' => {'suffix' => '.aln.nex', 'optional' => 1, 'w' => 0 },
    'nuc.aln' => {'suffix' => '.nuc.aln', 'optional' => 1, 'w' => 0 },
    'nuc.aln.PAMLseq' => {'suffix' => '.nuc.aln.PAMLseq', 'optional' => 1, 'w' => 0 },
    'seq' => {'suffix' => '.seq', 'optional' => 1, 'w' => 0 },
    'seq.original' => {'suffix' => '.seq.original', 'optional' => 1, 'w' => 0 },
    
    'mlc' => {'suffix' => '.mlc', 'optional' => 1, 'w' => 0 },
    
    # alternative suffix for .mlc
    'merged' => {'suffix' => '.merged', 'optional' => 1, 'w' => 0 },

    # files parameterized by w
    'PAMLout' => {'suffix' => '.PAMLout', 'optional' => 1, 'w' => 1 },
    '' => {'suffix' => '', 'optional' => 1, 'w' => 1 }, # In single-gene mode, output filenames end with w values.
    'rst' => {'suffix' => '.mst', 'optional' => 0, 'w' => 0 },
};

# Define colors for 11 site classes, since codeml uses 10 site classes to 
# approximate a continuous distribution + one for w.  The first 3 colors
# match those in the figure from Yang, Z. et al. (2000)  Maximum likelihood
# analysis of molecular adaption in abalone sperm lysin reveals variable
# selective pressures among lineages and sites.  Mol. Biol. Evol. 17, 1446-1455.
#
my $CODEML_CLASS_COLORS = 
    [
     'rgb(200,200,200)',
     'rgb(100,200,140)',
     'rgb(236,40,45)',
     'rgb(250,166,47)',
     'rgb(180,0,255)',
     'rgb(106,106,244)',
     'rgb(244,102,219)',
     'rgb(17,145,20)',
     'rgb(103,207,229)',
     'rgb(229,146,173)',
     'rgb(247,232,74)',
     ];

# ------------------------------------------------------------------
# Input
# ------------------------------------------------------------------

my($data_dir, $file_prefix, $model_pairs, $matching_probs_only, $key_alignment, $verbose, $help, $man);

&GetOptions("data_dir=s" => \$data_dir,
	    "file_prefix=s" => \$file_prefix,
	    "model_pairs=s" => \$model_pairs,
	    "matching_probs_only!" => \$matching_probs_only,
	    "key_alignment=s" => \$key_alignment,
	    "verbose" => \$verbose,
	    "help" => \$help,
	    "man" => \$man,
	    );

pod2usage(1) if $help;
pod2usage({-verbose => 2}) if $man;

# defaults
$data_dir = "." if (!defined($data_dir));
$matching_probs_only = 0 if (!defined($matching_probs_only));
$key_alignment = 'left' if (!defined($key_alignment));

pod2usage({-message => "Error:\n     --file_prefix must be specified\n", -exitstatus => 0, -verbose => 0}) if (!$file_prefix);
pod2usage({-message => "Error:\n     --data_dir=$data_dir is not readable\n", -exitstatus => 0, -verbose => 0}) if (!-r $data_dir);
pod2usage({-message => "Error:\n     illegal value for --key_alignment; must be 'start', 'middle', or 'end'\n", -exitstatus => 0, -verbose => 0}) if (!-r $data_dir);

# ------------------------------------------------------------------
# Main program
# ------------------------------------------------------------------

my $all_files = &findFiles({dir => $data_dir, prefix => $file_prefix});
my $w_parsed_data = &parseCodemlFiles({dir => $data_dir, files => $all_files});
my $w_values = $all_files->{'w_values'};

# function to abbreviate posterior prob names
my $getPosteriorProbAbbreviation = sub {
    my($pp, $index) = @_;

    if ($pp =~ /\(NEB\)/) {
	return 'NEB';
    } elsif ($pp =~ /\(BEB\)/) {
	return 'BEB';
    } elsif ($pp =~ /posterior p/i) {
	return 'PP';
    }

    # default: fallback to using index
    return "p${index}";
};

# foreach prior $w value
foreach my $w (@$w_values) {
    my $data = $w_parsed_data->{$w};
    my $result = $data->{'result'}; # Bio::Tools::Phylo::PAML::Result
    my $rst = $data->{'rst'};
    my $rst_models = $rst->{'models'};

    print "w=$w\n";
    my @model_nums = sort { $a <=> $b } keys %$rst_models;

    # make graph for each individual model and set of posterior probs
    foreach my $mn (@model_nums) {
	my $model = $rst_models->{$mn};
	my @postProbs = sort keys %{$model->{'posterior_probs'}};
	my $npp = scalar(@postProbs);

	for (my $p = 0;$p < $npp;++$p) {
	    my $pp = $postProbs[$p];
	    my $svgs = &makePosteriorProbGraph({'model' => $model, 'posterior_prob' => $pp});
	    my $numSVGsReturned = scalar(@$svgs);
	    if ($numSVGsReturned != 2){
		die "Expected:  2 SVG files; Created:  $numSVGsReturned\n";
	    }
		
	    my $ppAbbrev = &$getPosteriorProbAbbreviation($pp, $p);

	    print " m${mn} p=$ppAbbrev\n";

	    # write SVG figure to file
	    for (my $i = 0; $i < 2; $i++){
		my $svg_fh = FileHandle->new();
		$svg_fh->open(">${file_prefix}-w${w}-m${mn}-${ppAbbrev}" . (($i == 1) ? ".key" : "") . ".svg");
		$svg_fh->print($$svgs[$i]);
		$svg_fh->close();
	    }
	}
    }

    # make graph for specified pairs of models (if both are present)
    if (defined($model_pairs) && ($model_pairs =~ /\d/)) {
	$model_pairs =~ s/\s//g;
	my @mpList = split(',', $model_pairs);

	foreach my $mpp (@mpList) {
	    my($m1, $m2) = split(':', $mpp);

	    my $model1 = $rst_models->{$m1};
	    my $model2 = $rst_models->{$m2};
	    
	    # check both models are defined
	    if (!defined($model1)) {
		print STDERR "not generating figure for model $m1 vs. $m2: model $m1 is not defined in this dataset.\n";
		next;
	    } 
	    elsif (!defined($model2)) {
		print STDERR "not generating figure for model $m1 vs. $m2: model $m2 is not defined in this dataset.\n";
		next;
	    }
	    
	    # look at all combinations of posterior probs
	    my @postProbs1 = sort keys %{$model1->{'posterior_probs'}};
	    my @postProbs2 = sort keys %{$model2->{'posterior_probs'}};
	    
	    my $npp1 = scalar(@postProbs1);
	    my $npp2 = scalar(@postProbs2);
	    
	    for (my $p1 = 0;$p1 < $npp1;++$p1) {
		my $pp1 = $postProbs1[$p1];

		for (my $p2 = 0;$p2 < $npp2;++$p2) {
		    my $pp2 = $postProbs2[$p2];
		    
		    # only graph matching posterior probs if $matching_probs_only
		    next if (($matching_probs_only) && ($pp1 ne $pp2));
		    
		    my $svg = &makePosteriorProbGraphPair({'model_1' => $model1, 'model_2' => $model2, 
							   'posterior_prob_1' => $pp1, 'posterior_prob_2' => $pp2 });
		    
		    my $ppAbbrev1 = &$getPosteriorProbAbbreviation($pp1, $p1);
		    my $ppAbbrev2 = &$getPosteriorProbAbbreviation($pp2, $p2);
		    
		    print " m${m1} vs. m${m2} p1=$ppAbbrev1 p2=$ppAbbrev2\n";
		    
		    # write SVG figure to file
		    my $svg_fh = FileHandle->new();
		    $svg_fh->open(">${file_prefix}-w${w}-m${m1}-${ppAbbrev1}-v-m${m2}-${ppAbbrev2}.svg");
		    $svg_fh->print($svg);
		    $svg_fh->close();
		}
	    }
	}
    }
    print "\n";
}

exit(0);

# ------------------------------------------------------------------
# Subroutines - public
# ------------------------------------------------------------------

=head2 B<findFiles>

 Usage   : my $files = &findFiles({dir => "./data_dir", prefix => "gene5"});
 Function: Check that all the desired files are present and readable.
 Returns : 
 Args    : A hashref with the following fields:
            dir       - Directory in which all the IDEA files are located.
            prefix    - Prefix that identifies all the files from the desired IDEA run.
 
=cut

sub findFiles {
    my($args) = @_;
    my($dir, $prefix) = map {$args->{$_}} ('dir', 'prefix');
    
    opendir(CMD, $dir);
    my @all_files = grep(/^($prefix|ONLY)/, readdir(CMD));
    closedir(CMD);

    # list of w-values observed in the file names
    my $w_values = {};
    # hashref of files indexed by suffix
    my $files = {};
    # files that depend on w, indexed by w and suffix
    my $w_files = {};

    # group files by w value and suffix
    my $files_by_w = {};

    # examine file names and check them against $MEPIPE_FILE_SUFFIXES
    foreach my $file (@all_files) {
	my(undef, $suffix) = ($file =~ /^($prefix|ONLY)\.(.*)$/);
	my $w = undef;

	# see whether $suffix includes a 'w' value
	if ($suffix =~ /^w(\d+([\_\.]\d+)?)\.(\S+)$/) {
	    $w = $1;
	    $suffix = $3;
	    # some files look like "gene5.w0.1.PAMLout", but others "gene5.w0_1.rst";
	    # add the decimal point if none was found
	    if ($w !~ /\./) {
		if ($w =~ /\_/){
		    $w =~ s/\_/\./;
		}
		else{
		    # assumes only 1 digit after decimal point
		    my $wl = length($w);
		    $w = substr($w,0,$wl-1) . '.' . substr($w,$wl-1,1);
		}
	    }
	}
	elsif ($suffix =~ /^w(\d+([\_\.]\d+)?)$/) {
	    $w = $1;
	    $suffix = "";
	    # some files look like "gene5.w0.1.PAMLout", but others "gene5.w01.rst";
	    # add the decimal point if none was found
	    if ($w !~ /\./) {
		if ($w =~ /\_/){
		    $w =~ s/\_/\./;
		}
		else{
		    # assumes only 1 digit after decimal point
		    my $wl = length($w);
		    $w = substr($w,0,$wl-1) . '.' . substr($w,$wl-1,1);
		}
	    }
	}

	my $type = $MEPIPE_FILE_SUFFIXES->{$suffix};
	# ignore unknown file types
	if (!defined($type) && ($suffix !~ /~$/)) {
	    next;
	}

	# case 1: file depends on w
	if ($type->{'w'}) {
	    unless ($suffix){
		next;
	    }
	    # check that a w-value was found if one was expected
	    if (!defined($w)) {
		carp "unable to parse w-value from filename '$file'";
		next;
	    }
	    $w_values->{$w} = 1;
	    my $hash = $w_files->{$w};
	    $hash = $w_files->{$w} = {} if (!defined($hash));
	    $hash->{$suffix} = $file;
	}
	# case 2: file does not depend on w
	else {
	    $files->{$suffix} = $file;
	}
    }

    # check that all the required files were found and are readable
    my @w_values = sort keys %$w_values;

    my $checkFiles = sub {
	my($depends_on_w, $files) = @_;

	foreach my $suffix (keys %$MEPIPE_FILE_SUFFIXES) {
	    my $type = $MEPIPE_FILE_SUFFIXES->{$suffix};
	    next unless ($depends_on_w == $type->{'w'});
	    my $file = $files->{$suffix};
	    my $errorString = "did not find \.$suffix file";
	    if ($depends_on_w){
		$errorString.= " (which should depend on w)";
	    }
	    carp "$errorString for $prefix in ${dir}/" if (!defined($file) && (!$type->{'optional'}));
	    carp "${dir}/${file} is not readable" if (!-r "${dir}/${file}");
	}
    };

    # check files that don't depend on w
    &$checkFiles(0, $files);

    # check files that depend on w
    foreach my $w (@w_values) {
	my $wf = $w_files->{$w};
	&$checkFiles(1, $wf);
    }

    # print summary
    if ($verbose) {
	my $nf = scalar(keys %$files);
	my $ws = join(',', @w_values);
	map { $nf += scalar(keys %{$w_files->{$_}}); } @w_values;
	print STDERR "found $nf files; w-values=$ws\n";
    }

    return { 'files' => $files, 'w_files' => $w_files, 'w_values' => \@w_values };
}

=head2 B<parseCodemlFiles>

 Usage   : my $data = &parseCodemlFiles({dir => "./data_dir", files => $files});
 Function: Parse the codeml output files in $files.
 Returns : 
 Args    : A hashref with the following fields:
            dir       - Directory in which all the IDEA files are located.
            files     - Value returned by &findFiles
 
=cut

sub parseCodemlFiles {
    my($args) = @_;
    my($dir, $files) = map {$args->{$_}} ('dir', 'files');

    my $w_parsed_data = {};
    my $nonw_files = $files->{'files'};  # aegan:  I added this.
    my $w_values = $files->{'w_values'};
    my $w_files = $files->{'w_files'};
    
    # use Bioperl PAML module to parse w-dependent files
    foreach my $w (@$w_values) {
	my $wf = $w_files->{$w};

	# aegan:  Where nonw_files occurs below, it has been substituted for wf.
	my $mlc = $nonw_files->{"mlc"} || $nonw_files->{"merged"} || $wf->{"PAMLout"} || $wf->{""};
	my $rst = $nonw_files->{"rst"};
	my $parser = new Bio::Tools::Phylo::PAML(-file => "${dir}/${mlc}");
	
	my $result = undef;
	my @models = undef;

	eval{
	    if ($result = $parser->next_result()) {
		@models = $result->get_NSSite_results();
	    } 
	    else {
		print STDERR "unable to parse ${dir}/${mlc}\n";
	    }
	}; # aegan:  This eval block was added to support PAML 3.14.

	# parse RST file
	my $rst_fh = FileHandle->new();
	my $rst_path = $dir . '/' . $rst;
	$rst_fh->open($rst_path, 'r') || die "unable to read from $rst_path";
	my $rst_data = &RST::parseRstFile({fh => $rst_fh, filename => $rst_path});
	$rst_fh->close();

	$w_parsed_data->{$w} = { 'result' => $result, 'models' => \@models, 'rst' => $rst_data };
    }
    return $w_parsed_data;
}

=head2 B<makePosteriorProbGraph>

 Usage   : my $svg = &makePosteriorProbGraph({'model' => $model, 'posterior_prob' => ''});
 Function: Create a graph of the posterior probabilities for a single model.
 Returns : An SVG-format figure.
 Args    : A hashref with the following fields:
            model             - parsed RST model data
            posterior_prob    - one of the keys from $model->{'posterior_probs'}
=cut

sub makePosteriorProbGraph {
    my($args) = @_;
    my($model, $pp) = map {$args->{$_}} ('model', 'posterior_prob');
    my $tracks = &_getPosteriorProbGraphTracks({'model' => $model, 'posterior_prob' => $pp, 'invert_y' => 0});
    my @colorKeyGraphTrackArray = ($$tracks[0]);
    shift(@$tracks);
    my $graphArgs = { 'seqlen' => scalar(@{$model->{'posterior_probs'}->{$pp}->{'site_probs'}}), 'tracks' => $tracks };
    my $seqGraph = SeqGraph::Graph->new($graphArgs);
    my $colorKeyGraphTrackList = \@colorKeyGraphTrackArray;
    my $colorKeyGraphArgs = { 'seqlen' =>scalar(@{$model->{'posterior_probs'}->{$pp}->{'site_probs'}}), 'tracks' => $colorKeyGraphTrackList };
    my $colorKeyGraph = SeqGraph::Graph->new($colorKeyGraphArgs);
    my @svgs = ($seqGraph->svg(), $colorKeyGraph->svg());
    return \@svgs;
}

=head2 B<makePosteriorProbGraphPair>

 Usage   : my $svg = &makePosteriorProbGraphPair({'model_1' => $model1, 'model_2' => $model2, 'posterior_prob' => $pp});
 Function: Create a graph of the posterior probabilities for a pair of nested models.
 Returns : An SVG-format figure.
 Args    : A hashref with the following fields:
            model_1           - parsed RST model data for the first model
            model_2           - parsed RST model data for the second model
            posterior_prob_1  - one of the keys from $model_1->{'posterior_probs'}
            posterior_prob_2  - one of the keys from $model_2->{'posterior_probs'}
=cut

sub makePosteriorProbGraphPair {
    my($args) = @_;
    my($model1, $model2, $pp1, $pp2) = map {$args->{$_}} ('model_1', 'model_2', 'posterior_prob_1', 'posterior_prob_2');

    my $tracks1 = &_getPosteriorProbGraphTracks({'model' => $model1, 'posterior_prob' => $pp1, 'invert_y' => 0});
    my $tracks2 = &_getPosteriorProbGraphTracks({'model' => $model2, 'posterior_prob' => $pp2, 'invert_y' => 1});

    my $sl1 = scalar(@{$model1->{'posterior_probs'}->{$pp1}->{'site_probs'}});
    my $sl2 = scalar(@{$model2->{'posterior_probs'}->{$pp2}->{'site_probs'}});

    croak "widths of model1 and model2 differ" if ($sl1 != $sl2);

    my @all_tracks = (@$tracks1, @$tracks2);

    my $graphArgs = { 'seqlen' => $sl1, 'tracks' => \@all_tracks };
    my $seqGraph = SeqGraph::Graph->new($graphArgs);
    return $seqGraph->svg();
}

# ------------------------------------------------------------------
# Subroutines - private
# ------------------------------------------------------------------

=head2 B<_getPosteriorProbGraphTracks>

 Usage   : my $tracks = &getPosteriorProbGraphTracks({'rst_model' => $model, 'invert_y' => 1});
 Function: Generate a listref of SeqGraph::Track::Tracks that can be passed to SeqGraph::Graph
 Returns : Listref of SeqGraph::Track::Track objects for a posterior probability graph.
 Args    : A hashref with the following fields:
            model     - parsed RST model data
            invert_y  - Whether to invert the y-axis. 
=cut

sub _getPosteriorProbGraphTracks {
    my($args) = @_;
    my($model, $pp, $invert_y) = map {$args->{$_}} ('model', 'posterior_prob', 'invert_y');

    my $probs = $model->{'posterior_probs'};
    my $np = scalar(keys %$probs);
    my $postProbs = undef;

    if (!defined($pp)) {
	my @allProbs = values %$probs;
	$postProbs = $allProbs[0];
	if ($np > 1) {
	    print STDERR "no posterior probabilities specified for model $model - using set 1/$np\n";
	}
    } else {
	$postProbs = $probs->{$pp};
	die "no such posterior probabilities ('$pp') in model $model" if (!defined($postProbs));
    }

    my $siteProbs = $postProbs->{'site_probs'};

    # ---------------------------------
    # report w for each site class
    # ---------------------------------

    my $wvals = $model->{'w_values'};
    my $nwv = scalar(@$wvals);

    my $colors = [];

    # prefix the key with the model name
    push(@$colors, {
	'color' => undef,
	'text' => undef,
	'caption' => "Model #" . $model->{'model_num'},
	'caption_color' => 'black',
	'caption_style' => 'font-weight: bold',
    });

    for (my $c = 0;$c < $nwv;++$c) {
	push(@$colors, {
	    'color' => $CODEML_CLASS_COLORS->[$c],
	    'text' => $c+1,
	    'text_color' => 'black',
	    'text-style' => 'font-weight: bold; font-family: monospaced',
	    'caption' => "w=" . $wvals->[$c],
	    'caption_color' => 'black',
	    'caption_style' => 'font-weight:bold; font-style: italic',
	});
    }

    # color key
    my $ckTrackArgs = {
	'font_h_fhm' => 1.2,
	'colors' => $colors,
	'h_align' => $key_alignment,
    };
    my $ckT = SeqGraph::Track::ColorKey->new($ckTrackArgs);

    # ---------------------------------
    # sequence coordinates
    # ---------------------------------
    
    my $seqCoordArgs = {
	'draw_ticks' => $invert_y ? 'top' : 'bottom',
	'tick_h_fhm' => 0.2,
	'v_pad_fhm' => 0.35,
	'tick_interval' => 10,
	'coord_interval' => 1,
	'coord_offset' => 1,
    };
    my $coordT = SeqGraph::Track::SequenceCoords->new($seqCoordArgs);
    
    # ---------------------------------
    # graph of posterior probabilities
    # ---------------------------------

    my $ppFun = sub {
	my($index) = @_;
	return $siteProbs->[$index]->{'probs'};
    };
    
    # tick marks from 0.0 to 1.0, label every 0.2
    my $y_axis_lbls = [];
    my $flip = 0;
    for (my $yv = 0;$yv <= 1;$yv += 0.1) {
	my $lbl = ($flip++ % 2) ? undef : sprintf("%0.1f", $yv);
	push(@$y_axis_lbls, [$yv, $lbl]);
    }
    
    my $barGraphArgs = {
	'min_y_val' => 0.0,
	'max_y_val' => 1.0,
	'y_axis_labels' => $y_axis_lbls,
	'y_val_fn' => $ppFun,
	'colors' => $CODEML_CLASS_COLORS,
	'invert_y' => $invert_y ? 0 : 1,
	'x_axis' => $invert_y ? 'top' : 'bottom',
	'x_axis_h' => 2,
	'y_axis_w' => 2,
    };
    my $barGraphT = SeqGraph::Track::StackedBarGraph->new($barGraphArgs);
    
    # ---------------------------------
    # reference aa seq
    # ---------------------------------
    
    # make hash of positively selected sites
    my $pos_sites = $postProbs->{'pos_sites'};
    my $nps = scalar(@$pos_sites);
    my $pos_hash = {};
    map { $pos_hash->{$_->{'index'}} = $_; } @$pos_sites;
    
    # use black on white for regular sites, white on black to highlight
    # positively selected ones
    my $seqFg = sub { 
	my($index) = @_;
	return defined($pos_hash->{$index+1}) ? 'white' : 'black';
    };
    my $seqBg = sub {
	my($index) = @_;
	return defined($pos_hash->{$index+1}) ? 'black' : undef;
    };
    my $seqArgs = {
	'bg_color_fn' => $seqBg, 
	'fill_color_fn' => $seqFg, 
	'stroke_color_fn' => $seqFg,
	'draw_ticks' => $invert_y ? 'bottom' : 'top',
	'seq_fn' => sub {my $index = shift; return $siteProbs->[$index]->{'aa'};},
	'v_pad_fhm' => 0.35,
    };
    my $seqT = SeqGraph::Track::Sequence->new($seqArgs);
    
    # ---------------------------------
    # codeml site classes
    # ---------------------------------
    
    my $classFg = sub { return 'black'; };
    # background color indicates site class
    my $classBg = sub {
	my($index) = @_;
	my $class = $siteProbs->[$index]->{'class'};
	return $CODEML_CLASS_COLORS->[$class-1];
    };
    
    my $aaClassFn = sub {
	my($index) = @_;
	return $siteProbs->[$index]->{'class'};
    };
    
    my $classArgs = {
	'seq_fn' => $aaClassFn,
	'bg_color_fn' => $classBg, 
	'fill_color_fn' => $classFg, 
	'stroke_color_fn' => sub {undef;}, # $classFg,
	'font_h_fhm' => 0.6,
	'text_style' => 'font-weight: bold',
    };
    my $classT = SeqGraph::Track::Sequence->new($classArgs);
    my @tracks = ($ckT, $coordT, $barGraphT, $seqT, $classT);
#    my @tracks = ($coordT, $barGraphT, $seqT, $classT);
    @tracks = reverse @tracks if ($invert_y);
    
    return \@tracks;
}

__END__

=head1 BUGS

Please e-mail any bug reports to crabtree@tigr.org

=head1 SEE ALSO

=over 4

=item o
http://sybil.sourceforge.net

=back

=head1 AUTHOR(S)

 Jonathan Crabtree, Amy Egan
 The Institute for Genomic Research
 9712 Medical Center Drive
 Rockville, MD 20850

=head1 COPYRIGHT

Copyright (c) 2005, 2007, Jonathan Crabtree, Amy Egan and Joana C. Silva.
All Rights Reserved.

=cut
