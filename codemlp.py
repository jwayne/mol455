#!/usr/bin/python
"""
Workflow for running codeml given any set of input data files.
"""
import argparse
import os
import shutil
import sys
from codemlp.preprocess import (fetch_homologs, fetch_seqs, run_clustalw2, run_pal2nal,
                        run_phyml, convert_maintree, convert_boottrees,
                        make_ctl)
from codemlp.utils import ts_str, bin_dir, data_dir


AUTOMATIC = "AUTOMATIC"
INPUTS = ('refseq_id',                         # dna/aa sequence of interest
          'prot_ids', 'dna_fasta', 'aa_fasta', # homologues
          'prots_aln', 'codons_aln',           # alignments
          'tree', 'boot_trees',                # trees
          'ctl_template')


class SingleRun(object):

    def __init__(self, fnames_in, refseq_id_in, dirname_out, n_bootstrap, bootstrap):
        """
        Categorize input files and create the appropriate output directory,
        stored at `self.dirname_out`.

        @param fnames_in:
            absolute paths of input files
        """
        #XXX: it isn't checked for that inputs match each other!
        if not fnames_in and not refseq_id_in:
            raise ValueError("No input files provided")

        inputs = dict((inp, None) for inp in INPUTS)

        # Clean the input id and filenames somewhat.
        tmp = []
        for fname_in in fnames_in:
            if os.path.isdir(fname_in):
                tmp += (os.path.join(fname_in, x) for x in os.listdir(fname_in))
            else:
                tmp.append(fname_in)
        fnames_in = [os.path.abspath(fname_in) for fname_in in tmp]

        # Classify the input filenames by their extension.
        for fname_in in fnames_in:
            # TODO: Open up file to sanity check inputs, and provide greater flexibility
            # on filename extensions
            if fname_in.endswith(".protids"):
                if inputs['prot_ids']:
                    raise ValueError("Multiple lists of protein homologues provided")
                inputs['prot_ids'] = fname_in
            elif fname_in.endswith(".dna.fasta"):
                if inputs['dna_fasta']:
                    raise ValueError("Multiple nucleotide sequence files provided")
                inputs['dna_fasta'] = fname_in
            elif fname_in.endswith(".aa.fasta"):
                if inputs['aa_fasta']:
                    raise ValueError("Multiple protein sequence files provided")
                inputs['aa_fasta'] = fname_in
            elif fname_in.endswith(".aa.aln"):
                if inputs['prots_aln']:
                    raise ValueError("Multiple protein alignment files provided")
                inputs['prots_aln'] = fname_in
            elif fname_in.endswith(".codon.aln"):
                if inputs['codons_aln']:
                    raise ValueError("Multiple codon alignment files provided")
                inputs['codons_aln'] = fname_in
            elif fname_in.endswith("_phyml_tree.txt"):
                if inputs['tree']:
                    raise ValueError("Multiple tree files provided")
                if inputs['boot_trees']:
                    raise ValueError("Do not provide both tree and bootstrap trees")
                inputs['tree'] = fname_in
            elif fname_in.endswith("_phyml_trees.txt"):
                if inputs['boot_trees']:
                    raise ValueError("Multiple bootstrap tree files provided")
                if inputs['tree']:
                    raise ValueError("Do not provide both tree and bootstrap trees")
                inputs['boot_trees'] = fname_in
            elif fname_in.endswith(".ctl"):
                if inputs['ctl_template']:
                    raise ValueError("Multiple ctl template files provided")
                inputs['ctl_template'] = fname_in
            else:
                sys.stderr.write("Skipping file due to unrecognized extension: %s\n" % fname_in)

        # TODO: name dirname_out after first protein sequence in file
        if not dirname_out:
            os.chdir(data_dir())
            if refseq_id_in:
                out_prefix = refseq_id_in
            else:
                tail = os.path.split(fnames_in[0])[-1]
                out_prefix = tail.split(".")[0]
            dirname_out = "%s-%s" % (out_prefix, ts_str())
            dirname_out = os.path.abspath(dirname_out)
            if os.path.exists(dirname_out):
                raise NotImplementedError()
            os.mkdir(dirname_out)

        for fname_in in inputs.values():
            if fname_in:
                tail = os.path.split(fname_in)[-1]
                shutil.copy(fname_in, os.path.join(dirname_out, tail))

        if refseq_id_in:
            inputs['refseq_id'] = refseq_id_in

        if bootstrap == AUTOMATIC:
            if inputs['tree']:
                self.bootstrap = False
            elif inputs['boot_trees']:
                self.bootstrap = True
            else:
                # If no tree provided, default to not analyzing bootstrap trees
                self.bootstrap = False
        else:
            if bootstrap and n_bootstrap <= 1:
                raise ValueError("Cannot analyze bootstrap trees with n_bootstrap<=1")
            self.bootstrap = bootstrap
        self.n_bootstrap = n_bootstrap

        self.inputs = inputs
        self.dirname_out = dirname_out


    def preprocess(self):
        """
        Preprocess input files by creating necessary inputs for codeml analysis.
        These inputs are stored in `self.dirname_out`.
        """
        os.chdir(self.dirname_out)

        # TODO: Check that files are coordinated with each other, i.e. contain the
        #       same sequences, etc
        def _check_prot_ids():
            if not self.inputs['prot_ids']:
                if not self.inputs['refseq_id']:
                    raise ValueError("No input refseq ids provided")
                # BLAST search
                self.inputs['prot_ids'] = fetch_homologs(self.inputs['refseq_id'])
        def _check_dna_fasta():
            if not self.inputs['dna_fasta']:
                _check_prot_ids()
                # UNIPROT search
                self.inputs['dna_fasta'], a = fetch_seqs(self.inputs['prot_ids'])
                if self.inputs['aa_fasta']:
                    sys.stderr.write("Overwriting input aa_fasta\n")
                self.inputs['aa_fasta'] = a
        def _check_aa_fasta():
            if not self.inputs['aa_fasta']:
                _check_prot_ids()
                # UNIPROT search
                a, self.inputs['aa_fasta'] = fetch_seqs(self.inputs['prot_ids'])
                if self.inputs['dna_fasta']:
                    sys.stderr.write("Overwriting input dna_fasta\n")
                self.inputs['dna_fasta'] = a
        def _check_prots_aln():
            if not self.inputs['prots_aln']:
                _check_aa_fasta()
                self.inputs['prots_aln'] = run_clustalw2(self.inputs['aa_fasta'])
        def _check_codons_aln():
            if not self.inputs['codons_aln']:
                _check_dna_fasta()
                _check_prots_aln()
                self.inputs['codons_aln'] = run_pal2nal(self.inputs['prots_aln'],
                        self.inputs['dna_fasta'], self.inputs['aa_fasta'])
        def _check_tree():
            if not self.inputs['tree']:
                _check_prots_aln()
                self.inputs['tree'], _ = run_phyml(self.inputs['prots_aln'], self.n_bootstrap)
        def _check_boot_trees():
            if not self.inputs['boot_trees']:
                _check_prots_aln()
                _, self.inputs['boot_trees'] = run_phyml(self.inputs['prots_aln'], self.n_bootstrap)

        _check_codons_aln()
        if not self.bootstrap:
            _check_tree()
            # Get rid of confidences in tree
            fname_tree = convert_maintree(self.inputs['tree'])
            # Generate ctl file.
            self.ctl = make_ctl(self.inputs['codons_aln'], fname_tree,
                    self.inputs["ctl_template"])
        else:
            _check_boot_trees()
            # Split up bootstrap trees
            fname_trees = convert_boottrees(self.inputs['boot_trees'])
            # Generate ctl file.
            self.ctls = []
            for i, fname_tree in enumerate(fname_trees):
                self.ctls.append(make_ctl(self.inputs['codons_aln'], fname_tree,
                        self.inputs["ctl_template"], suffix="-%d"%i))


    def process(self):
        print ""
        print "Preprocessing completed"
        print ""
#        print "==How to run idea=="
#        print "cd %s" % self.dirname_out
#        print os.path.abspath("%s/../idea-2.5.1/idea" % bin_dir())
#        print "File > Load configuration > codonml.ctl"
#        print "It's actually really annoying.  So don't run idea."
#        print ""
        print "==How to run codeml (copy and paste to run)=="
        print "cd %s" % self.dirname_out
        dirs = []
        if self.bootstrap:
            for i, ctl in enumerate(self.ctls):
                dirname = "codeml-%d" % i
                dirs.append(dirname)
                print "cd %s" % dirname
                print "%s/codeml > out.codeml-%d 2>&1 &" % (bin_dir(), i)
                print "cd .."
        else:
            dirname = "codeml-main"
            dirs.append(dirname)
            print "cd %s" % dirname
            print "%s/codeml > out.codeml-main 2>&1 &" % (bin_dir(),)
            print "cd .."
        print "%s/postprocess.py %s" % (os.path.join(bin_dir(), "..", "codemlp"), " ".join([dirname + "rst" for dirname in dirs]))


def main():
    # take protein aln file in CSA format from caprasingh08 dataset
    parser = argparse.ArgumentParser(description="produce dn/ds output from simple aln file")
    parser.add_argument('fnames_in', nargs="*",
        help="input files in any format")
    parser.add_argument('-i', dest='refseq_id', type=str, default=None,
        help="refseq id of sequence of interest. either protein or dna is OK")
    parser.add_argument('-o', dest='dirname_out', type=str, default=None,
        help="output directory. Defaults to new directory in folder of first positional argument")
    parser.add_argument('-n', dest='n_bootstrap', type=int, default=1,
        help="number of bootstrapped trees. Defaults to 1")
    parser.add_argument('-b', dest='bootstrap', action='store_true', default=AUTOMATIC,
        help="run codeML on all bootstrap trees, and analyze the resulting dnds distribution per site")
    args = parser.parse_args()
    
    singlerun = SingleRun(args.fnames_in, args.refseq_id, args.dirname_out,
            args.n_bootstrap, args.bootstrap)
    singlerun.preprocess()
    singlerun.process()


if __name__ == "__main__":
    main()
