#!/usr/bin/python
"""
Helper functions for preprocessing codeml input data.

These functions often create new files in the current directory, so make sure
you are in a directory you're OK with making a lot of new files in before
running them.
"""
import os
import sys
from Bio import Entrez
from Bio import Phylo
from Bio import SeqIO
from utils import ts_str, bin_dir


def fetch_homologs(gb_id):
    """
    @param gb_id:
        The genbank id to get homologs for.
    @return:
        (homologs_file, nucs_file, aas_file) = File containing a list of gbids
        of homologs for the input id, FASTA file containing nucleotide sequences,
        FASTA file containing aa sequences.
        The first entry in each file should be the input gbid in the appropriate format.
    """
    raise NotImplementedError()


Entrez.email = "ldiao@princeton.edu"
def fetch_seqs(fname_gb_ids):
    """
    Fetch the NUC and AA sequences for the genbank IDs listed in fname_gbids.
    
    @param fname_gb_ids:
        File containing list of genbank ids of homologs to get sequence data for.
        Each line contains 1 id.
    @return:
        (fname_nuc, fname_aa) = 2 FASTA files containing nucleotide and amino acid
        sequences, respectively, of the ids in fname_gbids
    """
    #TODO
    sys.stderr.write("\nSTEP: fetch_seqs(%s)\n" % fname_gb_ids)

    with open(fname_gb_ids, 'r') as f:
        gb_ids = [line.strip() for line in f if line.strip()]
    
    fname_nuc = "nucleotides.fasta"
    fname_aa = "amino_acids.fasta"

    if os.path.exists(fname_nuc) or os.path.exists(fname_aa):
        raise Exception("File %s and/or %s already exists" % (fname_nuc, fname_aa))

    with open(fname_nuc, 'w') as f_nuc:
        with open(fname_aa, 'w') as f_aa:
            nuc_records = SeqIO.parse(Entrez.efetch(db="nucleotide", id=','.join(gb_ids),
                                                    rettype="fasta", retmode="text"), "fasta")
            for record in nuc_records:
                SeqIO.write(record, f_nuc, 'fasta')
                record.seq = record.seq.translate()
                SeqIO.write(record, f_aa, 'fasta')    

    return (fname_nuc, fname_aa)

def run_clustalw2(fname_aas):
    """
    Generate a MSA of the amino acids (in fasta format) via clustalw.

    @param fname_aas:
        Protein sequences in FASTA format (.fasta)
    @return:
        MSA of protein sequences in CLUSTAL format (.aln)
    """
    # Note: clustalw2 is assumed to have already been installed. We can change
    # this assumption by adding clustalw2 to bin/
    os.system("clustalw2 %s" % fname_aas)
    fname_aln = ".".join(fname_aas.split(".")[:-1]) + ".aln"
    return fname_aln


def run_pal2nal(fname_aln, fname_nuc):
    """
    Generate a codon alignment via PAL2NAL.

    @param fname_aln:
        MSA of protein sequences in CLUSTAL format (.aln)
    @param fname_nuc:
        Nucleotide sequences in FASTA format (.fasta)
    @return:
        Codon alignment in CLUSTAL format (.aln), suitable for codeml
    """
    sys.stderr.write("\nSTEP: run_pal2nal(%s, %s)\n" % (fname_aln, fname_nuc))
    fname_codon = ".".join(fname_aln.split(".")[:-1]) + ".codon.nuc"
    os.system("%s/pal2nal.pl %s %s --output paml > %s" %
            (bin_dir(), fname_aln, fname_nuc, fname_codon))
    return fname_codon


def run_phyml(fname_aln, n_bootstrap):
    """
    Generate a phylogenetic tree via PHYML.

    @param fname_aln:
        MSA of protein sequences in CLUSTAL format (.aln)
    @return:
        (tree_file, bootstrap_file) = File of phylo tree with clade confidences (_tree.txt),
        file of bootstrapped phylo trees (_boot_trees.txt)
    """
    sys.stderr.write("\nSTEP: run_phyml(%s, %s)\n" % (fname_aln, n_bootstrap))
    fname_phy = ".".join(fname_aln.split(".")[:-1]) + ".phy"
    with open(fname_aln, "rU") as f_in:
        with open(fname_phy, "w") as f_out:
            SeqIO.convert(f_in, "clustal", f_out, "phylip-relaxed")
    os.system("%s/phyml -i %s -d aa -b %d" % (bin_dir(), fname_phy, n_bootstrap))
    fname_tree = ".".join(fname_aln.split(".")[:-1]) + ".phy_phyml_tree.txt"
    fname_boot_trees = ".".join(fname_aln.split(".")[:-1]) + ".phy_phyml_boot_trees.txt"
    return fname_tree, fname_boot_trees


def convert_maintree(fname_tree):
    """
    Convert the main phylogenetic tree with confidences into a format suitable
    for codeml (without confidences).

    @param fname_tree:
        Phylo tree with clade confidences
    @return:
        Phylo tree without clade confidences
    """
    tree = Phylo.read(fname_tree, 'newick')
    for clade in tree.find_clades():
        clade.confidence = None
    out_fname = fname_tree + ".codeml-main"
    Phylo.write(tree, out_fname, "newick")
    return out_fname


def convert_boottrees(fname_trees):
    out_fnames = []
    for i, tree in enumerate(Phylo.parse(fname_trees, 'newick')):
        fname_tree = "%s.codeml-%d" % (fname_trees, i)
        Phylo.write(tree, fname_tree, "newick")
        out_fnames.append(fname_tree)
    return out_fnames


CTL_TEMPLATE = os.path.join(os.path.dirname(__file__), "codonml.ctl")
def make_ctl(fname_codon, fname_tree, ctl_template=None):
    """
    Generate the PAML config file.

    @param fname_codon:
        Codon alignment in CLUSTAL format (.aln), suitable for codeml
    @param fname_tree:
        Phylo tree file to analyze, without clade confidences (_tree.txt)
    @param ctl_template:
        Config file template for codeml (.ctl)
    @return:
        Config file for codeml (.ctl)
    """
    if not ctl_template:
        ctl_template = CTL_TEMPLATE
    sys.stderr.write("\nSTEP: make_ctl(%s, %s)\n" % (fname_codon, fname_tree))
    fname_ctl = ts_str() + ".ctl"
    wrote_seqfile = False
    wrote_treefile = False
    with open(fname_ctl, "w") as fw:
        with open(ctl_template, "r") as fr:
            for line in fr:
                if line.strip().startswith("seqfile"):
                    if wrote_seqfile:
                        raise ValueError("Bad ctl template file; multiple seqfile lines")
                    fw.write("seqfile = " + fname_codon)
                    wrote_seqfile = True
                elif line.strip().startswith("treefile"):
                    if wrote_treefile:
                        raise ValueError("Bad ctl template file; multiple treefile lines")
                    fw.write("treefile = " + fname_tree)
                    wrote_treefile = True
                else:
                    fw.write(line)
    # Sanity check.
    if not wrote_seqfile:
        raise ValueError("Bad ctl template file; no seqfile line")
    if not wrote_treefile:
        raise ValueError("Bad ctl template file; no treefile line")
    return fname_ctl


def run_codeml(fname_ctl):
    """
    XXX
    Input:
        fname_ctl
    Output:
        codeml output, currently
    """
    os.system("%s/codeml %s" % (bin_dir(), fname_ctl))
    # TODO: redirect output into more sensible format

def main():
    fetch_seqs(sys.argv[1])

if __name__ == "__main__":
    main()
