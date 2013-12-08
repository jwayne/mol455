package TIGR::Panda::FilterSearchDB;

# This module is usd to prefilter the database for blastp searches
# using max-match, a maximal matching program to reduce the database
# size used in the searches.
#
# <p>Copyright (C) 2001, The Institute for Genomic Research (TIGR).
#
# <p>All rights reserved.
#
# <pre>
# $RCSfile: FilterSearchDB.pm,v $
# $Revision: 1.1 $
# $Date: 2003/07/29 13:12:26 $
# $Author: amahurka $
# </pre>
#

=head1 NAME

TIGR::Panda::FilterSearchDB.pm - A module to filter the search database by running a quick matching algorithm.

=head1 VERSION

This document refers to version 1.00 of TIGR::Panda::FilterSearchDB.pm, released MMMM, DD, YYYY.

=head1 SYNOPSIS

    use TIGR::Panda::FilterSearchDB;

    # OO Usage

    # Create instance and validate the parameters
    my $validator = new TIGR::Panda::FilterSearchDB($primary, $secondary, $other);

=head1 DESCRIPTION

This is the concrete class that describes the properties and methods of the protein
sequence object. This class provides the methods to access the attributes and cross-references
associated with this protein.

=head2 Overview

An overview of the purpose of the file.

=head2 Constructor and initialization.

if applicable, otherwise delete this and parent head2 line.

=head2 Class and object methods

if applicable, otherwise delete this and parent head2 line.

=over 4

=cut


use strict;
use Data::Dumper;
use Log::Log4perl qw(get_logger);
use vars '$AUTOLOAD'; # To keep strict happy
use File::Basename;
use Carp;

my $logger; # Logger instance used by this class

## internal variables and identifiers
our $VERSION = (qw$Revision: 1.1 $)[-1];
our @DEPEND = qw(Data::Dumper Log::Log4perl);

# Eliminate annoying warnings
if ($^W) {  # Boolean for the global warnings flag.
    $VERSION = $VERSION;
    @DEPEND = @DEPEND;
}


=item  TIGR::Panda::FilterSearchDB->new(%args)

B<Description:> Creates an instance of the FilterSearchDB class. 

B<Parameters:> %args - The arguments hash, the valid arguments are
                query  - Query file name
                db     - Database file name
                length - Max-match word length (default 10)
                minidb - Mini database name (optional)

B<Returns:> The instance of this class initialized with the specified values.

=cut

sub new {
    my ($class, %args) = @_;
    no strict "refs";

    $class = ref($class) || $class;

    # Get an instance of the logger for this module
    $logger = get_logger("TIGR::Panda");

    my $self = {};
    bless $self, $class;

    # Call the init method to do other initialization tasks
    $self->_init(%args);

    return $self;
}

=item $obj->_init()

B<Description:> This is the initiaization method called when a new object is created.

B<Parameters:> $query     - The query file name
               $db        - The database file name
               $length    - Max-match word length
               $minidb    - Mini database name
               $template  - Temlpate file name
               $instance  - Instance file name
               $log_level - Log level
               $logfile   - Log file name

B<Returns:> Nothing.

=cut

sub _init {
    my ($self, %args) = @_;

    # Get an instance of the logger for this module
    $logger = get_logger("TIGR::Panda");
    $logger->debug("Inside _init method of FilterSearchDB.\nArguments:  ", Dumper(\%args));
    
    # Set the initial value of the instance variables
    $self->{_query} = $args{query};              # Set the value for query file name
    $self->{_db} = $args{db};                    # Set the value for db name
    $self->{_length} = $args{length} || 10;      # Set the value for max-match word length
    $self->{_mini_db} = $args{minidb};           # Set the mini db name
    $self->{_template} = $args{template};        # Set the workflow template name
    $self->{_instance} = $args{instance};        # Set the workflow instance name
    $self->{_log_level} = $args{log_level};      # Set the log level
    $self->{_logfile} = $args{logfile};          # Set the log file name
}

=item $obj->filter()

B<Description:> This method launches the step of filtering the database. The method
    first builds the workflow instance and runs the workflow instance to build the
    filtered mini database that can be used for blastp searches.

B<Parameters:> None.

B<Returns:> The mini database file name.

=cut

sub filter {
    my ($self) = @_;
    
    my $query = $self->{_query};
    my ($query_prefix, $query_path, $query_type) = fileparse($query, '\..*'); # Split the query file name into parts
    my $db = $self->{_db};
    my $length = $self->{_length};
    my $template_name = $self->{_template};
    my $log_level = $self->{_log_level};
    my $logfile = $self->{_logfile};
    my $mini_db = $self->{_mini_db} || $query_path . "${query_prefix}.mini";  # Mini database name, if not specified use query name

    # Generate the unique name for the ini file by using the name part of the mini db file without extension
    # and concatenating it with FilterDB string
    my ($mini_db_prefix, $mini_db_path, $mini_db_type) = fileparse($mini_db, '\..*'); # Split the mini_db file name into parts
    my $ini_name = $mini_db_path . "FilterDB" . $mini_db_prefix . ".ini"; 
    my $instance_name = $self->{_instance} || $mini_db_path . "FilterDB" . $mini_db_prefix . ".xml";
    
    # Get an instance of the logger for this module
    $logger = get_logger("TIGR::Panda");
    $logger->info("Beginning to filter the database '$self->{_db}' for query file '$self->{_query}'");
    
    # Create the ini map hash used to build the INI file used to build the workflow
    my %ini_map = ();

    # Create the section for the command set and add to the map
    my %cset = ();
    $ini_map{"1"} = \%cset; 
    $cset{"comment"} = "Build MiniDB workflow";

    # Create the section for the command to build the query for max match
    my $query_cat = "$mini_db_prefix.cat"; # Concanetated query file name
    my %build_query = ();
    $ini_map{"1.1"} = \%build_query;
    $build_query{comment} = "Build query for max match";

    my @keys = ();
    my @values = ();
    push @keys, ("param.command");   push @values, ("BuildQueryForMaxMatch");
    push @keys, ("param.query");     push @values, ("$query");
    push @keys, ("param.out");       push @values, ("$query_cat");

    $build_query{keys} = \@keys;
    $build_query{values} = \@values;

    # Create the section for the command to run max match
    my $query_clst = "$mini_db_prefix.clst"; # Clusters file name
    my %run_mam = ();
    $ini_map{"1.2"} = \%run_mam;
    $run_mam{comment} = "Run max match";
    
    my @keys = ();
    my @values = ();
    push @keys, "param.command";              push @values, "max-match";
    push @keys, "param.l";                    push @values, $length;
    push @keys, "param.stdout";               push @values, $query_clst;
    push @keys, "arg";                        push @values, $query_cat;
    push @keys, "arg";                        push @values, $db;
    push @keys, "flag";                       push @values, "f";
    
    $run_mam{keys} = \@keys;
    $run_mam{values} = \@values;

    # Create the section for the command to build the mini database
    my %build_mini_db = ();
    $ini_map{"1.3"} = \%build_mini_db;
    $build_mini_db{comment} = "Build mini database from max-match clusters";

    my @keys = ();
    my @values = ();
    push @keys, "param.command";          push @values, "BuildDBFromMatches";
    push @keys, "param.cluster";          push @values, $query_clst;
    push @keys, "param.out";              push @values, $mini_db;
    push @keys, "param.db";               push @values, $db;

    $build_mini_db{keys} = \@keys;
    $build_mini_db{values} = \@values;

    # Write the datastructure to the ini file
    $self->write_ini($ini_name, \%ini_map);
    $logger->info("Building mini database '$mini_db'.");

    # Build the workflow instance and run it to build the MiniDB
    my $command = "RunWorkflow -c $ini_name -t $template_name -i $instance_name";
    $command .= " -v $log_level" if ($log_level);
    $command .= " -l $logfile" if ($logfile);

    $logger->info("Executing the command to build and execute workflow: $command");
    system "$command";
    my $ret_val = $? >> 8;
    $logger->info("Command return value: $ret_val");
    !$ret_val or $self->abort("Error executing workflow.");
    $logger->info("Built mini database '$mini_db'.");

    return $mini_db;
}

=item $obj->write_ini()

B<Description:> This method creates an INI file from the hash. Each section of the
    INI file corresponds to a key in the hash. The value is another hash that contains
    the keys and the values for this section.

B<Parameters:> $ini_name  - INI file name
               \%ini_map  - Reference to the ini map hash

B<Returns:> None.

=cut

sub write_ini {
    my ($self, $ini_name, $ini_ref) = @_;
    
    # Get an instance of the logger for this module
    $logger = get_logger("TIGR::Panda");
    $logger->info("Beginning to write the ini file '$ini_name'");
    $logger->debug("The INI hash is: ", Dumper($ini_ref));

    open INI, ">$ini_name" or $self->abort("Could not create file '$ini_name': $!");
    # Loop through each of the sections and write to the file
    foreach my $key (keys %$ini_ref) {
        # write the section header
        print INI "[$key]\n";

        # Write the details for this section
        my %section = %{$ini_ref->{$key}};

        # Write the comment if it exists
        if (exists $section{comment}) {
            print INI "; $section{comment}\n";
        }

        # Loop through the list of keys and write the keys and corresponding
        # values
        if (exists $section{keys}) {
            my @keys = @{$section{keys}};
            my @values = @{$section{values}};
            my $len = scalar @keys;
            for (my $i = 0; $i < $len; $i++) {
                print INI "$keys[$i]=$values[$i]\n";
            }
        }
        print INI "\n";

    }
    close INI;
    $logger->info("Finished writing the ini file '$ini_name'");

}

sub abort {
    my ($self, $msg) = @_;
    $logger->fatal($msg);
    croak;
}

sub AUTOLOAD {
    no strict "refs";
    my ($self, $newval) = @_;

    # Handle get methods
    if ($AUTOLOAD =~ /.*::get(_\w+)/) {
        my $attr_name = $1;
        *{$AUTOLOAD} = sub { return $_[0]->{$attr_name} };
        return $self->{$attr_name};
    }

    # Handle set methods
    if ($AUTOLOAD =~ /.*::set(_\w+)/) {
        my $attr_name = $1;
        *{$AUTOLOAD} = sub { $_[0]->{$attr_name} = $_[1]; return };
        $self->{$attr_name} = $newval;
    }
}
1;
