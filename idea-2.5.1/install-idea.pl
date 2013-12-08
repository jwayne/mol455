$COMMAND_EXECUTED = $0;
undef(@directoryStack);
$invocationDirectory = `pwd`;
chomp($invocationDirectory);
push(@directoryStack, $invocationDirectory);

$bash = &findOrGiveUp("bash");
$perl = &setUpPerl();
$projectBaseDir = &projectBaseDirectory();
&enterDirectory($projectBaseDir);
$workflowBaseDir = &unpackWorkflowTarFile();

# The following statements are executed in the working directory $workflowBaseDir.
$gridType = &determineGridType();
$eventLogDir = &createEventLogHierarchy($workflowBaseDir);
&transformWorkflowFiles();
&createWorkflowLinks();
&returnToPreviousDirectory();

# Back in the IDEA installation directory to set up IDEA!
$ruby = &setUpRuby();
$phyml = &requirePHYML();
if ($phyml){
    $ENV{"phyml"} = 1;
}
%phylipPrograms = &requirePhylipPrograms($phyml);
$pamlBinDir = &requirePAMLPrograms();
&requireOtherPrograms();
&unpackThirdPartyArchives();
&customizeIDEAFiles();
&createJARLinks();
&removeUnwantedFiles();
$java = &setUpJava();
&compileJavaSource($java);
@scripts = ("check-java-memory", "delete-old-event-logs", "idea", "idea-A-create-tree.pl", "idea-B-run-paml.pl",
	    "idea-C-merge-runs.pl", "idea-D-parse-output.pl", "newickDraw", "ss.convert-svg-files.pl",
		   "ss.make-idea-svg-graphs.pl", "verify-java");
foreach $script(@scripts){
    system("chmod u+x $script")
	and die "$script could not be made executable.\n";
    system("chmod g+x $script")
	and die "$script could not be made executable.\n";
}
&printNotices();

sub printNotices{
    print "Congratulations!  You have successfully installed IDEA.\n";
    print "You may find it convenient to add $projectBaseDir to your path for easy access to IDEA.\n";
    if ($gridType eq "sge"){
	print "\nSGE event logs will be stored in $eventLogDir.\nThis directory should be cleaned out periodically.\n";
	print "The script delete-old-event-logs may be used for this purpose.\n";
	&warnSGEUsers();
    }
}

sub warnSGEUsers{
    print "\nNOTICE TO SGE (SUN GRID ENGINE) USERS:\n";
    print "======================================\n";
    print "IDEA uses the Workflow process management system to manage grid submissions.\n";
    print "In order to work with SGE, Workflow requires the following set-up by a grid administrator:\n";
    print "The scripts $workflowBaseDir/bin/prolog_uninitialized and $workflowBaseDir/bin/epilog_uninitialized must be ";
    print "installed as prolog and epilog, respectively, on every queue on which Workflow jobs will be executed.\n\n";
    print "Grid submissions have been temporarily disabled.\n";
    print "You may still use IDEA, but all processes will be executed on the machine from which you launch IDEA.\n\n";
    print "Once an administrator has installed these scripts, rename $workflowBaseDir/bin/prolog_uninitialized to ";
    print "$workflowBaseDir/bin/prolog and $workflowBaseDir/bin/epilog_uninitialized to $workflowBaseDir/bin/epilog to ";
    print "enable grid submissions.\n\n";
    print "(If Workflow has already been installed on your grid, prolog and epilog do not need to be reinstalled.)\n";
}

sub requirePHYML{

    my $unameString = `uname -sm`; 
    
    chomp($unameString);
    
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

sub requirePhylipPrograms{

    my $phymlFound = shift(@_);

    my @requiredPhylipPrograms = ("dnadist", "dnapenny", "neighbor", "retree");
    my $phylipProgram, %phylipExecutables;

    undef(%phylipExecutables);
    print "Checking for required PHYLIP programs...\n";
    foreach $phylipProgram(@requiredPhylipPrograms){
	$phylipExecutables{$phylipProgram} = $phymlFound ? &findProgram($phylipProgram) : &findOrGiveUp($phylipProgram);
    }
    return %phylipExecutables;
}

sub requirePAMLPrograms{

    my @requiredPAMLPrograms = ("baseml", "chi2", "codeml");
    my $pamlProgram, $pamlDir, $errorMessage, $nextToLastIndex;

    undef(%pamlExecutables);
    print "Checking for required PAML programs...\n";
    foreach $pamlProgram(@requiredPAMLPrograms){
	$pamlExecutables{$pamlProgram} = &findOrGiveUp($pamlProgram);
    }
    $pamlDir = &directoryContaining($pamlExecutables{"baseml"});
    unless (($pamlDir eq &directoryContaining($pamlExecutables{"chi2"}))
	    && ($pamlDir eq &directoryContaining($pamlExecutables{"codeml"}))){
	$errorMessage = "The executables ";
	foreach $pamlProgram(@requiredPAMLPrograms[0..($#requiredPAMLPrograms - 2)]){
	    $errorMessage .= "$pamlProgram, ";
	}
	$nextToLastIndex = $#requiredPAMLPrograms - 1;
	$errorMessage .= "$requiredPAMLPrograms[$nextToLastIndex] and $requiredPAMLPrograms[$#requiredPAMLPrograms]";
	$errorMessage.= " must be in the same location.  Current locations:  \n";
	foreach $pamlProgram(@requiredPAMLPrograms){
	    $errorMessage .= "$pamlExecutables{$pamlProgram}\n";
	}
	die $errorMessage;
    }
    return $pamlDir;
}
    
sub requireOtherPrograms{

    my @otherRequiredPrograms = ("head", "tail");
    my $otherRequiredProgram;

    print "Checking for other required programs...\n";
    foreach $otherRequiredProgram(@otherRequiredPrograms){
	&findOrGiveUp($otherRequiredProgram);
    }
}

sub removeUnwantedFiles{
    
    my @unwantedExtensions = ('.svn$', '.token$', '.nfs');
    my $unwantedExtension;

    foreach $unwantedExtension(@unwantedExtensions){
	system("find . | grep \"$unwantedExtension\" | xargs rm -f ");  # We don't really care if this fails.
    }
}
	    
sub compileJavaSource{
    
    my ($javaExecutable) = @_;

    print "Compiling...\n";
    system("${javaExecutable}c CheckAvailableMemory.java")
	and die "CheckAvailableMemory.java could not be compiled with ${javaExecutable}c.\n";
}

sub createLink{
    my ($destination, $source) = @_;

    system("ln -s $destination $source")
	and die "$source could not be created as a link to $destination.";
}

sub createJARLinks{
    &enterDirectory("jars");
    &createLink("../commons-io/commons-io-1.3.1/commons-io-1.3.1.jar", "commons-io-1.3.1.jar");
    &createLink("../jdic/jdic-20060613-bin-cross-platform/jdic.jar", "jdic.jar");
    &createLink("../pdfbox/PDFBox-0.7.2/lib/PDFBox-0.7.2.jar", "PDFBox-0.7.2.jar");
    &createLink("../xstream/xstream-1.2.1/xstream/lib/xpp3_min-1.1.3.4.O.jar", "xpp3_min-1.1.3.4.O.jar");#Yes, it's an O.
    &createLink("../xstream/xstream-1.2.1/xstream-1.2.1.jar", "xstream-1.2.1.jar");
    &returnToPreviousDirectory();
}

sub createWorkflowLinks{

    my @workflowTools = ("CheckWorkflow", "CleanWorkflowRegistry", "ControlWorkflow", "CreateWorkflow",
			 "EditTemplate", "KillWorkflow", "MonitorWorkflow", "RunTestSuite",
			 "RunWorkflow", "WorkflowEditor", "WorkflowMonitor");
    my $wfTool;

    foreach $wfTool(@workflowTools){
	system ("ln -sf RUN_PROGRAM $wfTool")
	    and die "$workflowBaseDir/$wfTool could not be created as a link to ($workflowBaseDir/)RUN_PROGRAM.\n";
	$wfTool =~ s/([A-Z])/\_$1/g;
	$wfTool =~ s/^\_//;
	$wfTool = "\L$wfTool\E";
	system("ln -sf switch.sh $wfTool")
	    and die "$workflowBaseDir/$wfTool could not be created as a link to ($workflowBaseDir/)switch.sh.\n";
    }
    system("ln -sf $workflowBaseDir/server-conf/sge_mockserver.conf $workflowBaseDir/server-conf/htc.conf")
	and die "$workflowBaseDir/server-conf/htc.conf could not be created as a link to $workflowBaseDir/server-conf/sge_mockserver.conf.\n";
}

sub customizeIDEAFiles(){
    
    my @localizedFiles = ("idea_gene_template.xml", "idea_gene_template_user_tree.xml", "idea_local_gene_template.xml",
			  "idea_local_gene_template_user_tree.xml", "ss.make-idea-svg-graphs.pl");
    my $localizedFile, $maxHeapSize, $phylipProgram, $phylipInstalled;
    my %phylipProgramSubstitutions;
    
    print "Configuring IDEA...\n";
    foreach $localizedFile(@localizedFiles){
	&transformFile($localizedFile, $localizedFile, "<IDEA_BASE_DIR>", $projectBaseDir);
    }
    &transformFile("delete-old-event-logs", "delete-old-event-logs", '<MOCK_SERVER_BASE>', "\"$eventLogDir\"");
    &transformFile("ss.convert-svg-files.pl", "ss.convert-svg-files.pl",
		   '\'\/?.*\/apache-batik', "\'$projectBaseDir\/apache-batik",
		   "zcat", &zcatVariant());
    undef(%phylipProgramSubstitutions);
    $phylipInstalled = 0;
    foreach $phylipProgram(keys(%phylipPrograms)){
	if ($phylipPrograms{$phylipProgram}){
	    $phylipInstalled = 1;
	}
	$phylipProgramSubstitutions{"\`which $phylipProgram\`"} = "\"$phylipPrograms{$phylipProgram}\"";
	$phylipProgramSubstitutions{"\\\$$phylipProgram = \".*$phylipProgram\""} =
	    "\$$phylipProgram = \"$phylipPrograms{$phylipProgram}\"";
    }
    &transformFile("idea-A-create-tree.pl", "idea-A-create-tree.pl", %phylipProgramSubstitutions);
    &transformFile("idea", "idea", '\$phyml = \'\'', '$phyml = "' . $phyml . '"');
    &transformFile("idea-A-create-tree.pl", "idea-A-create-tree.pl", "my \$phyMLBinary = ''", 'my $phyMLBinary = "' . $phyml . '"');
    &transformFile("idea", "idea", '\$phylipInstalled = 0', '$phylipInstalled = ' . $phylipInstalled);
    &transformFile("idea-B-run-paml.pl", "idea-B-run-paml.pl",
		   '\$options\{\"paml_bin_dir\"\} \= \".*\"', '$options{"paml_bin_dir"} = "' . $pamlBinDir . '"');
    &transformFile("idea-D-parse-output.pl", "idea-D-parse-output.pl", '\$pamlBinDir \= \".*\"', '$pamlBinDir = "' . $pamlBinDir . '"');
    undef(@localizedFiles);
    @localizedFiles = ("check-java-memory", "delete-old-event-logs", "idea", "idea-A-create-tree.pl",
		       "idea-B-run-paml.pl", "idea-C-merge-runs.pl", "idea-D-parse-output.pl", "ss.convert-svg-files.pl",
		       "ss.make-idea-svg-graphs.pl", "verify-java");
    foreach $localizedFile(@localizedFiles){
	&setUserPerl($localizedFile, $perl);
    }
}
    
sub transformWorkflowFiles(){

    my $tests = "testing/testprops/UnitTest/org/tigr/workflow/common";
    my @perlScripts = ("bin/CommandNotification.pl", "bin/CommandSetNotification.pl",
		       "bin/LoggingSystemCommandProcessor.pl", "bin/SystemCommandProcessor",
		       "interrupt", "RUN_PROGRAM",
		       "$tests/DistributedDispatcher/test_ls.pl", "$tests/DistributedProcessor/run_condor_status",
		       "$tests/DistributedProcessor/test_ls.pl", "$tests/WorkflowModifier/restart-record-long.pl");
    my $finalFilename, $sgeLogger;

    print "Configuring Workflow...\n";
    &transformFile("workflow.config.token", "workflow.config", "<WF_ROOT>", $workflowBaseDir, "<BASH_LOC>", $bash,
		   "<GRID_TYPE>", $gridType, "<ID_GEN_CLASS>", "org.tigr.antware.shared.idgen.FileIDGenerator", "<IDFILE_LOC>", "$workflowBaseDir/event-logs");
   # system("cp runJava_universal bin/runJava.token")
#	and die "$workflowBaseDir/runJava_universal could not be copied to $workflowBaseDir/bin/runJava.token.\n";
    &transformFile("bin/runJava.token", "bin/runJava", "<BASH_LOC>", $bash);
    &transformFile("switch.sh.token", "switch.sh", "<BASH_LOC>", $bash,
		   '(CALLED_AS\s*=\s*\`.*)\`', '$1 | sed \'s/^sh_//\'`');
    &transformFile("bin/sge_submitter.sh.token", "bin/sge_submitter.sh",
		   "<SGE_ROOT>", ($gridType eq "sge") ? $ENV{"SGE_ROOT"} : "",
		   "<SGE_CELL>", ($gridType eq "sge") ? $ENV{"SGE_CELL"} : "");
    foreach $finalFilename("exec_env.tcsh", "server-conf/log4j_server.conf"){
	&transformFile("$finalFilename.token", $finalFilename, "<WF_ROOT>", $workflowBaseDir);
    }
    &transformFile("exec_env.bash.token", "exec_env.bash", "<WF_ROOT>", $workflowBaseDir, "<BASH_LOC>", $bash);
    &transformFile("server-conf/sge_mockserver.conf.token", "server-conf/sge_mockserver.conf",
		   "<WF_ROOT>", $workflowBaseDir, "<GRID_TYPE>", $gridType, "<MOCK_SERVER_BASE>", $eventLogDir);
    foreach $finalFilename(@perlScripts){
	&transformFile($finalFilename, "$finalFilename.temp", '\#\!.*perl', '#!' . $perl);
	system("mv $finalFilename.temp $finalFilename")
	    and die "The contents of $finalFilename could not be overwritten with those of $finalFilename.temp.\n";
    }
    #&transformFile("RUN_PROGRAM", "RUN_PROGRAM", '\$script\s*\=\s*\"', '$script = "sh_');
    #&transformFile("RUN_PROGRAM", "RUN_PROGRAM", '\$script\s*\=\s*\"sh_sh_', '$script = "sh_');
    &createLocalDispatcherDirectory("dispatcher_factory_lookup.prop", "local_dispatcher_factory_lookup.prop");
    system("chmod -R 755 *")
	and die "Could not recursively set permissions on directory $workflowBaseDir!\n";
    if ($gridType eq "sge"){
	foreach $sgeLogger("prolog", "epilog"){
	    system("mv bin/$sgeLogger bin/${sgeLogger}_uninitialized")
		and die "$workflowBaseDir/bin/$sgeLogger could not be moved to $workflowBaseDir/bin/$sgeLogger_uninitialized.\n";
	}
    }
}

sub createEventLogHierarchy{
    my ($superDir) = @_;

    my $eventLogDir, $idFilename;

    $eventLogDir = "$superDir/event-logs";
    &makeDirectory($eventLogDir);
    $idFilename = "$eventLogDir/idfile";
    open(ID_FILE_createEventLogHierarchy, ">$idFilename") # FOR OUTPUT
	or die "$idFilename could not be opened for output.\n";
    print ID_FILE_createEventLogHierarchy "1\n";
    close(ID_FILE_createEventLogHierarchy);
    return $eventLogDir;
}

sub findOrGiveUp{
    my ($programName) = @_;

    my $pathToExecutable;

    $pathToExecutable = `which $programName | grep -v "^no $programName"`;
    chomp($pathToExecutable);
    unless ($pathToExecutable){
	die "The required program $programName could not be located.\n";
    }
    return $pathToExecutable;
}

sub directoryContaining{
    my ($file) = @_;

    return substr($file, 0, rindex($file, "/"));
}

sub zcatVariant{
    
    my $pathToExecutable = &findProgram("zcat") || &findProgram("gzcat") || &findProgram("gunzip");

    unless ($pathToExecutable){
	die "The required program zcat (gzcat, gunzip) could not be located.\n";
    }
    return substr($pathToExecutable, rindex($pathToExecutable, "/") + 1)
	. (($pathToExecutable =~ /gunzip$/) ? " -c" : "");
}

sub findProgram{
    my ($programName) = @_;

    my $pathToExecutable = `which $programName | grep -v "^no $programName"`;

    chomp($pathToExecutable);
    return $pathToExecutable;
}

sub setUpJava{

    my $userJavaExecutable = `./verify-java`;

    chomp($userJavaExecutable);
    die "Java was not found.  IDEA requires JAVA 1.5 or higher.\n" unless $userJavaExecutable;
    return $userJavaExecutable;
}

sub setUpRuby{

    my $rubyDir = "$projectBaseDir/jruby";

    
    my $rubyBinPath;

    &enterDirectory($rubyDir);
    $rubyBinPath = $rubyDir . "/" . &unpackArchive("jruby-bin-1.0.0RC1.tar.gz", "jruby-1.0.0RC1") . "/" . "bin";
    &returnToPreviousDirectory();
    #&transformFile("newickDraw", "newickDraw", "/usr/bin/env ruby", "/usr/bin/env $rubyBinPath/jruby");
    return "$rubyBinPath/jruby";
}

sub unpackThirdPartyArchives{
    
    my %thirdPartyLibraries = ("jdic", "jdic-20060613-bin-cross-platform",
			       "pdfbox", "PDFBox-0.7.2",
			       "xstream", "xstream-1.2.1/xstream");
    my $thirdPartyLibrary;
    
    foreach $thirdPartyLibrary(keys(%thirdPartyLibraries)){
	&enterDirectory("$projectBaseDir/$thirdPartyLibrary");
	&unpackArchive(&findArchive(), $thirdPartyLibraries{$thirdPartyLibrary});
	&returnToPreviousDirectory();
    }
    system("mv $projectBaseDir/jdic_stub.jar $projectBaseDir/jdic/jdic-20060613-bin-cross-platform/linux/jdic_stub.jar")
	and die "jdic_stub.jar could not be moved into jdic/jdic-20060613-bin-cross-platform/linux/ .\n";
    system("mv $projectBaseDir/jdic-linux64 $projectBaseDir/jdic/jdic-20060613-bin-cross-platform/linux/amd64")
	and die "jdic-linux64 could not be moved to jdic/jdic-20060613-bin-cross-platform/linux/amd64 .\n";
}

sub setUserPerl{
    my ($perlScript, $userPerlExecutable) = @_;

    my $shebangLine = &lineNumber(1, $perlScript);
    if ($shebangLine =~ /\#! *(\S+)\s/){
	if ($1 ne $userPerlExecutable){
	    open(SCRIPT_setUserPerl, $perlScript)
		or die "$perlScript could not be opened.\n";
	    open(REVISED_SCRIPT_setUserPerl, ">$perlScript.temp") # FOR OUTPUT
		or die "$perlScript.temp could not be opened for output.\n";
	    <SCRIPT_setUserPerl>;
	    print REVISED_SCRIPT_setUserPerl "#!$userPerlExecutable\n";
	    while ($scriptLine = <SCRIPT_setUserPerl>){
		print REVISED_SCRIPT_setUserPerl $scriptLine;
	    }
	    close(SCRIPT_setUserPerl);
	    close(REVISED_SCRIPT_setUserPerl);
	    system("mv -f $perlScript.temp $perlScript")
		and die "The contents of $perlScript could not be overwritten with those of $perlScript.temp.\n";
	}
    }
    system("chmod u+x $perlScript")
	and system("chmod g+x $perlScript")
	and die "$perlScript could not be made executable.\n";
}

sub setUpPerl{
    
    require(5.006);
    &requireModules("Pod::Usage", "Log::Log4perl", "Config::IniFiles", "Getopt::Long",
		    "File::Copy", "Carp", "FileHandle", "Bio::Tools::Phylo::PAML");
    return `which perl 2>/dev/null | tr -d [:space:]`;
}

sub requireModules{
    my @requiredModules = @_;

    my $module, $moduleFile;

    print "Checking for required Perl modules...\n";
    foreach $module(@requiredModules){
	$moduleFile = $module;
	$moduleFile =~ s/\:\:/\//g;
	eval{ require("$moduleFile.pm"); import $module; };
	if ($@){
	    die "The required module $module was not found:\n$@\n";
	}
    }
}

sub createLocalDispatcherDirectory{
    my ($standardDispatcherDirectory, $localDispatcherDirectory) = @_;

    my $line, $serial, $parallel;

    open(STANDARD_createLocalDispatcherDirectory, "properties/$standardDispatcherDirectory")
	or die "$standardDispatcherDirectory could not be opened.\n";
    open(LOCAL_createLocalDispatcherDirectory, ">properties/$localDispatcherDirectory") # FOR OUTPUT
	or die "$localDispatcherDirectory could not be opened for output.\n";
    while ($line = <STANDARD_createLocalDispatcherDirectory>){
	if ($line =~ /^serial\s*=\s*(\S+)$/){
	    $serial = $1;
	}
	elsif ($line =~ /^parallel\s*=\s*(\S+)$/){
	    $parallel = $1;
	}
	elsif ($line =~ /^\S+serial\s*=\s*(\S+)$/){
	    $line =~ s/$1/$serial/;
	}
	elsif ($line =~ /^\S+parallel\s*=\s*(\S+)$/){
	    $line =~ s/$1/$parallel/;
	}
	print LOCAL_createLocalDispatcherDirectory $line;
    }
    close(STANDARD_createLocalDispatcherDirectory);
    close(LOCAL_createLocalDispatcherDirectory);
}

sub transformFile{
    my ($oldFilename, $newFilename, %substitutions) = @_;

    my $line, $patternToReplace, $suffix, $outputFilename;

    open(OLD_transformFile, $oldFilename)
	or die "$oldFilename could not be opened.\n";
    $outputFilename = ($oldFilename eq $newFilename) ? "$oldFilename.temp" : $newFilename;
    open(NEW_transformFile, ">$outputFilename") # FOR OUTPUT
	or die "$outputFilename could not be opened for output.\n";
    while ($line = <OLD_transformFile>){
	foreach $patternToReplace(keys(%substitutions)){
	    if ($substitutions{$patternToReplace} =~ /^\$1(.*)$/){
		$suffix = $1;
		$line =~ s/$patternToReplace/$1$suffix/g;
	    }
	    else{
		$line =~ s/$patternToReplace/$substitutions{$patternToReplace}/g;
	    }
	}
	print NEW_transformFile $line;
    }
    close(OLD_transformFile);
    close(NEW_transformFile);
    if ($oldFilename eq $newFilename){
	system("mv -f $outputFilename $newFilename")
	    and die "The contents of $newFilename could not be overwritten with those of $outputFilename.\n";
    }
}

sub determineGridType{

    my %statusCommands = ("sge", "qhost",
			  "condor", "condor_status");
    my $gridManager;

    foreach $gridManager(keys(%statusCommands)){
	unless (system("$statusCommands{$gridManager} 1>/dev/null 2>/dev/null")){
	    print "Using grid type $gridManager!\n";
	    return ($gridManager);
	}
    }
    print "No grid detected!\n";
    &transformFile("workflow.config.token", "workflow.config.token", '^max\.proc\.pool\.count\=\d+', "max.proc.pool.count=100",
		   '^max\.cmd\.set\.disp\.count\=\d+', "max.cmd.set.disp.count=16", '^max\.htc\.jobs\.count\=\d+', "max.htc.jobs.count=16");
    return ("");
}

sub makeDirectory{
    my ($directory) = @_;
    
    unless (mkdir($directory)){
	die "The directory $directory could not be created.\n";
    }
}

sub enterDirectory{
    my ($directory) = @_;

    if (chdir($directory)){
	unshift(@directoryStack, $directory);
    }
    else{
	die "The directory $directory could not be entered.\n";
    }
    return;
}

sub returnToPreviousDirectory{
    
    my $parentOrPrevious;

    unless (scalar(@directoryStack) >= 2){
	die "Internal error:  no previous directory\n";
    }
    shift(@directoryStack);
    chdir($directoryStack[0])
	or die "The directory $parentOrPrevious could not be entered.\n";
}

sub projectBaseDirectory{

    my ($projectBaseDir, $programExecutedSubpart, @programExecutedSubparts);
    
    @programExecutedSubparts = split(/\//, $COMMAND_EXECUTED);
    pop(@programExecutedSubparts);
    my $projectBaseDir = join("/", @programExecutedSubparts);
    if ($projectBaseDir eq "."){
	$projectBaseDir = `pwd`;
	chomp($projectBaseDir);
    }
    return $projectBaseDir;
}

sub unpackWorkflowTarFile{

    my $wfTarFilePattern = '^wf\-([\d\.]+)\.tar\.gz$';
    my $currentDirectory = &currentDirectory();
    my $potentialTarFile, $listIndex, $duplicateFileErrorMessage, $untarCommand, $workflowDirectory, @potentialTarFiles;
    
    $workflowDirectory = "$currentDirectory/wf";
    &enterDirectory($workflowDirectory);
    opendir(WORKFLOW_DIR_unpackWorkflowTarFile, $workflowDirectory)
	or die "The directory $workflowDirectory could not be opened.\n";
    @potentialTarFiles = readdir(WORKFLOW_DIR_unpackWorkflowTarFile);
    closedir(WORKFLOW_DIR_unpackWorkflowTarFile);
    for ($listIndex = 0; $listIndex < scalar(@potentialTarFiles); $listIndex++){
	unless ($potentialTarFiles[$listIndex] =~ /$wfTarFilePattern/){
	    splice(@potentialTarFiles, $listIndex, 1);
	    $listIndex--;
	}
    }
    if (scalar(@potentialTarFiles) == 0){
	die "No .tar.gz file for Workflow was found in $currentDirectory!\n";
    }
    elsif (scalar(@potentialTarFiles) > 1){
	$duplicateFileErrorMessage = "Too many Workflow tar files:\n" . join("\n", @potentialTarFiles)
	    . "\nDo not attempt to overwrite an existing IDEA installation.\n";
	die $duplicateFileErrorMessage;
    }
    &unpackArchive($potentialTarFiles[0], "$workflowDirectory/bin");
#    system("mv $workflowDirectory/jars/wf.jar.ALT $workflowDirectory/jars/wf.jar")
#	and die "$workflowDirectory/jars/wf.jar could not be updated.\n";
    return $workflowDirectory;
}

sub findArchive{
    
    my @archiveExtensions = (".tar.gz", ".zip");
    my $listIndex, $archiveExtension, @candidates;

    unless (scalar(@directoryStack) >= 1){
	die "Internal error:  no current directory\n";
    }
    opendir(CURRENT_DIR_findArchive, $directoryStack[0])
	or die "The directory $directoryStack[0] could not be opened.\n";
    @candidates = readdir(CURRENT_DIR_findArchive);
    closedir(CURRENT_DIR_findArchive);
    CANDIDATE: for ($listIndex = 0; $listIndex < scalar(@candidates); $listIndex++){
	foreach $archiveExtension(@archiveExtensions){
	    if ($candidates[$listIndex] =~ /$archiveExtension$/){
		next CANDIDATE;
	    }
	}
	
	# pattern not matched
	splice(@candidates, $listIndex, 1);
	$listIndex--;
    }
    if (scalar(@candidates) == 0){
	die "No archive files were found in $directoryStack[0]!\n";
    }
    elsif (scalar(@candidates) > 1){
	die ("Too many archives in $directoryStack[0]:\n" . join("\n", @candidates) . "\n");
    }
    return $candidates[0];
}
	
sub unpackArchive{
    my ($tarFile, $expectedResultingDirectory) = @_;

    my $untarCommand = (($tarFile =~ /\.tar\.gz$/) ? "gtar xzf " : "unzip -qq ") . $tarFile;
    my $alternateUntarCommand = $untarCommand;

    print "Unpacking $tarFile...\n";
    if (system($untarCommand)){
	$alternateUntarCommand =~ s/^gtar/tar/;
	if (($alternateUntarCommand eq $untarCommand) || system($alternateUntarCommand)){
	    die "The command [$alternateUntarCommand] failed.\n";
	}
    }
    unless (-d $expectedResultingDirectory){
	die "The command [$untarCommand] failed to create the expected directory $expectedResultingDirectory.\n";
    }
    return $expectedResultingDirectory;
}

sub lineNumber{
    my ($i, $filename) = @_;

    my $line;
    my $linesSeen = 0;
    open(FILE_lineNumber, $filename)
	or die "$filename could not be opened.\n";
    while ($line = <FILE_lineNumber>){
	$linesSeen++;
	if ($linesSeen == $i){
	    close(FILE_lineNumber);
	    return $line;
	}
    }
    close(FILE_lineNumber);
    return "";
}

sub currentDirectory{

    return (defined(@directoryStack) && scalar(@directoryStack)) ? $directoryStack[scalar(@directoryStack) - 1] : "";
    
}

sub listToString{
    my @l = @_;

    my $e, $rv;

    $rv = "(";
    foreach $e(@l){
	$rv.= "$e, ";
    }
    return ($rv eq "(") ? "()" : (substr($rv, 0, -2) . ")");
}

sub printList{
    my @l = @_;

    print (&listToString(@l) . "\n");
}
