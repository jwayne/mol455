#!/usr/bin/python
"""
Workflow for running codeml given any set of input data files.
"""
import argparse
import os
from preprocess import (fetch_homologs, fetch_seqs, run_clustalw2, run_pal2nal,
                        run_phyml, convert_maintree, convert_boottrees,
                        make_ctl)
from utils import ts_str, bin_dir, data_dir


AUTOMATIC = "AUTOMATIC"


class SingleRun(object):

    def __init__(self, fnames_in, prot_id_in, dirname_out, n_bootstrap, bootstrap):
        """
        Categorize input files and create the appropriate output directory,
        stored at `self.dirname_out`.

        @param fnames_in:
            absolute paths of input files
        """
        #XXX: it isn't checked for that inputs match each other!
        if not fnames_in and not prot_id_in:
            raise ValueError("No input files provided")

        inputs = ('prot_id', 'prot_ids',
                  'dna_fasta', 'aa_fasta',
                  'prots_aln', 'codons_aln',
                  'tree', 'boot_trees',
                  'ctl_template')
        inputs = dict((inp, None) for inp in inputs)

        if prot_id_in:
            inputs['prot_id'] = prot_id_in

        fnames_in = [os.path.abspath(fname_in) for fname_in in fnames_in]
        for fname_in in fnames_in:
            # Use filename to determine what input type it is
            # TODO: Open up file to sanity check inputs, and provide greater flexibility
            if fname_in.endswith(".protids"):
                if inputs['prot_ids']:
                    raise ValueError("Duplicate lists of prot_ids")
                inputs['prot_ids'] = fname_in
            elif fname_in.endswith(".dna.fasta"):
                if inputs['dna_fasta']:
                    raise ValueError("Duplicate nucleotide sequence files")
                inputs['dna_fasta'] = fname_in
            elif fname_in.endswith(".aa.fasta"):
                if inputs['aa_fasta']:
                    raise ValueError("Duplicate protein sequence files")
                inputs['aa_fasta'] = fname_in
            elif fname_in.endswith(".aa.aln"):
                if inputs['prots_aln']:
                    raise ValueError("Duplicate protein alignment files")
                inputs['prots_aln'] = fname_in
            elif fname_in.endswith(".codon.aln"):
                if inputs['codons_aln']:
                    raise ValueError("Duplicate codon alignment files")
                inputs['codons_aln'] = fname_in
            elif fname_in.endswith("_phyml_tree.txt"):
                if inputs['tree']:
                    raise ValueError("Duplicate tree files")
                if inputs['boot_trees']:
                    raise ValueError("Do not provide both tree and bootstrap trees")
                inputs['tree'] = fname_in
            elif fname_in.endswith("_phyml_trees.txt"):
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
                raise NotImplementedError("Input file format not recognized: %s" % fname_in)

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

        # TODO: name dirname_out after first protein sequence in file
        if not dirname_out:
            os.chdir(data_dir())
            if not fnames_in:
                prefix = prot_id_in
            else:
                tail = os.path.split(fnames_in[0])[-1]
                prefix = tail.split(".")[0]
            dirname_out = "%s-%s" % (prefix, ts_str())
            dirname_out = os.path.abspath(dirname_out)
            if os.path.exists(dirname_out):
                raise NotImplementedError()
            os.mkdir(dirname_out)

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
                if not self.inputs['prot_id']:
                    raise ValueError("No input sequence ids provided")
                # BLAST search
                self.inputs['prot_ids'] = fetch_homologs(self.inputs['prot_id'])
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
        print "==How to run idea=="
        print "cd %s" % self.dirname_out
        print os.path.abspath("%s/../idea-2.5.1/idea" % bin_dir())
        print "File > Load configuration > codonml.ctl"
        print "It's actually really annoying.  So don't run idea."
        print ""
        print "==How to run codeml=="
        print "cd %s" % self.dirname_out
        if self.bootstrap:
            for i, ctl in enumerate(self.ctls):
                print "mkdir codeml-%d; cd codeml-%d" % (i, i)
                if not os.path.isabs(ctl):
                    ctl = "../" + ctl
                print "%s/codeml %s > out.codeml-%d 2>&1 &" % (bin_dir(), ctl, i)
                print "cd .."
        else:
            print "mkdir codeml-main; cd codeml-main"
            ctl = self.ctl
            if not os.path.isabs(ctl):
                ctl = "../" + ctl
            print "%s/codeml %s > out.codeml-main 2>&1 &" % (bin_dir(), ctl)
            print "cd .."


def main():
    # take protein aln file in CSA format from caprasingh08 dataset
    parser = argparse.ArgumentParser(description="produce dn/ds output from simple aln file")
    parser.add_argument('fnames_in', nargs="*",
        help="input files in any format")
    parser.add_argument('-i', dest='prot_id', type=str, default=None,
        help="protein id of sequence of interest")
    parser.add_argument('-o', dest='dirname_out', type=str, default=None,
        help="output directory. Defaults to new directory in folder of first positional argument")
    parser.add_argument('-n', dest='n_bootstrap', type=int, default=1,
        help="number of bootstrapped trees. Defaults to 1")
    parser.add_argument('-b', dest='bootstrap', action='store_true', default=AUTOMATIC,
        help="run codeML on all bootstrap trees, and analyze the resulting dnds distribution per site")
    args = parser.parse_args()
    
    singlerun = SingleRun(args.fnames_in, args.prot_id, args.dirname_out,
            args.n_bootstrap, args.bootstrap)
    singlerun.preprocess()
    singlerun.process()


if __name__ == "__main__":
    main()
