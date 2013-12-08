#!/bin/bash
# This is the deployment script for workflow.

# Initialize the variables
InstallDir=$PWD
UntarDir=$PWD
IdGenClass="org.tigr.antware.shared.idgen.FileIDGenerator"
BashLoc="/bin/bash"
IdFileDir="${InstallDir}/server-conf"
GridType=""

# This supprot type for the grid 
SupportType="local"
HtcHost=""
HtcPort=""
HtcBase=""
MockServerBase=""
SgeRoot="/local/n1ge"
SgeCell="default"

####################### Functions ##########################################

function prompt_bash_location {
	echo ""
	echo "*****************************************************************"
	echo "BASH EXECUTABLE:  This bash executable is used by the Workflow's" 
	echo "JavaSystemCommandProcessor to execute the system commands."
	echo "The default bash executable used by Workflow is: $BashLoc."
	echo "*****************************************************************"
	echo ""
	echo -n "Enter a different bash to change the default -->"
	read TERM

	if [ ! -z "$TERM" ]; then 
		#echo -n "Using bash from location $TERM."
		BashLoc=$TERM
	fi

	echo ""
	echo "Using bash location $BashLoc."
}


function prompt_deployment_dir {
	echo ""
	echo ""
	echo "*********************************************************************"
	echo "DEPLOYMENT DIRECTORY:  This is the location where the Workflow is" 
	echo "installed." 
	echo "The default install location is user's working directory: $InstallDir"
	echo "*********************************************************************"
	echo ""
	echo -n "Enter a different install location to change the default -->"
	read TERM
	
	#Change the installation directory, if any entered by user
	if [ ! -z "$TERM" ]; then 
		#echo -n "Changing deployment directory to $TERM."
		InstallDir=$TERM
	fi
	
	echo ""
	#echo "Deployment directory is $InstallDir."
	
	# Make sure the directory path is a valid directory. 
	# If the directory doesn't exists, create it.
	if [ -d "$InstallDir" ]; then
		# Check permissions to the directory
		if [ -x "$InstallDir" ]; then
			echo "The Installation directory is $InstallDir"
		else
			echo "Permission denied to the Installation directory $InstallDir"
			exit 1
		fi
	else
		echo "$InstallDir doesn't exist. Creating it."
		mkdir $InstallDir
		# if the directory couldn't be created, exit.
		if [ $? != 0 ]; then 
			echo "Could not create the install directory $InstallDir." 
			exit $?
		fi
	fi

	# set variable WF_ROOT, used in the workflow.config and setenv.tcsh files.
	WF_ROOT=${InstallDir}
	echo "WF_ROOT variable is set to $WF_ROOT"
}


function prompt_id_generator_class {

	# Prompt user for the source class to be used for workflow id generation. 
	# The default implementation generates id's reading a seed from a file.
	echo ""
	echo ""
	echo "*****************************************************************"
	echo "ID GENERATOR : This Java class is used by the Workflow to generate "
	echo "the unique ids of the Command and CommandSets. The default Workflow"
	echo "id generator class is: $IdGenClass,			   	 " 	
	echo "which is a file based id generator.				 "
        echo "Please specify a custom id generator API, if you have one.         "
	echo "*****************************************************************"
	echo ""
	echo -n "Enter a different qualified class name to change the default -->" 
	read TERM

	#Change the installation directory, if any entered by user
	if [ ! -z "$TERM" ]; then 
		#echo "Changing id generator class to $TERM" 
		IdGenClass=$TERM
	fi

	echo ""
	echo "Id generator class to be used is $IdGenClass"
}

function prompt_idfile_location {
	echo ""
	echo "*****************************************************************"
	echo "idfile: This file is used for id storage when running "
 	echo "workflows on your local machine .  Specify the directory "
 	echo "where you want this file to reside.  Make sure it is writable "
 	echo "by everyone. " 
	echo "The default location is: $IdFileDir. "
	echo "*****************************************************************"
	echo ""
	echo -n "Enter a different directory to change the default -->"
	read TERM
	
	if [ ! -z "$TERM" ]; then 
		#echo -n "The idfile directory is $TERM."
		IdFileDir=$TERM
	fi

	# Create idfile for local workflow runs
	if [ ! -f ${IdFileDir}/idfile ];
	then
		echo "1" > ${IdFileDir}/idfile;
	fi


	echo ""
	echo "Using fileid directory $IdFileDir."
}

function prompt_grid_type {
	# Prompt the user for grid type, if used any. Supported grids are 
	# 'condor' and 'sge'.
	echo ""
	echo ""
	echo "*********************************************************************"
	echo "GRID TYPE: This is the name of the grid software, if used any by the" 
	echo "the user. Supported grid types are 'condor' and 'sge'." 
	echo "Skip this, if no grid support is required."
	echo "*********************************************************************"
	echo ""
	echo -n "Enter a grid type to be supported -->" 
	read TERM

	#Set the grid type that is entered by the user
	if [ ! -z "$TERM" ]; then 
		GridType=$TERM

		# Prompt for SGE ROOT and SGE CELL variables
		if [ "$GridType" = "sge" ]; then 
			echo "*****************************************************************"
			echo "SGE ROOT : This is the location where SGE is installed. 	"
			echo "Default value is '/local/n1ge'." 
			echo " "
			echo "SGE CELL : This is the primary SGE CELL name. 		"
			echo "Default value is 'default'." 
			echo "*****************************************************************"

			echo -n "Enter SGE ROOT variable -->" 
			read TERM

			if [ ! -z "$TERM" ]; then
				SgeRoot=$TERM	
			fi

			echo " "
			echo -n "Enter SGE CELL variable -->" 
			read TERM

			if [ ! -z "$TERM" ]; then
				SgeCell=$TERM	
			fi
			echo "SGE_ROOT is set as $SgeRoot and SGE_CELL is set as $SgeCell"
		fi 

		if [ "$GridType" = "condor" ] || [ "$GridType" = "sge" ]; then 
		#	# Prompt for the type of the grid support. Available types are
		#	# 'local' and 'server' 
		#	echo ""
		#	echo ""
		#	echo "*********************************************************************"
		#	echo "SUPPORT TYPE: This is the kind of support requested for the grid. The"
		#	echo "available types are: "
		#	echo " local : Jobs are submitted to the grid from the local machine." 
		#	echo " server: Jobs are submitted to the grid through htcservice."
		#	echo "Default support type is 'local'." 
		#	echo "*********************************************************************"
		#	echo ""
		#	echo -n "Enter the kind of support to be supported -->" 
		#	read TERM
		#	if [ ! -z "$TERM" ]; then
		#		SupportType=$TERM	
		#	fi
		#
			echo ""
			echo "Grid support of type $SupportType to be provided for $GridType"

		else 
			echo "Grid type $GridType is not supported in this release. Exiting."
			exit 1;
		fi
	else 
		echo ""
		echo "No Grid support is requested."
	fi
}

function prompt_htcservice_details {
	echo ""
	echo ""
	echo "*************************************************************************"
	echo "HTC_BASE: This is the directory where the htcservice is installed."
	echo "*************************************************************************"
	echo ""

	echo -n "Enter the base directory of htcservice -->"
	read TERM
	if [ ! -z "$TERM" ]; then 
		HtcBase=$TERM
	fi

	if [ -z "$HtcBase" ]; then
		echo "Warning!  No base directory for HTC service!"
		HtcBase="."
	fi
}

function prompt_local_support_details {
	echo ""
	echo ""
	echo "*************************************************************************	"
	echo "MOCK_SERVER_BASE: This is the directory where job script and event log   	"
	echo "files are placed. This directory should have world read/write permissions	"
	echo "with sticky bit turned on. 						"
	echo "*************************************************************************	"
	echo ""

	# set variable MockServerBase, used in the sgemockserver.conf file.
	echo -n "Enter the base directory of mockserver -->"
	read TERM
	if [ ! -z "$TERM" ]; then 
		MockServerBase=$TERM
	fi

	if [ -z "$MockServerBase" ]; then
		echo "Warning!  No base directory for MockServer! Using current directory"
		MockServerBase="."
	fi
}


echo ""
echo "*****************************************************************"
echo "                 WORKFLOW 3.1.1 INSTALLATION                       "
echo "*****************************************************************"
echo ""


# Prompt the user for bash location. This is used by the 
# JavaSystemCommandProcessor to execute the system commands.
prompt_bash_location


# Prompt user the installtion directory
prompt_deployment_dir 

# Prompt user for the source class to be used for workflow id generation. 
# The default implementation generates id's reading a seed from a file.
prompt_id_generator_class

prompt_idfile_location

# Prompt the user for grid type, if used any. Supported grids are 
# 'condor' and 'sge'.
prompt_grid_type

# Prompt the user for the htcservice instllation location (HTC_BASE), if 
# support for a grid type is requested.
if [ ! -z "$GridType" ] && [ "$SupportType" = "server" ]; then 
	prompt_htcservice_details

	# set environment variable HTC_BASE. This variable is used to locate the
	# the htcservice
	export HTC_BASE="$HtcBase"
	echo "HTC_BASE is $HTC_BASE"
elif [ ! -z "$GridType" ] && [ "$SupportType" = "local" ]; then 
	prompt_local_support_details
fi

# Move everything from the directory where the tar file is untared to 
# the instllation directory.
if [ ! "$UntarDir" == "$InstallDir" ]; then
	#echo "Installation directory $InstallDir is different from the working directory $UntarDir."
	cp -pr $UntarDir/* $InstallDir
fi

# Change directory to the installation directory
cd $InstallDir

# Replace tokens in 'workflow.config.token'  file and create a 'workflow.props' file
sed -e "s:<WF_ROOT>:${WF_ROOT}:g" -e "s:<USER>:${USER}:g" -e "s:<BASH_LOC>:$BashLoc:g" -e \
				"s:<GRID_TYPE>:$GridType:g" -e "s:<ID_GEN_CLASS>:$IdGenClass:g" \
				 -e "s:<IDFILE_LOC>:$IdFileDir:g" \
						workflow.config.token > workflow.config
						
# Replace <IDFILE_LOC> token in the workflow.config.token file
#sed -e "s:<IDFILE_LOC>:$IdFileDir:g" 
						 
# Replace <BASH_LOC> token in the switch.token file
sed -e "s:<BASH_LOC>:$BashLoc:g" switch.sh.token > switch.sh

# Replace SGE related tokens in the sge_submitter.token file
sed -e "s:<SGE_ROOT>:$SgeRoot:g"  -e "s:<SGE_CELL>:$SgeCell:g" bin/sge_submitter.sh.token > bin/sge_submitter.sh

# Replace <BASH_LOC> token in the runJava.token file
sed -e "s:<BASH_LOC>:$BashLoc:g" bin/runJava.token > bin/runJava

# Replace tokens in 'exec_env.tcsh.token'  file and create a 'exec_env.tcsh' file
sed -e "s:<WF_ROOT>:${WF_ROOT}:g" exec_env.tcsh.token > exec_env.tcsh 

# Replace tokens in 'exec_env.bash.token'  file and create a 'exec_env.bash' file
sed -e "s:<WF_ROOT>:${WF_ROOT}:g" -e "s:<BASH_LOC>:$BashLoc:g" exec_env.bash.token > exec_env.bash 

# Replace tokens in server related configuration files 
sed -e "s:<WF_ROOT>:${WF_ROOT}:g" server-conf/log4j_server.conf.token > server-conf/log4j_server.conf 

sed -e "s:<WF_ROOT>:${WF_ROOT}:g" -e "s:<GRID_TYPE>:$GridType:g" -e "s:<MOCK_SERVER_BASE>:$MockServerBase:g" \
				server-conf/sge_mockserver.conf.token > server-conf/sge_mockserver.conf 

# Change permissions of all files
chmod -R 755 *


ln -sf RUN_PROGRAM RunWorkflow 
ln -sf RUN_PROGRAM CreateWorkflow
ln -sf RUN_PROGRAM WorkflowMonitor
ln -sf RUN_PROGRAM MonitorWorkflow
ln -sf RUN_PROGRAM RunTestSuite
ln -sf RUN_PROGRAM WorkflowEditor
ln -sf RUN_PROGRAM EditTemplate
ln -sf RUN_PROGRAM KillWorkflow
ln -sf RUN_PROGRAM ControlWorkflow
ln -sf RUN_PROGRAM CheckWorkflow
ln -sf RUN_PROGRAM CleanWorkflowRegistry

echo "Finished creating links to RUN_PROGRAM"

# Now, create softlinks from switch.sh script to workflow tools
ln -sf switch.sh create_workflow
ln -sf switch.sh run_workflow
ln -sf switch.sh workflow_monitor
ln -sf switch.sh monitor_workflow
ln -sf switch.sh run_test_suite
ln -sf switch.sh workflow_editor
ln -sf switch.sh edit_template
ln -sf switch.sh check_workflow
ln -sf switch.sh control_workflow
ln -sf switch.sh kill_workflow
ln -sf switch.sh clean_workflow_registry

echo "Finished creating links to switch.sh"

# Create a sot link htc.conf. This defults to the sge_mockserver.conf
ln -sf ${WF_ROOT}/server-conf/sge_mockserver.conf ${WF_ROOT}/server-conf/htc.conf

# Create a soft link in the server-conf directory, to the right conf file based on the 
# grid support type and the HTC_BASE area nto the server-conf directory.
if [ ! -z "$HTC_BASE" ]; then 
	if [ "$GridType" = "sge" ]; then
		cp "$HTC_BASE/request/conf/request.conf"	server-conf/
		# Remove and create a link to the request.conf
		rm ${WF_ROOT}/server-conf/htc.conf
		ln -sf ${WF_ROOT}/server-conf/request.conf ${WF_ROOT}/server-conf/htc.conf
	fi
fi

# delete all the token files
find . | grep ".token" | xargs rm -f

# delete all the svn directories, if any
find . -name ".svn" | xargs -r rm -r
echo ""
echo "Workflow is installed under $InstallDir."
echo "Source $InstallDir/exec_env.tcsh file to start using the tools."
echo ""
echo "Workflow deployment complete !!"
exit 0
