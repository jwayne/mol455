#!/usr/bin/perl -w
=head1 NAME

CommandSetNotification.pl - The command line script used to get notifications about 
command level events occuring in the execution of a workflow.

=head1 VERSION

This document refers to version 1.00 of CommandNotification.pl.
 $RCSfile: CommandSetNotification.pl,v $
 $Revision: 1.4 $
 $Date: 2004/07/08 21:08:04 $

 Author: Anup Mahurkar

=over 4

=cut

use strict;
use Config::IniFiles;
use DBI;
use Data::Dumper;
use Getopt::Long;
use Log::Log4perl qw(get_logger);
use Pod::Usage;

my %options = ();
my %file_options = ();

# If no arguments were specified then print help with error
unless (@ARGV) {
    print "Invalid invocation. No parameters specified!. See usage for help.\n";
    pod2usage(1);
    exit(1);
}

print "Invoked with arguments: " . join " ", @ARGV;

# Read all the command line options and put them in a hash
GetOptions(\%options, "name=s", "id=i", "event=s", "file=s", "time=s", "message=s", "props=s",
			"host=s",
           "h", "help", "usage",                               # All possible help flags
           "conf=s",  "logconf=s",                             # Configuration files.
           );

# The user can specify his/her own logger configuration file with the --logconf flag. If the
# flag wasn't passed, then we check the current directory for the logger.conf file. Finally,
# if that doesn't exist, then we read the global default logger.conf which should be installed
# in a globally accessible area.
my $logger_conf = $options{logconf} || "log4perl.properties";
# Check that the file exists, is a text file, and that it is readable.
unless ( -e $logger_conf && -T $logger_conf && -r $logger_conf) {
    warn("Problem with the logger config file. Using global default.");
    $logger_conf = "$ENV{WF_ROOT}/log4perl.properties";
}

# Set up the logger specification through the conf file
Log::Log4perl->init($logger_conf);
my $logger = get_logger();

# If the user has requested help then print usage and exit
if ( (exists $options{h}) || (exists $options{help}) || (exists $options{usage}) ) {
    pod2usage(1);
    exit(0);
}

$logger->debug("After parsing command line:\n", sub { Dumper(\%options) } );

# If the --conf flag has been specified then read the parameters in configuration file 
if (exists $options{conf}) {
    my $ini_file = $options{conf};

    # Read the parameters in the configuration file
    read_config_file($ini_file);
}

# Make sure that command name has been specified
if (!exists $options{name}) {
    $logger->fatal("No command name specified with --name, aborting execution.");
    die;
}

# Print the message to the log file at info level and quit
my $event = $options{'event'};
my $msg = "Received '" . $event . "' notification from command set '" . $options{'name'} . "' ID: " .$options{'id'}; 
$msg .= " at '" . $options{'time'} . "'.";

# If the host name has been specified the append the host name
if ($options{host}) {
	$msg .= " Executing on host: $options{host}.";
}	

# If the event was a failure event append the failure cause
if  ($event eq "failure") {
	$msg .= " Command Set failed due to '" . $options{'fail'} . "'.";	
}

# If the event was finish event, then add the return value and create the empty files used by
# tests to determine if this script was invoked
if ($event eq 'finish') {
	$msg .= " Command Set finished.";	
	system("touch command_set_finished");
}

if ($options{'message'}) {
	$msg .= " Message '" . $options{'message'} . "'.";
}

# If the event was start event, then create the file to indicate that start command set was received
if ($event eq 'start') {
	system("touch command_set_started");
}

$logger->info($msg);
exit;
    
=item read_config_file($ini_file)

B<Description:> Parses the INI style file to read the program parameters. These parameters
are used instead of command line parameters

B<Parameters:> $ini_file - The name of the file that contains the configuration parameters 

=cut

sub read_config_file {
    my $ini_file = shift;
    $logger->info("Reading configuration parameters from the INI file '$ini_file'");

    my $cfg = Config::IniFiles->new( -file => "$ini_file" );

        
    $options{name} = $cfg->val('params', 'name') if (!$options{name}); # Read the name from file if not specified
    $options{id} = $cfg->val('params', 'id') if (!$options{id}); # Read the id from file if not specified
    $options{event} = $cfg->val('params', 'event') if (!$options{event}); # Read the event from file if not specified
    $options{file} = $cfg->val('params', 'file') if (!$options{file}); # Read the file from file if not specified
    $options{'time'} = $cfg->val('params', 'time') if (!$options{'time'}); # Read the time from file if not specified
    $options{'message'} = $cfg->val('params', 'message') if (!$options{'message'}); # Read the message from file if not specified
    $options{'retval'} = $cfg->val('params', 'retval') if (!$options{'retval'}); # Read the retval from file if not specified
    $options{'fail'} = $cfg->val('params', 'fail') if (!$options{'fail'}); # Read the fail from file if not specified
    $options{'host'} = $cfg->val('params', 'host') if (!$options{'host'}); # Read the host from file if not specified
    
    
    $logger->debug("After reading params from config file:\n", sub { Dumper(\%options) } );
}

__END__

=back

=head1 SYNOPSIS

This script is a generic script used to receive command event notifications. This script just prints out the details of the
event to the default log file. The parameters for the command may be passed as command line parameters or in a INI style 
configuration file.

Usually command line parameters override the ones specified in config file.

Typical invocation:

    CommandSetNotification.pl --name=<command name> 
                            --id=<command ID>
                            --event=<event that triggered this call>
                            --file=<command set instance file>
                            --time=<time when this event was generated>
                            [--message=<message associated with event> (in case of failure or error)]
                            [--host=<hostname> (in case oc command set start and restart)]
                            [--retval=<return value>]
                            [--fail=<failure reason>]            
                            [--conf=<configuration file>]
                            [--logconf=<logging configuration file (Log::Log4perl)>]
                            [other parameters]

Some options may be used with all modes:

=head1 DESCRIPTION

This script is the command line script used to receive events associated with commands in a workflow instance.

=head1 ENVIRONMENT

=head1 DIAGNOSTICS

=over 4

=item "Error message that may appear."

Explanation of error message.

=item "Another message that may appear."

Explanation of another error message.

=back

=head1 BUGS

Most of the Panda use cases are not implemented yet.

=head1 SEE ALSO

 Log::Log4perl
 Config::IniFiles

=head1 AUTHOR(S)

 The Institute for Genomic Research
 9712 Medical Center Drive
 Rockville, MD 20850

=head1 COPYRIGHT

Copyright (c) 2003, The Institute for Genomic Research. All Rights Reserved.
