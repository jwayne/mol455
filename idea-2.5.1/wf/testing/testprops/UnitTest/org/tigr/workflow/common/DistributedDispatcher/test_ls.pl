#!/usr/bin/perl -w
print "Executing: ls @ARGV \n";
sleep (60);
!system ("ls @ARGV") or die "Couldn't run ls command";

print "Returning: $? \n";
exit $?;

