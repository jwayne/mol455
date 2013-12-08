#!/usr/bin/perl
#BEGIN{foreach (@INC) {s/\/usr\/local\/packages/\/local\/platform/}};
#use lib (@INC,$ENV{"PERL_MOD_DIR"});
no lib "$ENV{PERL_MOD_DIR}/i686-linux";
no lib ".";

use warnings;
use Getopt::Long qw(:config no_ignore_case no_auto_abbrev);
use File::Copy;

my $usage = '
=head1  NAME

idea-B-run-paml.pl - Step 2 of IDEA: executes PAML

=head1 SYNOPSIS

USAGE: idea-B-run-paml.pl
        --input_prefix=geneName
        --input_path=/path/to/directory/containing/input/files/
        --temp_path=/path/to/store/temporary/output
        --output_path=/path/to/store/output
        --paml_bin_dir=/path/to/paml/bin 
	--omegas=0.1
	[--require_more_than_two]
        [--debug=4]

=head1 OPTIONS

B<--input_prefix,-p>
    The file prefix for the gene/cog to be analyzed.
    This might correspond to a gene name.
    It is used only in multi-dataset mode.

B<--seqfile>
    This is the name of the input sequence file.
    It is used only in single-dataset mode.
    It can be an absolute path or relative to --input_path.

B<--input_path,-i>
    The path to the directory containing required input files.

B<--temp_path,-t>
	The temporary path for execution steps. (eg: $;TEMP_DIR$;)
	
B<--output_path,-o>
	The path to the directory to store output.
	
B<--paml_bin_dir,-b>
	Full path containing PAML executables (specifically codeml and baseml).
	
B<--omegas,-w>
	dN/dS ratio estimate (w).
        Multiple values can be specified by separating them with commas.
        To run baseml (for which this parameter is unused), use -w STANDARD.

B<--user_tree,-u>
        Use the specific tree name (or extension for it) given in the control file.

B<--require_more_than_two,-3>
	Will not execute if the number of input sequences is less than three.
	
B<--debug,-d>
    Debug level.  Use a large number to turn on verbose debugging.

B<--log,-l>
    Log file

B<--help,-h>
    This help message

=head1   DESCRIPTION

This script runs PAML on the given gene, using the output of previous
steps.  For details of the method, see the documentation for
idea-text.

=head1 INPUT

This should be run as part of IDEA.  See DESCRIPTION.

=head1 OUTPUT

See DESCRIPTION.

=head1 CONTACT

    Original author:
    Joana Silva
    jcsilva@som.umaryland.edu

    Most recent modifications:
    Amy Egan
    aegan@som.umaryland.edu

=cut

=head1 COPYRIGHT

Copyright (c) 2006, Amy Egan and Joana C. Silva. 
All Rights Reserved.

=cut
';

my %options = ();
$options{'program'} = "codeml";
$options{"paml_bin_dir"} = "/usr/local/projects/aegan/paml44/bin";
$options{"input_prefix"} = "";
$options{"seqfile"} = "";

my $results = GetOptions (\%options,
			  'input_prefix|p=s',
			  'seqfile=s',
                          'input_path|i=s',
			  'temp_path|t=s',
                          'output_path|o=s',
			  'paml_bin_dir|b=s',
			  'omegas|w=s',
			  'user_tree|u=s',
			  'require_more_than_two|3',
			  'program|a=s',
			  'ctl_file|c=s',
                          'log|l=s',
                          'debug=s',
                          'help|h') || &usage(1);

## display documentation
if( $options{'help'} ){
    &usage(0);
}
unless ($options{"ctl_file"}){
    $options{"ctl_file"} = "$options{program}.ctl.template";
}
unless ($options{"input_prefix"} ^ $options{"seqfile"}){
    print "You must specify -p XOR --seqfile.\n";
    &usage(1);
}

my $pamlSeq;
my $geneName;

my $outgroup;
my $batchfile;
my @speciesNames;
my $noSpecs;
my $PAMLrunmode;

foreach my $dir((
		'paml_bin_dir', 
		 'temp_path',
		 'input_path',
		 'output_path', 
		 )) {
    if (! -d $options{$dir}) {
	die "'$dir' specified as '".$options{$dir}."' does not exist.\n";
    }
    unless ($options{$dir} =~ /\/$/) {
	$options{$dir} .= "/";
    }
}

# Set up the working directory.
$geneName = $options{'input_prefix'};
my $originalTempPath = $options{"temp_path"};
my $createdSubdirectories = 0;
if ($geneName && ($options{"temp_path"} eq $options{"output_path"})){
    $options{"temp_path"} = "$options{temp_path}${geneName}_dir/";
    unless (-d $options{"temp_path"}){
	system("mkdir $options{temp_path}")
	    and die "The directory $options{temp_path} could not be created.\n";
    }
    system("cp -p $originalTempPath/$options{ctl_file} $options{temp_path}")
	and die "$options{ctl_file} could not be copied to $options{temp_path} from $originalTempPath!\n";
    $createdSubdirectories = 1;
}
my $filenamePrefix = $createdSubdirectories ? "../" : "";
chdir($options{'temp_path'});
	
if ($geneName && ($geneName !~ /^\//)){
    if ((! (-e "$originalTempPath$geneName")) && (-e "$options{input_path}$geneName")){
	system("cp $options{input_path}$geneName $originalTempPath$geneName")
	    and die "$geneName could not be copied from $options{input_path} to $originalTempPath.";
    }
}
$pamlSeq =
    $geneName
    ? ((($geneName =~ /^\//) ? "" : "$originalTempPath") . $geneName)
    : ((($options{"seqfile"} =~ /^\//) ? "" : $options{"input_path"}) . $options{"seqfile"});


# Count the sequences in the sequence file.  The list of sequence names is written to the array @speciesNames.
@speciesNames = ("");
$noSpecs = 0;
$noSpecs = &numberOfSpecies($pamlSeq);   

# Set up some filename affixes.
undef(@omegas);
@omegas = split(/\,/, $options{"omegas"});
undef(%wString);
foreach $omega(@omegas){
    if ($omega =~ /^\./){
	$omega = "0$omega";
    }
    $wString = "w" . $omega;
    $wString =~ s/\./_/g;
    $wString{$omega} = $wString;
}
my $fileIdentifier = $geneName ? $geneName : "ONLY";

# Obtain the user-specified runmode.
$PAMLrunmode = &runMode(&ctlTemplateFilename());

# The analysis performed will be:
# * pairwise if there are two sequences, the user specified runmode = -2 and the program to be run is codeml
# * tree-based if there are three or more species, the program to be run is baseml, or the requested runmode is not -2
# A user-specified runmode of -2 is overridden with 0 if there are more than two sequences or the program is baseml.

# If a pairwise analysis is to be performed, create a codeml control file based on the template and the first omega
# value (all others are ignored) and overriding the user's entry for NSsites with 0.  Then run codeml.

# Otherwise, if a tree-based analysis is to be performed, create a codeml or baseml control file for each starting omega
# value and run codeml or baseml once with each.
if (($noSpecs > 2) || ($options{"program"} eq "baseml") || (($PAMLrunmode ne "none") && ($PAMLrunmode != -2))){
    # When running codeml with three or more species, calculations are tree-based.
    # 
    # Trees were either created manually (<= 3 species) 
    # or generated using PHYLIP or PAUP; either way, they have been
    # already created in the temporary directory.

    $PAMLrunmode = 0;  # This will only override runmode = -2, not other choices of runmode.

    foreach $omega(@omegas){
	# Run PAML with the specified starting omega value.
	&updateCtlFile ($filenamePrefix, $geneName, $omega, $PAMLrunmode, "DEFAULT", "DEFAULT");
	system($options{'paml_bin_dir'}.$options{"program"}); # run PAML (codeml or baseml)
	move($options{'temp_path'}."rst", $options{'output_path'}."$fileIdentifier.$wString{$omega}.rst");
	move($options{'temp_path'}."lnf", $options{'output_path'}."$fileIdentifier.$wString{$omega}.lnf");  
	move("$options{temp_path}$options{program}.ctl",
	     $options{'output_path'}."$fileIdentifier.$wString{$omega}.$options{program}.ctl");  
    }    
}
elsif (! defined($options{'require_more_than_two'})) {
    # Run PAML with a single starting omega value (the first specified).
    &updateCtlFile($filenamePrefix, $geneName, $omegas[0], "DEFAULT", 0, "DEFAULT");
    system($options{'paml_bin_dir'}."codeml"); # run PAML (codeml)
    move($options{'temp_path'}."rst", $options{'output_path'}."$fileIdentifier.$wString{$omegas[0]}.rst"); # nec. for 2 species?
    move($options{'temp_path'}."lnf", $options{'output_path'}."$fileIdentifier.$wString{$omegas[0]}.lnf");  # nec. for 2 species?
    move($options{'temp_path'}."codeml.ctl", $options{'output_path'}."$fileIdentifier.$wString{$omegas[0]}.codeml.ctl");  
}	
if ($createdSubdirectories){ # Delete the subdirectories we created.
    system("rm -rf $options{temp_path}");
}

## Additional clean-up has been moved to idea-B2-clean-up.pl.  It is only necessary when PAUP has been run.

# end main
#-------------------------------------------------------------------------------------
#-------------------------------------------------------------------------------------

# ctlTemplateFilename: returns the name of the control file template, either in the temporary or the input directory
# Input: none
# Output: the name of the control file template
sub ctlTemplateFilename{
    my $firstChoice = "$options{temp_path}$options{ctl_file}";
    my $secondChoice = "$options{input_path}$options{ctl_file}";
    if (open(AVAILABILITY_TEST, $firstChoice)){
	close(AVAILABILITY_TEST);
	return $firstChoice;
    }
    elsif (open(AVAILABILITY_TEST, $secondChoice)){
	close(AVAILABILITY_TEST);
	return $secondChoice;
    }
    else{
	print STDERR "Neigher $firstChoice nor $secondChoice could be opened.\n";
	exit(1);
    }
}

# updateCtlFile: creates a control file based on the template and a particular sequence and omega value
sub updateCtlFile{
    # In single-dataset mode, there will be no gene name.
    my ($prefix, $geneName, $omega, $runModeOverride, $NSsitesOverride, $ncatGOverride) = @_;
    
    my $defaultCTLFile = "$options{temp_path}$options{ctl_file}";
    my $alternateCTLFile = "$options{input_path}$options{ctl_file}";
    my $newCTLFile = "$options{temp_path}$options{program}.ctl";
    my $defaultTreeFilename = "$geneName.PAMLtree";
    my ($treefile, $explanation, $treeFilename, $seqfile, $outfile);
    
    unless (open(OLD, $defaultCTLFile)){
	unless (open(OLD, $alternateCTLFile)){
	    print STDERR "Neither $defaultCTLFile nor $alternateCTLFile could be opened.\n";
	    exit(1);
	}
    }
    unless (open(NEW, ">$newCTLFile")){ # FOR OUTPUT
	print STDERR "$newCTLFile could not be opened for output.\n";
	exit(1);
    }
    while ($line = <OLD>){
	if ($line =~ /seqfile\s*\=\s*(.*\S+)\s*\**(.*)/){
	    $seqfile = $1;
	    $explanation = $2;
	    if ($geneName){
		$seqfile = "$prefix$geneName";
	    }
	    else{
		$seqfile = (($seqfile =~ /^\//) ? "" : "$options{input_path}") . $seqfile;
	    }
	    print NEW "      seqfile = $seqfile\n";
	}
	elsif ($line =~ /treefile\s*\=\s*(.*\S+)\s*\**(.*)/){
	    $treefile = $1;
	    $explanation = $2;
	    if ($options{"user_tree"}){ # using user's tree
		$treeFilename =
		    (($options{"user_tree"} =~ /^\//) ? "" : "$options{input_path}") . $options{"user_tree"};
	    }
	    elsif ($geneName){ # multi-dataset mode, using tree created in step A
		$treeFilename = "$prefix$defaultTreeFilename";
	    }
	    else{ # single-dataset mode, using tree created in step A
		$treeFilename = (($treefile =~ /^\//) ? "" : "$prefix") . $treefile;
	    }
	    print NEW "     treefile = $treeFilename *$explanation\n";
	}
	elsif ($line =~ /outfile \= (.*\S+)\s+\**\s+(.*)/){
	    $outfile = $1;
	    $explanation = $2;
	    if ($geneName){ # multi-dataset mode
		$outfile = "$geneName.$wString{$omega}.PAMLout";
	    }
	    else{ # single-dataset mode
		$outfile = "$outfile.$wString{$omega}";
	    }
	    $outfile = (($outfile =~ /^\//) ? "" : "$prefix") . $outfile;
	    print NEW "      outfile = $outfile *$explanation\n";
	}
	elsif (($line =~ /runmode \= -2/) && ($runModeOverride ne "DEFAULT")){
	    # Override runmode = -2 (pairwise) if there are > 2 species.  Don't override runmode in any other case.
	    print NEW "      runmode = $runModeOverride\n"; # * 0: user tree;  -2: pairwise\n";
	}
	elsif (($line =~ /NSsites \=/) && ($NSsitesOverride ne "DEFAULT")){
	    print NEW "      NSsites = $NSsitesOverride\n";
	}
	elsif (($line =~ /[^_]omega \=/) && ($omega ne "STANDARD")){  # Skip omega when running baseml.
	    print NEW "        omega = $omega\n"; #* initial or fixed omega, for codons or codon-based AAs
	}
	elsif (($line =~ /ncatG \=/) && ($ncatGOverride ne "DEFAULT")){
	    print NEW "        ncatG = $ncatGOverride\n"; # * no. of categories in dG of NSsites models
	}
	else{
	    print NEW $line;
	}
    }
    close(OLD);
    close(NEW);
}

# ctlTemplateFilename: parses the runmode out of a control file
# Input: the name of a control file template
# Output: the runmode specified in that template, or "none" if it contains no runmode
sub runMode{
    my ($unmodifiedTemplateControlFilename) = @_;

    my $templateLine;

    open(TEMPLATE, $unmodifiedTemplateControlFilename)
	or die "$unmodifiedTemplateControlFilename could not be opened.\n";
    while ($templateLine = <TEMPLATE>){
	if ($templateLine =~ /runmode \= ([\d\.\-]+)/){
	    close(TEMPLATE);
	    return $1;
	}
    }
    close(TEMPLATE);
    return "none";
}

# numberOfSpecies: returns the number of species
# Input: PAMLseq file
# Output: number of species

sub numberOfSpecies{
  my ($seqfile) = @_;
  
  my $firstLine;
 
  open (IN, $seqfile)
      or die ("Can't open $seqfile");
  $firstLine = <IN>;
  close(IN);
  if ($firstLine =~/^\D*(\d+)\D+/){
      return $1;
  }
  return 0;
}

# usage: prints a usage message and exits
# Input: the return value to exit with
sub usage{

    my ($exitVal) = @_;

    $usage =~ s/=head1\s*//g;
    $usage =~ s/^B//gm;
    $usage =~ s/=cut\n//;
    print $usage;
    exit($exitVal);
}
