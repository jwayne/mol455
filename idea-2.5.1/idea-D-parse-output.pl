#!/usr/bin/perl
#
=head1 COPYRIGHT

Copyright (c) 2006, Amy Egan and Joana C. Silva. 
All Rights Reserved.

=cut

# Step 4 of IDEA:  This converts a .mlc or .merged file created for a
# gene/dataset in step 3 into a summary file with extension .summary.

unless ((scalar(@ARGV) == 2) || (scalar(@ARGV) == 3)){
    die "Usage: idea-D-parse-output.pl <input filename> <output filename> [<LRT output filename>]\n";
}
($inputFilename, $outputFilename, $lrtFilename) = @ARGV;
open(INPUT, $inputFilename)
    or die "$inputFilename could not be opened.\n";
open(OUTPUT, ">$outputFilename") # FOR OUTPUT
    or die "$outputFilename could not be opened for output.\n";
if ($lrtFilename){
    open(LRT, ">$lrtFilename") # FOR OUTPUT
	or die "$lrtFilename could not be opened for output.\n";
}

$pamlBinDir = "/usr/local/packages/paml-4a/bin";
$modelName = "";
undef(%likelihood);
while ($line = <INPUT>){
    chomp($line);
    if ($line =~ /Printing out site pattern counts/){
	$nextLine = <INPUT>;
	chomp($nextLine);
	while (! $nextLine){
	    $nextLine = <INPUT>;
	    chomp($nextLine);
	}
	($numberOfSequences, undef, undef) = split(" ", $nextLine);
	$numberOfSequences = &pad($numberOfSequences, 2, 1);
	print OUTPUT "$inputFilename\n# seqs in dataset:  $numberOfSequences\n\n";
    }
    if ($line =~ /Model (\d+): (\S+)/){
	if ($modelName){
	    print OUTPUT "model-name          \tlikelihood-score\ttree-length\t";
	    print OUTPUT "kappa   \tdN/dS\n";
	    print OUTPUT "$modelName\t$logLikelihood\t$treeLength\t";
	    print OUTPUT "$kappa\t$omega\n\n";
	    print OUTPUT "$siteClassArea\n\n";
	    $likelihood{substr($modelName, 0, index($modelName, " "))} =
		substr($logLikelihood, 0, index($logLikelihood, " "));
	}
	$modelName = "$1-$2";
	$modelName = &pad($modelName, 20, 0);
	$logLikelihood = "";
	$treeLength = "";
	$kappa = "";
	$alternateOmega = "";
	$siteClassArea = "";
	$omega = "";
    }
    if ($line =~ /lnL\(.*\)\:\s+(\S+)\s/){
	$logLikelihood = $1;
	$logLikelihood = &pad($logLikelihood, 16, 0);
    }
    if ($line =~ /tree length\s+\=\s+(\S+)$/){
	$treeLength = $1;
	$treeLength = &pad($treeLength, 11, 0);
    }
    if ($line =~ /kappa \(ts\/tv\)\s+\=\s+(\S+)$/){
	$kappa = $1;
	$kappa = &pad($kappa, 8, 0);
    }
    if ($line =~/dN\/dS for site classes/){
	$siteClassArea = "$line\n";
	for ($i = 0; $i < 3; $i++){
	    $siteClassArea.= <INPUT>;
	}
    }
    if ($line =~ /dN \& dS for each branch/){
	for ($i = 0; $i < 3; $i++){
	    <INPUT>;
	}
	$nextLine = <INPUT>;
	chomp($nextLine);
	@subparts = split(" ", $nextLine);
	$omega = $subparts[4];
	$omega = &pad($omega, 5, 0);
    }
}
close(INPUT);
print OUTPUT "model-name          \tlikelihood-score\ttree-length\t";
print OUTPUT "kappa   \tdN/ds\n";
print OUTPUT "$modelName\t$logLikelihood\t$treeLength\t";
print OUTPUT "$kappa\t$omega\n\n";
print OUTPUT "$siteClassArea\n\n\n\n\n";
$likelihood{substr($modelName, 0, index($modelName, " "))} = substr($logLikelihood, 0, index($logLikelihood, " "));
close(OUTPUT);
if ($inputFilename =~ /\.merged$/){
    $datasetName = "ONLY";
}
else{
    $datasetName = $outputFilename;
    $datasetName =~ s/.+\///g;
    $datasetName =~ s/\.summary$//;
}
$printedHeader = 0;
$headerString = &pad("Dataset", length($datasetName)) . "\t" . &pad("Models", 9) . "\t". &pad("lnL(H1)", 12) . "\t"
    . &pad("lnL(H0)", 12) . "\t2 * [lnL(H1) - lnL(H0)]\t" . &pad("P-Value", 11) . "\tSignificant?\n";
@alternativeModels = ("2-PositiveSelection", '8-beta&w>1');
@nullModels = ("1-NearlyNeutral", "7-beta");
foreach $alternativeModel(@alternativeModels){
    $nullModel = shift(@nullModels);
    if (exists($likelihood{$alternativeModel}) && exists($likelihood{$nullModel})){
	$alternativeModelNumber = substr($alternativeModel, 0, 1);
	$nullModelNumber = substr($nullModel, 0, 1);
	unless ($printedHeader){
	    print LRT $headerString;
	    $printedHeader = 1;
	}
	$testStatistic = 2 * ($likelihood{$alternativeModel} - $likelihood{$nullModel});
	if (($testStatistic < 0) && ($testStatistic > -0.01)){
	    $testStatistic = 0;  # Correct floating-point errors.
	}
	$testStatistic = sprintf("%.6f", $testStatistic);
	$lrtTempFilename = "$lrtFilename.$alternativeModelNumber.$nullModelNumber";
	open(TEMP, ">$lrtTempFilename") # FOR OUTPUT
	    or die "The temporary file $lrtTempFilename could not be opened for output.\n";
	print TEMP "2 $testStatistic\n0 -1\n";  # d.f. = 2 for both pairs of models.
	close(TEMP);
	$pValue = `$pamlBinDir/chi2 exact < $lrtTempFilename | head -n 3 | tail -1`;
	chomp($pValue);
	if ($pValue =~ /prob\s*\=\s*([\d\.]+)\s/){
	    $pValue = sprintf("%.9f", $1);
	    $significant = ($pValue < .05) ? "Yes" : "No";
	}
	else{
	    print STDERR "Malformatted output from chi2:\n$pValue\n";
	    $commandRun = "$pamlBinDir/chi2 exact < $lrtTempFilename | head -n 3 | tail -1";
	    print STDERR "Command run:  [$commandRun]\n";
	    exit(1);
	}
	print LRT "$datasetName\tM$alternativeModelNumber vs. M$nullModelNumber\t$likelihood{$alternativeModel}\t";
	print LRT "$likelihood{$nullModel}\t";
	print LRT &pad($testStatistic, 23);
	print LRT "\t$pValue\t$significant\n";
	system("rm -f $lrtTempFilename");  # Don't take any corrective action if this fails.
    }
}
unless ($printedHeader){
    $emptyExplanation = "No likelihood-ratio tests could be performed based on the model(s) you selected.\n";
    print STDERR $emptyExplanation;
    print LRT $emptyExplanation;
}
close(LRT);

sub pad{
    my ($string, $minLength, $leftSide) = @_;

    while (length($string) < $minLength){
	if ($padLeftSide){
	    $string = " $string";
	}
	else{
	    $string = "$string ";
	}
    }
    return $string;
}
