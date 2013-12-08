#!/usr/bin/perl -w
=head1 NAME

LoggingSystemCommandProcessor - The command line tool used execute a logger aware system command specified in the config file

=head1 VERSION

This document refers to version 1.00 of LoggingSystemCommandProcessor.
 $RCSfile: LoggingSystemCommandProcessor.pl,v $
 $Revision: 1.2 $
 $Date: 2003/12/12 19:22:20 $

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

print "Invoked with arguments: " . join " ", @ARGV, "\n";

print "PWD: $ENV{PWD} INSTANCE: $ENV{INSTANCE_FILE}\n";
# Read all the command line options and put them in a hash
GetOptions(\%options, "command=s",
           "h", "help", "usage",                               # All possible help flags
           "conf=s",  "logconf=s",                             # Configuration files.
           "command_id=i",
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
Log::Log4perl::MDC->put("Name", $options{command_id});

# If the user has requested help then print usage and exit
if ( (exists $options{h}) || (exists $options{help}) || (exists $options{usage}) ) {
    pod2usage(1);
    exit(0);
}

$logger->debug("After parsing command line:\n", sub { Dumper(\%options) } );

# For the proper execution of the command there should be a command paramater, if it
# is not specified, then warn and die
if (!exists $options{command}) {
    $logger->fatal("No command specified with --command, aborting execution.");
    die;
}

# Build the command string by concatenating the parameters in the options hash
my $command = $options{command};

# If the --conf flag has been specified then read the parameters in configuration file 
if (exists $options{conf}) {
    my $ini_file = $options{conf};

    # Read the parameters in the configuration file
    read_config_file($ini_file);

    # Process the flags
    if (defined $file_options{flags}) {
        my @flags = @{$file_options{flags}};
        $logger->debug("Flags specified: ", Dumper(\@flags));

        foreach my $flag (@flags) {
            my $pos = -1; 
            if(($pos = index($flag, "-")) > -1){ 
                # Append each flag to the command
                $command .= " $flag";
            }
            else {
                # If the parameter is a single character then use the single dash,
                # otherwise use double dash
                if ((length $flag) == 1) {
                    $command .= " -$flag";
                } else {
                    $command .= " --$flag";
                }
            }
        }
        $logger->debug("Command after appending flags '$command'.");
    }

    # Process the key value parameters
    if (defined $file_options{params}) {
        my @params_list = keys %{$file_options{params}};
        my %params = %{$file_options{params}};
        $logger->debug("Params specified: ", Dumper(\%params));
        foreach my $param (@params_list) {
            my $value = $params{$param};

            # If the value is an array then build a comma separated list
            if (ref ($value) eq "ARRAY") {
                $value = join ",", @$value;
            }

            # check to see the parameter starts with a '-'.
            my $pos = -1; 
            if(($pos = index($param, "-")) > -1){ 
                # If the parameter starts with a '-', then append each param/value to the command 
                $command .= " $param=$value";
            }
            else {
                #If the parameter is a single character then use the single dash,
                # otherwise use double dash
                if ((length $param) == 1) {
                    $command .= " -$param $value";
                } else {
                    $command .= " --$param=$value";
                }
            }
        }
        $logger->debug("Command after appending params '$command'.");
    }
    
    # Pass the command ID and logger config
    $command .= " --command_id=$options{command_id} --logconf=$logger_conf";

    # Process the args
    if (defined $file_options{args}) {
        $logger->debug("Args specified: ", Dumper($file_options{args}));
        $command .= " " . (join " ", @{$file_options{args}});
        $logger->debug("Command after appending args '$command'.");
    }

    # Check to see if any redirections have been specified in which case modify the
    # command string to accomodate that
    my %redirects = %{$file_options{redirects}};
    $logger->debug("Redirects specified: ", Dumper(\%redirects));

    # Check to see if STDIN is redirected
    if (exists $redirects{stdin}) {
        # As the user is redirecting the STDIN set that
        my $stdin_ref = $redirects{stdin};
        my $stdin = $stdin_ref->{stdin};
        $command .= " < $stdin";
    }

    # Check to see if either STDOUT or STDERR are redirected
    if (exists $redirects{stderr} || exists $redirects{stdout}) {
        $logger->debug("STDOUT or STDERR redirected");
        my ($stdout, $stdout_append, $stderr, $stderr_append);

        # Read the value for STDOUT
        if (exists $redirects{stdout}) {
            my $stdout_ref = $redirects{stdout};
            $stdout = $stdout_ref->{stdout};
            $stdout_append = $stdout_ref->{append};
        }

        # Read the value for STDERR
        if (exists $redirects{stderr}) {
            my $stderr_ref = $redirects{stderr};
            $stderr = $stderr_ref->{stderr};
            $stderr_append = $stderr_ref->{append};
        }

        # If both STDOUT and STDERR are specified to the same file
        # then use the 2>&1
        if ($stdout && $stderr) {

            # If they both point to the same file 
            if ($stdout eq $stderr) {
                my $outstr = " 1>";
                $outstr .= ">" if $stdout_append;
                $outstr .= $stdout;
                
                $command .= "$outstr 2>&1";
            } else {
                # As they are redirected to different files build string
                # for that
                my $outstr = " 1>";
                $outstr .= ">" if $stdout_append;
                $outstr .= $stdout;
                
                $outstr .= " 2>";
                $outstr .= ">" if $stderr_append;
                $outstr .= $stderr;
                
                $command .= $outstr;
            }
        } elsif ($stdout) {
            # As only stdout redirection is specified use that string
            my $outstr = " 1>";
            $outstr .= ">" if $stdout_append;
            $outstr .= $stdout;
            
            $command .= $outstr;
        } elsif ($stderr) {
            # As only stderr redirection is specified use that string
            my $errstr = " 2>";
            $errstr .= ">" if $stderr_append;
            $errstr .= $stderr;
            
            $command .= $errstr;
        }
    }

}

# Invoke the command specified as a system command and use the return value
# to decide if the command succeeded
$logger->info("Invoking command: $command");

system $command;
my $ret_val = $? >> 8;

# If the return value is not zero then this is an error condition
# so log that 
if ($ret_val != 0) {
    $logger->error("Error executing command: '$command'. Returned value: $ret_val");
} else {
    $logger->info("Command: '$command' was successful");
}

exit $ret_val;
    
=item read_config_file($ini_file)

B<Description:> Parses the INI style file to read the program parameters. These parameters
are used to invoke the command specified. The file options hash is loaded with the parameters 
read from the file. The file typically has four sections, params, args, flags, and redirects. The 
values from these three sections are loaded in three different data structures, hashes for
params, and redirects, and arrays for args and flags.

B<Parameters:> $ini_file - The name of the file that contains the configuration parameters 

=cut

sub read_config_file {
    my $ini_file = shift;
    $logger->info("Reading configuration parameters from the INI file '$ini_file'");

    my $cfg = Config::IniFiles->new( -file => "$ini_file" );

    my %params = ();
    my @flags = ();
    my @args = ();
    my %redirects = ();
    
    my @file_params = $cfg->Parameters('params');
    $logger->debug("Params read from config file:", Dumper(\@file_params) );

    # Build the flags list and use the list to push the arguments where the
    # list value is the position
    my @flags_list = $cfg->Parameters('flags');
    foreach my $pos (@flags_list) {
        $flags[$pos - 1] = $cfg->val('flags', $pos);
    }
    $logger->debug("Flags read from config file:", Dumper(\@flags) );

    my @args_list = $cfg->Parameters('args');
    foreach my $pos (@args_list) {
        $args[$pos - 1] = $cfg->val('args', $pos);
    }
    $logger->debug("Args read from config file:", Dumper(\@args) );


    $file_options{params} = \%params;
    $file_options{redirects} = \%redirects;
    $file_options{flags} = \@flags;
    $file_options{args} = \@args;

    # Loop through each of the possible parameters and initialize the params hash
    # if the key does not exist
    foreach my $param (@file_params) {
        my $value = $cfg->val('params', $param);
        $logger->debug("Value for parameter '$param' read from config file is '$value'.");
        $params{$param} = $value;
    }

    # See if the user has specified redirects
    my $value = $cfg->val('redirects', 'stdout');
    if ($value) {
        # As stdout redirect is specified create a hash
        my %stdout = ();
        $redirects{stdout} = \%stdout;
        $stdout{stdout} = $value;

        # See if the appnd flag is specified
        $value = $cfg->val('redirects', 'stdoutappend');
        if ($value) {
            $stdout{append} = 1;
        }
    }
    
    # Get the STDERR redirect specifications
    $value = $cfg->val('redirects', 'stderr');
    if ($value) {
        # As stderr redirect is specified create a hash
        my %stderr = ();
        $redirects{stderr} = \%stderr;
        $stderr{stderr} = $value;

        # See if the appnd flag is specified
        $value = $cfg->val('redirects', 'stderrappend');
        if ($value) {
            $stderr{append} = 1;
        }
    }
    
    # Get the STDIN redirect specifications
    $value = $cfg->val('redirects', 'stdin');
    if ($value) {
        # As stdin redirect is specified create a hash
        my %stdin = ();
        $redirects{stdin} = \%stdin;
        $stdin{stdin} = $value;
    }
    
    $logger->debug("After reading params from config file:\n", sub { Dumper(\%file_options) } );
}

__END__

=back

=head1 SYNOPSIS

This script is a generic system command processor which may be used to invoke system utilities
and other stand alone applications for executing commands in the workflow. The parameters for
the command may be passed as command line parameters or in a INI style configuration file. This 
module invokes the specified command with the command_id & logging configuration file as two of
the parameters. If the user has not specified a logging config file, then the default is used.

Usually command line parameters override the ones specified in config file.

Invocation:

    LoggingSystemCommandProcessor.pl --command=<command name> 
                           [--conf=<configuration file>]
                           [--command_id=<command ID of the command>]
                           [--logconf=<logging configuration file (Log::Log4perl)>]
                           [other parameters]

Some options may be used with all modes:

=head1 DESCRIPTION

This script is the command line tool to execute a system command and is used as the
default system command processor in the workflow system.

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
