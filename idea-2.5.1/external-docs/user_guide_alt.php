 <?php
$ip = gethostbyname($_SERVER['REMOTE_ADDR']);
echo "Your ip is [$ip].\n";
list($first, $second, $third, $fourth) = split("\.", $ip);
$number = ($first * 1000000000) + ($second * 1000000) + ($third * 1000) + $fourth;
#if ((($number >= 10090135001) && ($number <= 10090135254)) || (($number >= 10090136001) && ($number <= 10090136254))){
#  header( 'Location: http://idea.igs.umaryland.edu' );
# }
# else{
#   header( 'Location: external_guide.html' );
# }
?>
