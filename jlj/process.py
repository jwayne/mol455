#!/bin/python

import argparse
from singlerun import SingleRun


def main():
    # take protein aln file in CSA format from caprasingh08 dataset
    parser = argparse.ArgumentParser(description="produce dn/ds output from simple aln file")
    parser.add_argument('fnames_in', nargs="+",
        help="input files")
    parser.add_argument('-o', dest='dirname_out', type=str, default=None,
        help="output directory. Defaults to new directory in folder of first positional argument")
    parser.add_argument('-n', dest='n_bootstrap', type=int, default=1,
        help="number of bootstrapped trees. Defaults to 1")
    parser.add_argument('-b', dest='analyze_boostrap', action='store_true',
        help="run codeML on all bootstrap trees")
    args = parser.parse_args()
    
    singlerun = SingleRun(args.fnames_in, args.dirname_out)
    singlerun.preprocess(args.n_boostrap, args.analyze_boostrap):


if __name__ == "__main__":
    main()
