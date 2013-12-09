import os
from preprocess import *


class SingleRun(object):

    def __init__(self, fnames_in, dirname_out):
        """
        Categorize input files and create the appropriate output directory,
        stored at `self.dirname_out`.

        Input:
            fnames_in - absolute paths of input files
        """
        inputs = ('gbid', 'homolog_gbids',
                  'homolog_nucs', 'homolog_aas',
                  'aa_aln', 'codon_aln',
                  'tree', 'bootstrap_trees',
                  'ctl')
        inputs = dict((inp, None) for inp in inputs)

        for fname_in in fnames_in:
            # Use filename to determine what input type it is
            # TODO: Open up file to sanity check inputs, and provide greater flexibility
            if fname_in.endswith(".ctl"):
                inputs['ctl'] = fname_in
            elif fname_in.endswith("tree.txt"):
                inputs['tree'] = fname_in
            elif fname_in.endswith("trees.txt"):
                inputs['bootstrap_trees'] = fname_in
            elif fname_in.endswith(".aln"):
                inputs['codon_aln'] = fname_in
            else:
                raise NotImplementedError()

        fnames_in = [os.abspath(fname_in) for fname_in in fnames_in]
        if not dirname_out:
            head, tail = os.path.split(fnames_in[0])
            os.chdir(head)
            ts = datetime.datetime.now().strftime("%Y%m%d-%H%m%s")
            dirname_out = ".".join(tail.split(".")[:-1]) + ts
            dirname_out = os.abspath(dirname_out)
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
                    _check_homolog_gbids()
                else:
                    # UNIPROT search
                    self.inputs['homolog_nucs'], a = fetch_seqs(self.inputs['homolog_gbids']
                    if self.inputs['homolog_aas']:
                        sys.stderr.write("Overwriting input homolog_aas\n")
                        self.inputs['homolog_aas'] = a
        def _check_homolog_aas():
            if not self.inputs['homolog_aas']:
                if not self.inputs['homolog_gbids']:
                    _check_homolog_gbids()
                else:
                    # UNIPROT search
                    a, self.inputs['homolog_aas'] = fetch_seqs(self.inputs['homolog_gbids']
                    if self.inputs['homolog_nucs']:
                        sys.stderr.write("Overwriting input homolog_nucs\n")
                        self.inputs['homolog_nucs'] = a
        def _check_aa_aln():
            if not self.inputs['aa_aln']:
                _check_homolog_aas()
                self.inputs['aa_aln'] = run_clustalw(self.inputs['homolog_aas'])
        def _check_codon_aln():
            if not self.inputs['codon_aln']:
                _check_aa_aln()
                _check_homolog_nucs()
                self.inputs['codon_aln'] = run_pal2nal(
                        self.inputs['aa_aln'], self.inputs['homolog_nucs'])
        def _check_tree():
            if not self.inputs['tree']:
                _check_aa_aln()
                self.inputs['tree'] = run_phyml(self.inputs['aa_aln'], n_boostrap)

        if analyze_bootstrap:
            x

        if not self.inputs['ctl']:
            if not self.inputs['tree']:
                x

            self.inputs['ctl'] = make_ctl()


        # TODO: the preprocessing can kind of be done in parallel
        fname_nuc = fetch_nuc(fname_aln)
        fname_codon = run_pal2nal(fname_aln, fname_nuc)
        fname_tree = run_phyml(fname_aln, n_bootstrap)
        fname_ctl = make_ctl(fname_codon, fname_tree)
        run_codeml(fname_ctl)

    def _preprocess_codon_aln(self):
        pass
