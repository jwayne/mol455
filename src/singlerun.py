#!/usr/bin/python
"""
Workflow for running codeml given any set of input data files.
"""
import argparse
import os
from preprocess import (fetch_homologs, fetch_seqs, run_clustalw2, run_pal2nal,
                        run_phyml, convert_maintree, convert_boottrees,
                        make_ctl)
from utils import ts_str, bin_dir


AUTOMATIC = "AUTOMATIC"


class SingleRun(object):

    def __init__(self, fnames_in, dirname_out, analyze_bootstrap):
        """
        Categorize input files and create the appropriate output directory,
        stored at `self.dirname_out`.

        @param fnames_in:
            absolute paths of input files
        """
        inputs = ('homolog_gbids',
                  'homolog_nucs', 'homolog_aas',
                  'aa_aln', 'codon_aln',
                  'tree', 'boot_trees',
                  'ctl_template')
        inputs = dict((inp, None) for inp in inputs)

        fnames_in = [os.path.abspath(fname_in) for fname_in in fnames_in]
        for fname_in in fnames_in:
            # Use filename to determine what input type it is
            # TODO: Open up file to sanity check inputs, and provide greater flexibility
            fname_part = os.path.split(fname_in)[-1]
            if fname_in.endswith(".gbids"):
                if inputs['homolog_gbids']:
                    raise ValueError("Duplicate lists of gbids")
                inputs['homolog_gbids'] = fname_in
            elif fname_part == "nucleotides.fasta":
                if inputs['homolog_nucs']:
                    raise ValueError("Duplicate nucleotide sequence files")
                inputs['homolog_nucs'] = fname_in
            elif fname_part == "protein.fasta":
                if inputs['homolog_aas']:
                    raise ValueError("Duplicate protein sequence files")
                inputs['homolog_aas'] = fname_in
            elif fname_part == "protein.aln":
                if inputs['aa_aln']:
                    raise ValueError("Duplicate protein alignment files")
                inputs['aa_aln'] = fname_in
            elif fname_in.endswith(".aln"):
                if inputs['codon_aln']:
                    raise ValueError("Duplicate codon alignment files")
                inputs['codon_aln'] = fname_in
            elif fname_in.endswith("_tree.txt"):
                if inputs['tree']:
                    raise ValueError("Duplicate tree files")
                if inputs['boot_trees']:
                    raise ValueError("Do not provide both tree and bootstrap trees")
                inputs['tree'] = fname_in
            elif fname_in.endswith("_trees.txt"):
                if inputs['boot_trees']:
                    raise ValueError("Duplicate bootstrap tree files")
                if inputs['tree']:
                    raise ValueError("Do not provide both tree and bootstrap trees")
                inputs['boot_trees'] = fname_in
            elif fname_in.endswith(".ctl"):
                if inputs['ctl_template']:
                    raise ValueError("Duplicate ctl template files")
                inputs['ctl_template'] = fname_in
            else:
                raise NotImplementedError()

        if analyze_bootstrap == AUTOMATIC:
            if inputs['tree']:
                analyze_bootstrap = False
            elif inputs['boot_trees']:
                analyze_bootstrap = True
            else:
                # If no tree provided, default to not analyzing bootstrap trees
                analyze_bootstrap = False

        if not dirname_out:
            head, tail = os.path.split(fnames_in[0])
            os.chdir(head)
            dirname_out = ".".join(tail.split(".")[:-1]) + ts_str()
            dirname_out = os.path.abspath(dirname_out)
            if os.path.exists(dirname_out):
                raise NotImplementedError()
            os.mkdir(dirname_out)

        self.inputs = inputs
        self.dirname_out = dirname_out


    def preprocess(self, n_bootstrap, analyze_bootstrap):
        """
        Preprocess input files by creating necessary inputs for codeml analysis.
        These inputs are stored in `self.dirname_out`.
        """
        os.chdir(self.dirname_out)

        # TODO: Check that files are coordinated with each other, i.e. contain the
        #       same sequences, etc
        def _check_homolog_gbids():
            if not self.inputs['homolog_gbids']:
                if not self.inputs['gbid']:
                    raise ValueError("No input sequence ids provided")
                # BLAST search
                self.inputs['homolog_gbids'], a, b = fetch_homologs(self.inputs['gbid'])
                if self.inputs['homolog_nucs']:
                    sys.stderr.write("Overwriting input homolog_nucs\n")
                    self.inputs['homolog_nucs'] = a
                if self.inputs['homolog_aas']:
                    sys.stderr.write("Overwriting input homolog_aas\n")
                    self.inputs['homolog_aas'] = b
        def _check_homolog_nucs():
            if not self.inputs['homolog_nucs']:
                if not self.inputs['homolog_gbids']:
                    # BLAST search
                    raise NotImplementedError()
                    _check_homolog_gbids()
                else:
                    # UNIPROT search
                    self.inputs['homolog_nucs'], a = fetch_seqs(self.inputs['homolog_gbids'])
                    if self.inputs['homolog_aas']:
                        sys.stderr.write("Overwriting input homolog_aas\n")
                    self.inputs['homolog_aas'] = a
        def _check_homolog_aas():
            if not self.inputs['homolog_aas']:
                if not self.inputs['homolog_gbids']:
                    # BLAST search
                    raise NotImplementedError()
                    _check_homolog_gbids()
                else:
                    # UNIPROT search
                    a, self.inputs['homolog_aas'] = fetch_seqs(self.inputs['homolog_gbids'])
                    if self.inputs['homolog_nucs']:
                        sys.stderr.write("Overwriting input homolog_nucs\n")
                    self.inputs['homolog_nucs'] = a
        def _check_aa_aln():
            if not self.inputs['aa_aln']:
                _check_homolog_aas()
                self.inputs['aa_aln'] = run_clustalw2(self.inputs['homolog_aas'])
        def _check_codon_aln():
            if not self.inputs['codon_aln']:
                _check_homolog_nucs()
                _check_aa_aln()
                self.inputs['codon_aln'] = run_pal2nal(
                        self.inputs['aa_aln'], self.inputs['homolog_nucs'])
        def _check_tree():
            if not self.inputs['tree']:
                _check_aa_aln()
                self.inputs['tree'], _ = run_phyml(self.inputs['aa_aln'], n_bootstrap)
        def _check_boot_trees():
            if not self.inputs['boot_trees']:
                _check_aa_aln()
                _, self.inputs['boot_trees'] = run_phyml(self.inputs['aa_aln'], n_bootstrap)

        _check_codon_aln()
        if not analyze_bootstrap:
            _check_tree()
            self.bootstrap = False
            # Get rid of confidences in tree
            fname_tree = convert_maintree(self.inputs['tree'])
            # Generate ctl file.
            self.ctl = make_ctl(self.inputs['codon_aln'], fname_tree,
                    self.inputs["ctl_template"])
        else:
            _check_boot_trees()
            self.bootstrap = True
            # Split up bootstrap trees
            fname_trees = convert_boottrees(self.inputs['boot_trees'])
            # Generate ctl file.
            self.ctls = []
            for fname_tree in fname_trees:
                self.ctls.append(make_ctl(self.inputs['codon_aln'], fname_tree,
                        self.inputs["ctl_template"]))


    def process(self):
        print "Run the following--"
        print "cd %s" % self.dirname_out
        print "%s/../idea-2.5.1/idea" % bin_dir()
        print "Then go to File > Load configuration > codonml.ctl"
        print "I think."


def main():
    # take protein aln file in CSA format from caprasingh08 dataset
    parser = argparse.ArgumentParser(description="produce dn/ds output from simple aln file")
    parser.add_argument('fnames_in', nargs="+",
        help="input files in any format")
    parser.add_argument('-o', dest='dirname_out', type=str, default=None,
        help="output directory. Defaults to new directory in folder of first positional argument")
    parser.add_argument('-n', dest='n_bootstrap', type=int, default=1,
        help="number of bootstrapped trees. Defaults to 1")
    parser.add_argument('-b', dest='analyze_bootstrap', action='store_true', default=AUTOMATIC,
        help="run codeML on all bootstrap trees, and analyze the resulting dnds distribution per site")
    args = parser.parse_args()
    
    singlerun = SingleRun(args.fnames_in, args.dirname_out, args.analyze_bootstrap)
    singlerun.preprocess(args.n_bootstrap, args.analyze_bootstrap)
    singlerun.process()


if __name__ == "__main__":
    main()
