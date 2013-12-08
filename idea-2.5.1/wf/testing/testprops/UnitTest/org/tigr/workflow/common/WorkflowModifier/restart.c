#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

int main(int argc, char* argv[]){
  if (argc != 2){
    printf("%s\n", "You must supply exactly one argument:  an amount of memory to allocate (in bytes).");
  }
  printf("%s%d%s\n", "Allocating ", atoi(argv[1]), "...");
  malloc(atoi(argv[1]));
  sleep(100);
};
