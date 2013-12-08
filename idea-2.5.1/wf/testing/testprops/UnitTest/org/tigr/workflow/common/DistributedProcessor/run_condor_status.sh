#! /bin/tcsh

echo "Running Condor status on : $1 \n"

condor_status $1 -l | grep -E "^Opsys"

#$var = ` condor_status $1 -l | grep -E "^OpSys" `; 

#echo "Output: \n $var";

exit 0;

