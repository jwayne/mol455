export PERL=`which perl 2>/dev/null | tr -d [:space:]`
if [ -z $PERL ]; then
    echo Perl not found!  IDEA requires Perl 5.6 or higher.
    exit 1
fi
$PERL `echo $0 | sed s'/\/install-idea\.bash$//'`/install-idea.pl
