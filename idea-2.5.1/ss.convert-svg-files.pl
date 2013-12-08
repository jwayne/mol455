#!/usr/bin/perl

=head1 NAME

ss.convert-svg-files.pl - Run the apache batik rasterizer on one or more SVG files.

=head1 SYNOPSIS

ss.convert-svg-files.pl
      --svg_files=input.svg
      --format=jpg
    [ --svg_file_regex='.*' ]
    [ --quality=0.85 ]
    [ --output_height=1000 ]
    [ --output_width=1000 ]
    [ --force ]
    [ --verbose ]
    [ --help ]
    [ --man ]

=head1 PARAMETERS

--svg_files
    The SVG file(s) to convert.  This may either be a single SVG file or a directory
    that contains one or more SVG files.  The option --svg_file_regex will be
    used to determine which files should be converted.  The input SVG files may be 
    gzipped, but if and only if the file names end in either ".gz" or ".svgz".

--svg_file_regex
    An optional regular expression used to filter the set of SVG files to convert,
    in the case where --svg_files identifies a directory.

--format
    The output format: either 'jpg', 'png', or 'pdf'

--quality
    Output quality - a number between 0 and 0.99, used only when --format=jpg

--output_height
    The height of the output image (in pixels for PNG or JPEG-format images.)

--output_width
    The width of the output image (in pixels for PNG or JPEG-format images.)

--force
    Continue with conversion even if the target file(s) already exist.

--verbose
    Output some extra informational messages.

--help
    Displays the usage statement.   

--man
    Displays this man page.

=head1 DESCRIPTION

Run the apache batik rasterizer on one or more SVG files, converting them to
either PNG, JPEG, or PDF format.  If --output_height or --output_width is 
specified, but not both, then the images will be resized so that they retain 
their original aspect ratio.  If neither is specified then an --output_height 
of 1000 will be used.

=head1 AUTHOR(S)

 Jonathan Crabtree, Amy Egan

=head1 COPYRIGHT

Copyright (c) 2007, Jonathan Crabtree, Amy Egan and Joana C. Silva.
All Rights Reserved.

=cut 

use strict;

use Carp;
use FileHandle;
use Getopt::Long;
use Pod::Usage;

# ------------------------------------------------------------------
# Globals
# ------------------------------------------------------------------

# Part of the Apache Batik software package:
my $BATIK_RASTERIZER_JAR = '/usr/local/projects/aegan/idea/third-party/apache-batik/batik-rasterizer.jar';

# Mapping from target file suffix to corresponding MIME type.
my $APP_TYPES = {
    'pdf' => 'application/pdf',
    'jpg' => 'image/jpeg',
    'png' => 'image/png',
};

my $PREFIX_REGEX = '^(.*)\.(svg|svg\.gz|svgz)$';

# ------------------------------------------------------------------
# Input
# ------------------------------------------------------------------

my($svg_files, $svg_file_regex, $output_height, $output_width, $format, $force, $quality, $verbose, $help, $man);

&GetOptions("svg_files=s" => \$svg_files,
	    "svg_file_regex=s" => \$svg_file_regex,
	    "output_height=i" => \$output_height,
	    "output_width=i" => \$output_width,
	    "format=s" => \$format,
	    "quality=s" => \$quality,
	    "force" => \$force,
	    "verbose" => \$verbose,
	    "help" => \$help,
	    "man" => \$man,
	    );

pod2usage(1) if $help;
pod2usage({-verbose => 2}) if $man;


pod2usage({-message => "Error:\n     --format must be specified\n", -exitstatus => 0, -verbose => 0}) if (!$format);

my $appType = $APP_TYPES->{$format};
pod2usage({-message => "Error:\n     $format is not a valid option for --format\n", -exitstatus => 0, -verbose => 0}) if (!$appType);

# defaults
$output_height = 1000 if (!defined($output_height) && !defined($output_width));
$svg_files = '.' if (!defined($svg_files));
$svg_file_regex='.*' if (!defined($svg_file_regex));

# ------------------------------------------------------------------
# Main program
# ------------------------------------------------------------------

# get list of files to be converted
my $files = [];

if (-d $svg_files) { # --svg_files is a directory
    opendir(SD, $svg_files);
    my @allFiles = grep {/$PREFIX_REGEX/ && /$svg_file_regex/} readdir(SD);
    closedir(SD);
    push(@$files, @allFiles);
} 
elsif (-f $svg_files) { # --svg_files is a regular file
    push(@$files, $svg_files);
} 
else {
    die "could not open/read from --svg_files=$svg_files";
}

my $nf = scalar(@$files);
my $nfp = 0;

print STDERR "converting $nf file(s) from $svg_files\n";

foreach my $file (@$files) {
    if ($file =~ /$PREFIX_REGEX/)  {
	my $prefix = $1;
	$prefix = $file if (!defined($prefix));
	my $new_file = $prefix . "." . $format;

	die "target file $new_file already exists; move it or re-run with --force option" if (-e $new_file && !$force);

	print STDERR " $file -> $new_file\n";
	&convertSvgFile($file, $new_file);
	++$nfp;
    }
}

print STDERR "converted $nfp file(s)\n";

exit(0);

# ------------------------------------------------------------------
# Subroutines
# ------------------------------------------------------------------

sub convertSvgFile {
    my($svg_file, $output_file) = @_;

    # do a trivial parse of the $svg_file to find total width and height
    my($svgWidth, $svgHeight) = (undef, undef);
    
    my $openCmd = ($svg_file =~ /\.(gz|svgz|svg\.gz)$/) ? "zcat $svg_file |" : "$svg_file";
    my $fh = FileHandle->new();
    $fh->open($openCmd) || die "unable to open $svg_file";
    while (my $line = <$fh>) {
	if ($line =~ /svg.*height=\"([\d\.]+)\"/i) {
	    $svgHeight = $1;
	}
	if ($line =~ /svg.*width=\"([\d\.]+)\"/i) {
	    $svgWidth = $1;
	}
	last if (defined($svgWidth) && defined($svgHeight));
    }
    $fh->close();
    die "unable to parse svg width and height from $svg_file" unless (defined($svgWidth) && defined($svgHeight));
    
    print STDERR " $svg_file [width=$svgWidth, height=$svgHeight] -> " if ($verbose);
    
    # want to maintain aspect ratio unless both --output_width and --output_height have been specified
    my $originalAspectRatio = $svgWidth / $svgHeight;
    
    my ($owidth, $oheight) = ($output_width, $output_height);
    
    # set height based on width
    if (defined($owidth) && !defined($oheight)) {
	$oheight = int($owidth/$originalAspectRatio);
    } 
    # set width based on height
    elsif (!defined($owidth) && defined($oheight)) {
	$owidth = int($originalAspectRatio * $oheight);
    }
    
    print STDERR " $output_file [width=$owidth, height=$oheight]\n" if ($verbose);

    my $tqs = defined($quality) ? "-q $quality" : "";
    
    # batik-rasterizer options:
    #   -w -h       output width and height
    #   -a x,y,w,h  output area
    #   -dpi        output resolution
    #   -maxw       maximum width (Images wider than 32,767 pixels cause problems in Java.)
    #
    my $maxHeapSize = $ENV{"IDEA_maxHeapSize"};
    my $rasterizeCmd = "java -mx${maxHeapSize}M -Djava.awt.headless=true -jar $BATIK_RASTERIZER_JAR " .
	"-a 0,0,$svgWidth,$svgHeight -w $owidth -h $oheight -maxw 32767 -d ${output_file} $tqs -m '${appType}' $svg_file > /dev/null";
    print STDERR "running batik-rasterizer command: $rasterizeCmd\n" if ($verbose);
    system($rasterizeCmd);
}
