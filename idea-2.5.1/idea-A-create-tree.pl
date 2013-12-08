#!/usr/bin/perl
#BEGIN{foreach (@INC) {s/\/usr\/local\/packages/\/local\/platform/}};
#use lib (@INC,$ENV{"PERL_MOD_DIR"});
no lib "$ENV{PERL_MOD_DIR}/i686-linux";
no lib ".";

use strict;
use warnings;
use Getopt::Long qw(:config no_ignore_case no_auto_abbrev);
use Pod::Usage;
use File::Copy;

my $usage='
=head1  NAME

idea-A-create-tree.pl - Step 1 of IDEA:  This creates a tree file using PHYLIP.

=head1 SYNOPSIS

USAGE: idea-A-create-tree.pl
        --input_prefix=geneName
        --input_path=/path/to/directory/containing/input/files/
        --temp_path=/path/to/store/temporary/output
        --output_path=/path/to/store/output
        --debug=4

=head1 OPTIONS

B<--input_prefix,-p>
    The file prefix for the gene/cog to be analyzed.
    This might correspond to a gene name.
    It is used only in multi-dataset mode.

B<--seqfile>
    This is the name of the input sequence file.
    It is used only in single-dataset mode.
    It can be an absolute or relative path.
    If it is given as a relative path,
    --temp_path is searched first, followed by --input_path.

B<--treefile>
    This is the name of the output tree file.
    It is used only in single-dataset mode.
    It can be an absolute path or relative to --output_path.

B<--input_path,-i>
    The path to the directory containing required input files.

B<--temp_path,-t>
	The temporary path for execution steps. (eg: $;TEMP_DIR$;)
	
B<--output_path,-o>
	The path to the directory to store output.
	
B<--debug>
    Debug level.  Use a large number to turn on verbose debugging.

B<--log,-l>
    Log file

B<--help,-h>
    Print this help message.

=head1   DESCRIPTION

This script uses PHYLIP to create a phylogenetic tree from a nucleotide
alignment.  This is the first step in the IDEA process.

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

Copyright (c) 2006, Jonathan Badger, Amy Egan and Joana C. Silva. 
All Rights Reserved.

=cut
';

my %options = ();

$options{"input_prefix"} = "";
$options{"seqfile"} = "";
$options{"treefile"} = "";

my $results = GetOptions (\%options,
			  'input_prefix|p=s',
			  'seqfile=s',
			  'treefile=s',
                          'input_path|i=s',
			  'temp_path|t=s',
                          'output_path|o=s',
			  'phyml|m',
                          'log|l=s',
                          'debug=s',
                          'help|h') || &usage(1);

## display documentation
if( $options{'help'} ){
    &usage(0);
}
unless ($options{"input_prefix"} ^ ($options{"seqfile"} && $options{"treefile"})){
    print "You must specify -p XOR (--seqfile AND --treefile).\n";
    &usage(1);
}
if ($options{"input_prefix"} && ($options{"seqfile"} || $options{"treefile"})){
    print "You must specify -p XOR (--seqfile AND --treefile).\n";
    &usage(1);
}

my $aaAlign;
my $nuclSeqs;
my $pamlSeq;
my $geneName;

my $outgroup;
my $batchfile;
my @speciesNames;
my $noSpecs;
my $nuclAlignLength;
my $PAMLrunmode;

my $err;

my $phyMLBinary = '/usr/local/projects/aegan/PhyML_3.0/PhyML_3.0_linux64';

foreach my $dir((
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

chdir($options{'temp_path'});
	
$geneName = $options{'input_prefix'};

$pamlSeq = $geneName ? $geneName : $options{"seqfile"};

$outgroup = "";
$batchfile = "";
@speciesNames = ("");
$noSpecs = 0;

if ($pamlSeq !~ /^\//){
    if ((! (-e "$options{temp_path}$pamlSeq")) && (-e "$options{input_path}$pamlSeq")){
	system("cp $options{input_path}$pamlSeq $options{temp_path}$pamlSeq")
	    and die "$pamlSeq could not be copied from $options{input_path} to $options{temp_path}.";
    }
    $pamlSeq = "$options{temp_path}$pamlSeq";
}
$noSpecs = &readSpeciesNames($pamlSeq);   #seq names are flanked by single quotes
#list of seq names is written to array @speciesNames

$nuclAlignLength = &getAlignmentLength($pamlSeq);


my $treeOutFilename = $geneName ? "$geneName.PAMLtree" : $options{"treefile"};
if ($treeOutFilename !~ /^\//){
    $treeOutFilename = "$options{output_path}$treeOutFilename";
}
unless ($geneName){
    $geneName = "ONLY";
}
if ($noSpecs > 3) {
    if ($options{"phyml"}){
	&runPhyML($pamlSeq, $treeOutFilename, $noSpecs);
    }
    else{
	# Run PHYLIP to create a tree stored in $geneName.PAMLtree.
	&makeTreeFile("$geneName", $pamlSeq, $noSpecs, $treeOutFilename);
    }
} else {
    $" = ",";
    open (TREE_OUT, ">$treeOutFilename")
	or die "$treeOutFilename could not be opened for output.\n";
    my $speciesNames = join(",", @speciesNames);
    $speciesNames =~ s/^\,//;
    my $treeString = "($speciesNames);\n";
    printf TREE_OUT (" %d %d\n%s",$noSpecs, 1, $treeString);
    close(TREE_OUT);
}

# end main
#-------------------------------------------------------------------------------------
#-------------------------------------------------------------------------------------

sub makeTreeFile{
    my ($geneName, $nucAlign, $noSpecs, $treeOutFilename) = @_;

    my %alias = &makePhylipAlign($nucAlign, "$options{temp_path}/$geneName");
    my $tree = "";
    if ($noSpecs < 7) { # use parsimony if fewer than 7 taxa
      $tree = &runDNAPenny("$options{temp_path}$geneName.phy");
    }
    else { # otherwise, use NJ
      $tree = &runNeighbor("$options{temp_path}$geneName.phy");
    }
    $tree = &unAlias($tree, %alias);
    printf("Unrooted Tree: $tree\n");
    open(TREE_OUT, ">$treeOutFilename") # FOR OUTPUT
	or die "$treeOutFilename could not be opened for output.\n";
    printf TREE_OUT (" %d %d\n%s",$noSpecs, 1, $tree);
    close(TREE_OUT);
}


# readSpeciesNames: fills in list of species names and returns number of species
# Input: PAML sequence file (gapped FASTA file with header)
# Output: number of species
# Modified: speciesNames (the global list of species names)

sub readSpeciesNames{
  my ($aln) = @_;
  
  my $numberOfSpecies = 0;
  my $name;

  open (IN, $aln) || die ("Can't open $aln");
  while(<IN>) {
      if ($_ =~/^\>(\S+)/){
	  $name = $1;
	  $numberOfSpecies++;  # This could be changed to read the number of species from the header.
	  push(@speciesNames, $name);
      }
  }
  close(IN);
  return $numberOfSpecies;
}

sub usage{

    my ($exitVal) = @_;

    $usage =~ s/=head1\s*//g;
    $usage =~ s/^B//gm;
    $usage =~ s/=cut\n//;
    print $usage;
    exit($exitVal);
}

sub getAlignmentLength {
    my ($aln) = @_;
    
    my $len = 0;
    my $sequencesSeen = 0;
    my $line;
    
    open (IN, $aln) || die ("Can't open $aln");
    while($line = <IN>){
	chomp($line);
	if ($line =~ /^\>/){
	    $sequencesSeen++;
	    if ($sequencesSeen > 1){
		last;
	    }
	}
	else{
	    $line =~ s/\s//g;
	    $len+= length($line);
	}
    }
    close(IN);
    return $len;
}

sub runPhyML{
    
    my ($pamlSeq, $treeOutFilename, $noSpecs) = @_;

    my $phyMLBinaryLocal = &findPHYML();
    my $phyMLOutFilename = "${pamlSeq}_phyml_tree.txt";
    my ($line, $machine);

    unless ($phyMLBinaryLocal){
	$phyMLBinaryLocal = $phyMLBinary;
    }
    unless ($phyMLBinaryLocal){
	$machine = `hostname`;
	chomp($machine);
	unless ($machine){
	    $machine = "[unknown machine]";
	}
	print STDERR "PhyML is not installed on $machine.\n";
	exit(1);
    }
    if (system("$phyMLBinaryLocal -i $pamlSeq -d nt -q -n 2 -f \"fA fC fG fT\" --quiet")){
	print STDERR "error running $phyMLBinaryLocal\n";
	exit(1);
    }
    unless (open(INTERMEDIATE_OUTPUT, $phyMLOutFilename)){
	print STDERR "$phyMLOutFilename could not be opened.\n";
	exit(1);
    }
    unless (open(TREE_OUT, ">$treeOutFilename")){ # FOR OUTPUT
	print STDERR "$treeOutFilename could not be opened for output.\n";
	exit(1);
    }
    printf TREE_OUT (" %d %d\n",$noSpecs, 1);
    while ($line = <INTERMEDIATE_OUTPUT>){
	$line =~ s/\>//g;
	$line =~ s/\:[\d\.]+//g;
	while ($line =~ /[\(\)\,]([\d\.]+)[\(\)\,]/){
	    $line =~ s/$1//;
	}
	print TREE_OUT $line;
    }
    close(INTERMEDIATE_OUTPUT);
    close(TREE_OUT);
}
	
sub findPHYML{

    my $unameString = `uname -sm`; 
    
    chomp($unameString);
    
    $ENV{"PATH"} = "$ENV{PATH}:/usr/local/packages/PhyML";

    if ($unameString =~ /Linux/){
	if ($unameString =~ /32$/){
	    return &findProgram("PhyML_3.0_linux32");
	}
	elsif ($unameString =~ /64$/){
	    return &findProgram("PhyML_3.0_linux64");
	}
	else{
	    return "";
	}
    }
    elsif ($unameString =~ /CYGWIN/){
	return &findProgram("PhyML_3.0_win32.exe");
    }
    elsif ($unameString =~ /Darwin/){
	return &findProgram("PhyML_3.0_macOS");
    }
    else{
	return "";  # PhyML is not supported on Solaris.
    }
}

sub findProgram{
    my ($programName) = @_;

    my $pathToExecutable = `which $programName | grep -v "^no $programName"`;

    chomp($pathToExecutable);
    return $pathToExecutable;
}

# *************************************
# *      JHB added subroutines        *
# *************************************


# makePhylipAlign: convert a gapped FASTA alignment to an aliased PHYLIP one
# Input: nucleotide alignment in gapped FASTA format
# Output: alias hash of sequence names
# Effects: writes .phy file with alignment in PHYLIP format

sub makePhylipAlign {
  my ($nucAlign, $name) = @_;
#  my $name = substr($nucAlign, 0, index($nucAlign,"."));
  open (IN, $nucAlign) || die ("Can't open $nucAlign");
  <IN>;
  my %alias;
  my %seqData;
  my $seqName = "";
  my $aliasName = "seq0001";
  my $count = 0;
  while(<IN>) {
    chomp($_);
    if ($_=~/>(.*)/) {
      $seqName = $1;
      $alias{$seqName} = $aliasName;
      $aliasName++;
      $seqData{$seqName} = "";
      $count++;
    }
    else {
      $seqData{$seqName} .= $_;
    }
  }
  open (PHY,">$name.phy") || die ("Can't create $name.phy");
  printf PHY ("%d %d\n", $count, length($seqData{$seqName}));
  for(my $i=0; $i < length($seqData{$seqName}); $i+=60) {
    foreach $seqName (sort {$alias{$a} cmp $alias{$b}} keys %seqData) {
      my $subSeq = substr($seqData{$seqName}, $i, 60);
      if ($i == 0) {
	printf PHY ("%-10s %s\n", $alias{$seqName}, $subSeq); 
      }
      else {
	printf PHY ("%s\n", $subSeq);
      }
    }
    printf PHY ("\n");
  }
  close(PHY);
  return %alias;
}


# runDNAPenny: infer a branch & bound DNA parsimony tree with PHYLIP
# Input: PHYLIP alignment
# Output: string containing unrooted tree

sub runDNAPenny {
  my ($phy) = @_;
  my $tree = "";
  #my $dnapenny = `which dnapenny`;
  my $dnapenny = "/usr/local/packages/phylip-3.67/bin/dnapenny";
  if ($dnapenny eq "") {
    print ("You don't have dnapenny on your path. Cannot infer tree\n");
    exit(1);
  }
#  my $retree = `which retree`;
  my $retree = "/usr/local/packages/phylip-3.67/bin/retree";
  if ($retree eq "") {
    print ("You don't have retree on your path. Cannot unroot tree\n");
    exit(1);
  }
  my $name = substr($phy, 0, rindex($phy,"."));
  system("rm -rf $phy.dir");
  system("mkdir $phy.dir");
  system("cp $phy $phy.dir/infile");
  system("cd $phy.dir; echo 'Y\n' | $dnapenny");
  system("cd $phy.dir; mv outtree intree");
  system("cd $phy.dir; rm outfile");
  system("cd $phy.dir; echo 'Y\nW\nU\nQ\n' | $retree");
  open(TREE, "$phy.dir/outtree");
  read(TREE, $tree, 100000);
  close(TREE);
  #system("rm -rf $phy.dir");
  system("echo '\n\n'");
  return $tree;
}

# runNeighbor: infer a DNA NJ tree with PHYLIP
# Input: PHYLIP alignment
# Output: string containing unrooted tree

sub runNeighbor {
  my ($phy) = @_;
  my $tree = "";
  my $neighbor = `which neighbor`;
  my $dnadist = `which dnadist`;
  if ($neighbor eq "") {
    print ("You don't have neighbor on your path. Cannot infer tree\n");
    exit(1);
  }
  if ($dnadist eq "") {
    print ("You don't have dnadist on your path. Cannot infer tree\n");
    exit(1);
  }
  my $retree = `which retree`;
  if ($retree eq "") {
    print ("You don't have retree on your path. Cannot unroot tree\n");
    exit(1);
  }
  my $name = substr($phy, 0, rindex($phy,"."));
  system("rm -rf $phy.dir");
  system("mkdir $phy.dir");
  system("cp $phy $phy.dir/infile");
  system("cd $phy.dir; echo 'Y\n' | $dnadist");
  system("cd $phy.dir; mv outfile infile");
  system("cd $phy.dir; echo 'Y\n' | $neighbor");
  system("cd $phy.dir; mv outtree intree");
  system("cd $phy.dir; rm outfile");
  system("cd $phy.dir; echo 'Y\nW\nU\nQ\n' | $retree");
  open(TREE, "$phy.dir/outtree");
  read(TREE, $tree, 100000);
  close(TREE);
  system("rm -rf $phy.dir");
  system("echo '\n\n'");
  return $tree;
}



# unAlias: unalias a Newick tree string
# Input: Newick tree string, alias hash
# Output: unaliased Newick tree string

sub unAlias {
  my ($tree, %alias) = @_;
  foreach my $key (keys %alias) {
    $tree =~ s/$alias{$key}/$key/;
  }
  return $tree;
}

