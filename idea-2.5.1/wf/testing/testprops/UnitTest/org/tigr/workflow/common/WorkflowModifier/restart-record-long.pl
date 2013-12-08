#!/usr/bin/perl -w
$args = join(" ", @ARGV);
system("/usr/local/java/1.5.0/bin/java -cp $ENV{WF_ROOT}/jars/wf.jar org.tigr.workflow.util.RestartRecordLong $args");
