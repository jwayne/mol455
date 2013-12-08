#!/bin/bash
#trap "sig 1" USR1 # useful in soft limits and suspensions
#trap "sig 2" USR2 # useful in hard limits and terminations

# Needed parameters to this script
# JOB_ID  - $1
# SIGNAL  - $2
# CKPT_DIR - $3


# JOB_ID
JOB_ID=$1

# Signal (either USR1 or USR2)
signal=$2

# Checkpt directory, where all the reshceduled jobs 
# are tracked, by touching a file.
CKPT_DIR=$3

# Complexes to preserved when rescheduling the job
COMPLEXES=$4

qalt_ret=0

queue=""

# Log file 
logfile=$CKPT_DIR/$JOB_ID.trap.log

# set SGE variables
export SGE_ROOT=/local/n1ge
export SGE_ARCH=`$SGE_ROOT/util/arch`
export SGE_CELL=tigr
export SGE_QMASTER_PORT=536
export SGE_EXECD_PORT=537
echo "`date` SGE Variables are: $SGE_ROOT $SGE_CELL $SGE_ARCH" >> $logfile

echo " " >> $logfile
echo " " >> $logfile
echo "*************** START TRAP FOR $JOB_ID *******************************" >> $logfile
echo "`date` Job $JOB_ID caught signal $signal with Complexes :$COMPLEXES:" >> $logfile

case "$signal" in 
     	#1|2) 
	# Resubmit in case of only USR1, which comes before SIGSTOP or SUSPEND 
     	1) 
		# Determine the queue name that this job is on
		echo "qstat -s r | grep $JOB_ID | awk '{print \$8}' | cut -d @ -f1 | uniq " >> $logfile
		queue=`qstat -s r | grep $JOB_ID | awk '{print \$8}' | cut -d @ -f1 | uniq`
		echo "$JOB_ID is currently on $queue" >> $logfile

		# If the job is on fast.q, then reschedule it to the default.q
		# If the job is on default.q, then reschedule it to the marathon.q	
   	   	echo "Job $JOB_ID  is being rescheduled " >> $logfile
	
		# default reschedule qcomplex is marathon
		#if [ "$queue" = "default.q" ]; then 
   	   	#    echo "qalter -l marathon '' -ckpt '' $JOB_ID " >> $logfile
		#    qalter -l marathon -ckpt '' $JOB_ID >> $logfile 
		#    qalt_ret=$?
		#elif [ "$queue" = "fast.q" ]; then 

		if [ "$queue" = "fast.q" ]; then 
		    # Qalter to remove all the complexes from the job
		    echo "qalter -l '' $JOB_ID " >> $logfile 
		    qalter -l '' $JOB_ID >> $logfile 
		    qalt_ret=$?
   	   	    echo "Job $JOB_ID  qalter complex is $qcomplex" >> $logfile
		else 
		    # If the queue name is either default.q or queue name is not found 
		    #qalter -l '' -l marathon -ckpt '' $JOB_ID >> $logfile 
		    qalter -l '' $JOB_ID >> $logfile 
		    qalt_ret=$?
		fi

		# Qalter to preserve the complexes that make sense.
		if [ -n "$COMPLEXES" ] && [ "$COMPLEXES" != " " ]; then 
		  echo "Job $JOB_ID is being qaltered with Complexes $COMPLEXES" >> $logfile
		  echo "qalter $COMPLEXES $JOB_ID " >> $logfile 
		  qalter $COMPLEXES $JOB_ID >> $logfile 
		  qalt_ret=$? 
	   	  echo " qalter $JOB_ID ret value $qalt_ret " >> $logfile
		fi

		echo "$SGE_ROOT/bin/$SGE_ARCH/qmod -rj $JOB_ID" >> $logfile
		$SGE_ROOT/bin/$SGE_ARCH/qmod -rj $JOB_ID
		qmod_ret=$?

	   	echo " qmod ret value $qmod_ret " >> $logfile

		if [ $qmod_ret -eq 0 ]; then 
			# Touch a file indicating that the current job is rescheduled.
			echo "Touching  a resched file for $JOB_ID " >> $logfile

			# For single jobs
			touch $CKPT_DIR/"$JOB_ID.resched"
		fi
		
		if [ $qalt_ret -eq 0 ] && [ $qmod_ret -eq 0 ]; then
			echo "Rescheduling of $JOB_ID is successful" >> $logfile
			exit $ret
		fi

     	;;
     	*)   echo "Caught signal $signal but did nothing" >> $logfile;; 
esac

echo "*************** END TRAP FOR $JOB_ID *******************************" >> $logfile
echo " "

exit 1;
