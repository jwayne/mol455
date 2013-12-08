<?php
#$ip = $_SERVER['REMOTE_ADDR'];
$ip = getIpAddress();
$serverIP = $_SERVER['SERVER_ADDR'];
list($first, $second, $third, $fourth) = split("\.", $ip);
$number = ($first * 1000000000) + ($second * 1000000) + ($third * 1000) + $fourth;
print "Your number is $number.";
#if (($number >= 134192132001) && ($number <= 134192135253)){
#  header( 'Location: http://idea.igs.umaryland.edu' );
# }
# else{
#   header( 'Location: main.html' );
# }

function getIpAddress(){
  return (empty($_SERVER['HTTP_CLIENT_IP'])
	  ? (empty($_SERVER['HTTP_X_FORWARDED_FOR'])
	     ?   $_SERVER['REMOTE_ADDR']
	     : $_SERVER['HTTP_X_FORWARDED_FOR'])
	  : $_SERVER['HTTP_CLIENT_IP']);
}

?>
