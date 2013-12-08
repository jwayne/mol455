#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

int main(int argc, char* argv[]){
  if (argc != 2){
    printf("%s\n", "You must supply exactly one argument:  an instance identifier (to be printed).");
  }
  printf("%s\n", argv[1]);
  sleep(60);
};
