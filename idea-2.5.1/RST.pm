package RST;

=head1 NAME

RST.pm

=head1 SYNOPSIS

Module for parsing codeml .rst files.

=head1 DESCRIPTION

Module for parsing codeml .rst files (of the variety produced by IDEA.)

=cut

# ------------------------------------------------------------------
# Public methods
# ------------------------------------------------------------------

=head2 B<parseRstFile>

 Usage   : my $data = &parseRstFile({fh => $fh, filename => 'test.rst'});
 Function: Parse a codeml RST output file.
 Returns : A hashref with the following fields:
            codon_diffs
            models
 Args    : A hashref with the following fields:
            fh        - FileHandle of the RST file
            filename  - File name to use in error messages.
 
=cut

sub parseRstFile {
    my($args) = @_;
    my($fh, $fname) = map {$args->{$_}} ('fh', 'filename');

    # summary of codon usage differences among the sequences
    my $codon_diffs = [];

    # hashref of data for each model, indexed by model # (0,1,2,3, etc.)
    my $model_hash = {};

    # return value
    my $data = { 'codon_diffs' => $codon_diffs, 'models' => $model_hash };

    my $line = undef;
    my $line_num = 0;
    my $reread_line = 0; # set this flag to make $readLine return the same line twice

    # a few anonymous subs to make parsing easier:

    # read a line from <$fh>, incrementing $line_num
    my $readLine = sub {
	if ($reread_line) {
	    $reread_line = 0;
	    return $line;
	}

	$line = <$fh>;
	if ($line) {
	    chomp($line);
	    ++$line_num;
	}
    };

    # skip to the next non-blank line
    my $skipBlankLines = sub {
	while (&$readLine()) {
	    last if ($line =~ /\S/) && ($line !~ /^TREE/);  # This is a hack to ignore lines starting with "TREE".
	}
    };

    # Tries to match the current line against the specified regex.  Returns the
    # match variables if successful, complains/dies if not.
    my $matchLine = sub {
	my($regex, $noError) = @_;

	if (my(@matches) = ($line =~ /$regex/)) {
	    return @matches;
	} else {
	    return undef if ($noError);
	    die "line $line_num of $fname does not match regex '$regex': $line";
	}
    };

    # skip initial blank line(s)
    &$skipBlankLines();

    # ---------------------------------
    # CODON USAGE
    # ---------------------------------

    if (&$matchLine('^Supplemental results', 1)) {
	&$skipBlankLines();
    }

    # aegan:  I made this optional since it's not produced by PAML 4a.
    my $codonUsageFound = &$matchLine('^Number of codon sites with 0,1,2,3 position differences', 1);
    print STDERR "26: Codon usage found? [$codonUsageFound]\n";

    while ($codonUsageFound && &$readLine()) {
	#  e.g. "2 vs.    1     38    73    62    22  -1.0000 (0.4127 -1.0000)"
	if ($line =~ /^\s*(\d+) vs\.\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+([\d\.\-]+)\s+\(([\d\.\-]+)\s+([\d\.\-]+)\)$/) {
	    my($s1, # index of sequence A
	       $s2, # index of sequence B
	       $c0, # count of 0-position diffs
	       $c1, # count of 1-position diffs
	       $c2, # count of 2-position diffs
	       $c3, # count of 3-position diffs
	       $f1, # ? looks like -ln of something...
	       $f2, # ?
	       $f3, # ?
	       ) = ($1, $2, $3, $4, $5, $6);

	    push(@$codon_diffs,{'a'=>$s1,'b'=>$s2,'c0'=>$c0,'c1'=>$c1,'c2'=>$c2,'c3'=>$c3,'f1'=>$f1,'f2'=>$f2,'f3'=>$f3});
	} 
	elsif ($line =~ /^\s*$/) {
	    last;
	} else {
	    die "unexpected value at line $line_num of $fname: $line";
	}
    }
    
    # TODO - check that the number of codon_diffs agrees with number of aligned seqs

    # ---------------------------------
    # PER-SITE MODEL PROBS
    # ---------------------------------

    while (&$readLine) {
	print STDERR "26: Now processing [$line]...\n";
	# section describing a model
	if ($line =~ /^Model (\d+):(.*)$/) {
	    my($model_num, $model_descr) = ($1, $2);
	    if ($model_num == 0){
		next;
	    }
	    &$skipBlankLines();

	    # blank model section
	    if ($line =~ /^Model \d+/) {
		$reread_line = 1;
		next;
	    }

	    # dN/dS for site classes
	    
	    # aegan EDITED
#	    my $num_classes;
#	    while (&$readLine()){
	    if (($line =~ /^dN\/dS for site classes \(K=(\d+)\)$/)
		|| ($line =~ /^dN\/dS \(w\) for site classes \(K=(\d+)\)$/)){
		    #$num_classes = &$matchLine('^dN\/dS for site classes \(K=(\d+)\)$');
		$num_classes = $1;
#		    last;
	    }
#	    }
#	    my $num_classes = ($model_num == 0) ? 1 : &$matchLine('^dN\/dS for site classes \(K=(\d|)\)$');

	    # there might be some blank lines here, and there might not
	    &$readLine();
	    &$skipBlankLines() if (&$matchLine('^\s*$', 1));
		
	    # list of p values, one for each site class
	    my($ps) = &$matchLine('^p:\s+(.*)\s*$');
	    my @pvals = split(/\s+/, $ps);
	    die "wrong number of p values (expected $num_classes) at line $line_num of $fname" if (scalar(@pvals) != $num_classes);

	    # list of w values, one for each site class
	    &$readLine();
	    my($ws) = &$matchLine('^w:\s+(.*)\s*$');
	    my @wvals = split(/\s+/, $ws);

	    # aegan PAML can fail to separate the w-values in cases of w >= 100.  Force separation.
	    my ($wval);
	    my (@adjustedWVals);
	    foreach $wval(@wvals){
		while ($wval =~ /\..*\./){
		    $wval =~ s/^(\d+)\.(\d{5})(\d{3}\.)/$3/
			or die "cannot correct for PAML error at line $line of $fname";
		    push(@adjustedWVals, "$1.$2");
		}
		push(@adjustedWVals, $wval);
	    }
	    @wvals = @adjustedWVals;
	    die "wrong number of w values at line $line_num of $fname" if (scalar(@wvals) != $num_classes);

	    &$skipBlankLines();

	    # ---------------------------------
	    # POSTERIOR PROBABILITIES
	    # ---------------------------------

	    # could have multiple sets of probabilities here
	    # e.g. Naive Empirical Bayes (NB) and Bayes Empirical Bayes (BEB) for model 2

	    while (1) {
		my($probSetName, $nc) = &$matchLine('^(.*(?:P|probabilities)) for (\d+) classes');
		die "number of classes ($nc) at line $line_num of $fname does not match expected ($num_classes)" if ($nc != $num_classes);

		&$readLine();
		my($ref) = &$matchLine('^(.*) used as reference', "noError") or &$matchLine('\(amino acids refer to 1st sequence: (.*)\)');
		
		# blank line
		&$readLine(); 
		&$matchLine('^\s*$');
		
		# site-specific posterior probabilities
		my $count = 1;
		my $site_probs = [];
		
		while (&$readLine) {
		    if (my(@matches) = ($line =~ /^\s*(\d+) (\S)\s+(\S.*) \(\s*(\d+)\)\s+([\d\.]+)(:?\s+(\+\-\s+)?(\-?[\d\.]+))?$/)) {
			my($index, $aa, $class_probs, $class, $f1, $plusminus, $f2) = @matches;
			die "unexpected aa index at line $line_num of $fname" if ($index != $count++);
			my @probs = split(/\s+/, $class_probs);
			my $np = scalar(@probs);
			die "wrong number ($np) of site probabilities at line $line_num of $fname" if ($np != $num_classes);
			
			push(@$site_probs, {
			    'index' => $index,
			    'aa' => $aa,
			    'probs' => \@probs,,
			    'class' => $class,
			    'f1' => $f1, # TODO - find out what $f1 and $f2 are and why only some models have $f2
			    'f2' => $f2,
			    'plusminus' => ($plusminus =~ /\S/) ? '1' : '0',
			});
		    }
		    elsif ($line =~ /^\s*$/) {
			last;
		    }
		    else {
			die "unexpected value parsing posterior probs at line $line_num of $fname: '$line'";
		    }
		}
		
		&$skipBlankLines();
		
		# ---------------------------------
		# POSITIVELY SELECTED SITES
		# ---------------------------------
		
		my $pos_sites = [];
		
		if ($line =~ /^positively selected sites/i) {
		    &$skipBlankLines();
		    
		    # no positively-selected sites
		    if ($line =~ /^\s+Prob\(w\>1\)\s+mean\s+w$/) { &$skipBlankLines(); };
		    if (($line !~ /^lnL =/) && ($line !~ /Ancestral reconstruction/)){ #aegan  I added the second clause.
			while (1) {
			    my($site_num, $aa, $probw, $meanw) = &$matchLine('^\s*(\d+) (\S)\s+([\d\.]+)\**(\s*|\s+\S.*)?$');
			    $meanw = undef if ($meanw =~ /^\s*$/);
			    push(@$pos_sites, {'index' => $site_num,
					       'aa' => $aa, 
					       'prob_w_gt_1' => $probw, 
					       'mean_w' => $meanw
					       });
			    
			    &$readLine();
			    if (!defined($line) || ($line =~ /^\s*$/)) {
				&$skipBlankLines();
				last;
			    }
			}
		    }
		}
		
		# ---------------------------------
		# NEGATIVE LOG LIKELIHOOD
		# ---------------------------------
		
		# this line is optional
		my($lnl) = &$matchLine('^lnL = (\-?[\d\.]+)', 1);
		
		# ---------------------------------
		# End of posterior probs
		# ---------------------------------
		
		my $model = $model_hash->{$model_num};

		# this is the 2nd or subsequent set of posterior probs - append probs to existing model
		if (defined($model)) {
		    if (defined($model->{'posterior_probs'}->{$probSetName})) {
			die "Multiple sets of posterior probabilities for '$probSetName' in model $model_num";
		    } else {
			$model->{'posterior_probs'}->{$probSetName} = 
			{
			    'ref_seq' => $ref,
			    'site_probs' => $site_probs,
			    'pos_sites' => $pos_sites,
			    'lnl' => $lnl,
			};
		    }
		} 
		# this is the first set of posterior probs - create model
		else {
		    $model = $model_hash->{$model_num} = {
			'model_num' => $model_num,
			'num_classes' => $num_classes,
			'p_values' => \@pvals,
			'w_values' => \@wvals,
			'posterior_probs' => {
			    $probSetName => {
				'ref_seq' => $ref,
				'site_probs' => $site_probs,
				'pos_sites' => $pos_sites,
				'lnl' => $lnl,
			    },
			},
		    };
		}

		# reached end of model?
		
		&$skipBlankLines() unless &$matchLine('^Model', 1);

		# another set of posterior probabilities
		if (&$matchLine('(P|probabilities) for (\d+) classes', 1)) {
		    # no-op
		} 
		# no more posterior probabilities
		else {
		    $reread_line = 1;
		    last;
		}
	    }
	} # end of Model

	# skip whitespace
	elsif ($line =~ /^\s*$/) {
	    next;
	}
	else {
	    print STDERR "unexpected value at line $line_num of $fname: '$line'\n";  # Hack:  Don't die here.
	}
    }

    return $data;
}

=head2 B<printRstFile>

 Usage   : &printRstFile({fh => $outfh, data => $data});
 Function: Generates an RST flat file from the data structure produced by &parseRstFile.
 Returns : Nothing
 Args    : A hashref with the following fields:
            fh        - FileHandle to write to
            data      - Data structure returned by &parseRstFile
 
=cut

sub printRstFile {
    my($args) = @_;
    my($fh, $data) = map {$args->{$_}} ('fh', 'data');

    my $codon_diffs = $data->{'codon_diffs'};
    print "\nNumber of codon sites with 0,1,2,3 position differences\n";
    foreach my $cd (@$codon_diffs) {
    }

    # TODO - not yet implemented

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

 Jonathan Crabtree, Amy Egan
 The Institute for Genomic Research
 9712 Medical Center Drive
 Rockville, MD 20850

=head1 COPYRIGHT

Copyright (c) 2005, 2007, Jonathan Crabtree, Amy Egan and Joana C. Silva.
All Rights Reserved.

=cut

