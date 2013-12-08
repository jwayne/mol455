#!/usr/bin/perl -w

use Getopt::Long qw(:config no_ignore_case no_auto_abbrev);
use IO::File;
use Pod::Usage;

my $usage = '
=head1  NAME

idea-C-merge-runs.pl - Step 3 of IDEA: merge PAML runs with different starting omega values

=head1 SYNOPSIS

USAGE: idea-C-merge-runs.pl [/directory/containing/files/to/be/merged]
        --dataset_name=geneName

=head1 OPTIONS

B<--dataset_name,-d>
    This is the file prefix for the gene/cog to be analyzed.
    It might correspond to a gene name.
    It is used only in multi-dataset mode.

B<--outfile>
    This is the common prefix for all PAML output files to be merged.
    It is used only in single-dataset mode.
    It can be an absolute path or relative to the output directory.

B<--debug>
    Debug level.  Use a large number to turn on verbose debugging.

B<--log,-l>
    Log file

B<--help,-h>
    This help message

=head1   DESCRIPTION

Step 3 of IDEA:
This script reads in multiple runs of PAML with different starting omega values on the
same dataset and merges the output files into a single file.  In multi-gene mode, this file
has the extension .mlc.  In single-gene mode, it has the extension .merged. 
Descriptive data should be identical in all outputs and is copied from the first file. 
Each tested model in (M0, M1, M2, M3, M7, M8) is compared across the runs and for each model,
the run with the best (least negative) lnL score is copied to the merged output file.
If no output directory is given, the current directory is assumed.


=head1 INPUT

This should be run as part of IDEA.  See DESCRIPTION.

=head1 OUTPUT

See DESCRIPTION.

=head1 CONTACT

    Maintainer:
    Amy Egan
    aegan@som.umaryland.edu

=cut

=head1 COPYRIGHT

Copyright (c) 2006, Amy Egan and Joana C. Silva. 
All Rights Reserved.

=cut
';

my $outputDirectory = `pwd`;
chomp($outputDirectory);
if (scalar(@ARGV)){
    $outputDirectory = shift(@ARGV);
}
my %options = ();

my $results = GetOptions (\%options,
			  'dataset_name|d=s',
			  'outfile=s',
                          'log|l=s',
                          'debug=s',
                          'help|h') || &usage(1);


## display documentation
if( $options{'help'} ){
    &usage(0);
}
foreach $option("dataset_name", "outfile"){
    if ((! defined($options{$option})) || (! exists($options{$option}))){
	$options{$option} = "";
    }
}
unless ($options{"dataset_name"} ^ $options{"outfile"}){
    print "You must specify -d XOR --outfile.\n";
    &usage(1);
}



my $geneName = $options{"dataset_name"};
print "$geneName\n";

if ($options{"outfile"} && ($options{"outfile"} !~ /^\//)){
    $options{"outfile"} = "$outputDirectory/$options{outfile}";
}
if ($options{"outfile"}){
    $outfile = "$options{outfile}.merged";
}
else{
    $outfile = "$outputDirectory/$geneName.mlc";
}
open (OUTFILE, ">$outfile") or die $!;

@outputFiles = $geneName ? `ls $outputDirectory/$geneName.*.PAMLout` : `ls $options{outfile}.w*`;
$noOutputFiles = @outputFiles; #no. of PAML output files for each COG
undef(%likelihoodForRunAndModel);
undef(%resultsForRunAndModel);

for (my $i = 0; $i < $noOutputFiles; $i++) {
    open (TEMPinfile, $outputFiles[$i]) or die $!;
    
    while ($line = <TEMPinfile>){
	if ($line =~ /^\s*Model (\d)/) {
	    $model = $1;
	    $runAndModel = "$i.$model";
	    $resultsForRunAndModel{$runAndModel} = $line;
	    last; 
	}
	elsif ($i == 0) {
	    print OUTFILE $line; # Use the first run's header.
	}
    }
    while ($line = <TEMPinfile>){
	if ($line =~ /^\s*Model (\d)/){
	    $model = $1;
	    $runAndModel = "$i.$model";
	    $resultsForRunAndModel{$runAndModel} = $line;
	}
	else{
	    $resultsForRunAndModel{$runAndModel}.= $line;
	    if ($line =~ /^lnL/){
		@lineSubparts = split(" ", $line);
		$likelihoodForRunAndModel{$runAndModel} = $lineSubparts[scalar(@lineSubparts) - 2];
	    }
	}
    }
    close(TEMPinfile);
}
undef(%runs);
undef(%models);
foreach $runAndModel(keys(%likelihoodForRunAndModel)){
    ($run, $model) = split(/\./, $runAndModel);
    $runs{$run} = 1;
    $models{$model} = 1;
}
foreach $model(sort {$a <=> $b} keys(%models)){
    $bestLnL = 0;
    $winningRun = "";
    foreach $run(keys(%runs)){
	if (($bestLnL == 0) || ($likelihoodForRunAndModel{"$run.$model"} > $bestLnL)){
	    $bestLnL = $likelihoodForRunAndModel{"$run.$model"};
	    $winningRun = $run;
	}
    }
    print OUTFILE $resultsForRunAndModel{"$winningRun.$model"};
}
close(OUTFILE);

# Now merge the RST files.
my $outfileDirectory;
if ($options{"outfile"}){
    $outfileDirectory = $options{"outfile"};
    $outfileDirectory =~ s/\/?[^\/]*$//;
    $outfile = "$outfileDirectory/ONLY.mst";
}
else{
    $outfile = "$outputDirectory/$geneName.mst";
}
my @rstFiles = $geneName ? `ls $outputDirectory/$geneName.*.rst` : `ls $outfileDirectory/*.rst`;

undef(@fileHandles);
undef(@fileContents);

open (MERGED_RST, ">$outfile") or die $!;
my $LOCAL_FILE_HANDLE;
foreach $rstFile(@rstFiles){
    undef($LOCAL_FILE_HANDLE);
    $LOCAL_FILE_HANDLE = IO::File->new();
    open($LOCAL_FILE_HANDLE, $rstFile) or die $!;
    push(@fileHandles, $LOCAL_FILE_HANDLE);
}
my $standardLine;
undef($LOCAL_FILE_HANDLE);
$LOCAL_FILE_HANDLE = $fileHandles[0];
while ($standardLine = <$LOCAL_FILE_HANDLE>){
    if ($standardLine =~ /^Model/){
	last;
    }
    foreach my $fileHandle(@fileHandles[1 .. $#fileHandles]){
	<$fileHandle>;
    }
    print MERGED_RST $standardLine;
}
foreach $rstFile(@rstFiles){
    push(@fileContents, 1);
}
my ($i, $bestResults, $bestLikelihoodScore, $likelihoodScore, $resultSet);
my @resultSet;
for ($i = 0; $i < scalar(@rstFiles); $i++){
    $fileContents[$i] = [&parseModels($fileHandles[$i], ($i == 0) ? $standardLine : "")];
}

if (scalar(@fileContents) > 0){
    for ($i = 0; $i < scalar(@{$fileContents[0]}); $i+= 2){
	$bestResults = "";
	$bestLikelihoodScore = 0;
	undef(@resultSet);
	foreach $resultSet(@fileContents){
	    @resultSet = @{$resultSet};
	    $likelihoodScore = $resultSet[$i];
	    if (($likelihoodScore > $bestLikelihoodScore) || (($bestLikelihoodScore == 0) && ($likelihoodScore != 0))){
		$bestLikelihoodScore = $likelihoodScore;
		$bestResults = $resultSet[$i + 1];
	    }
	}
	print MERGED_RST $bestResults;
    }
}
close(MERGED_RST);

sub parseModels{
    my ($fileHandle, $firstLine) = @_;

    my $likelihoodScore = 0;
    my $pamlOutput = $firstLine ? $firstLine : "";
    my ($outputLine, @outputForEachModel);
    undef(@outputForEachModel);

    while ($outputLine = <$fileHandle>){
	if ($outputLine =~ /^Model/){
	    if ($pamlOutput){
		push(@outputForEachModel, $likelihoodScore, $pamlOutput);
	    }
	    $pamlOutput = $outputLine;
	}
	else{
	    $pamlOutput.= $outputLine;
	    if ($outputLine =~ /^\s*lnL\s*=\s*(\-?[\d\.]+)/){
		$likelihoodScore = $1;
	    }
	}
    }
    close($fileHandle);
    push(@outputForEachModel, $likelihoodScore, $pamlOutput);
    return @outputForEachModel;
}

sub usage{

    my ($exitVal) = @_;

    $usage =~ s/=head1\s*//g;
    $usage =~ s/^B//gm;
    $usage =~ s/=cut\n//;
    print $usage;
    exit($exitVal);
}
