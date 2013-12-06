import argparse
import os
import sys
from Bio import SeqIO


def fetch_nuc(fname_aln):
    """
    PREPROCESSING
    Input:
        fname_aln = protein alignment either in CLUSTAL or FASTA format
    Output:
        fname_nuc = nucleotides for each sequence in the protein alignment
    """
    sys.stderr.write("\nSTEP: fetch_nuc(%s)\n" % fname_aln)
    return fname_nuc


def run_pal2nal(fname_aln, fname_nuc):
    """
    PREPROCESSING
    Input:
        fname_aln = protein alignment either in CLUSTAL or FASTA format
        fname_nuc = DNA sequences (single multi-fasta or separated files)
    Output:
        fname_codon = codon delimited alignment, suitable for codeml
    """
    # TODO: the output should be treated as tmp files
    sys.stderr.write("\nSTEP: run_pal2nal(%s, %s)\n" % (fname_aln, fname_nuc))
    fname_codon = ".".join(fname_aln.split(".")[:-1]) + ".codon.nuc"
    os.system("pal2nal.pl %s %s --output paml > %s" % (fname_aln, fname_nuc, fname_codon))
    return fname_codon


def run_phyml(fname_aln, n_bootstrap):
    """
    PREPROCESSING
    Input:
        fname_aln = protein alignment either in CLUSTAL or FASTA format
    Output:
        fname_tree = phylo tree with clade confidences
        TODO: bootstrapped tree fnames?
    """
    # TODO: the output should be treated as tmp files
    sys.stderr.write("\nSTEP: run_phyml(%s, %s)\n" % (fname_aln, n_bootstrap))
    fname_phy = ".".join(fname_aln.split(".")[:-1]) + ".phy"
    with open(fname_aln, "rU") as f_in:
        with open(fname_phy, "w") as f_out:
            SeqIO.convert(f_in, "clustal", f_out, "phylip-relaxed")
    os.system("phyml -i %s -d aa -b %d" % (fname_phy, n_bootstrap))
    fname_tree = ".".join(fname_aln.split(".")[:-1]) + ".phy_phyml_tree.txt"
    return fname_tree


def make_ctl(fname_nuc):
    sys.stderr.write("\nSTEP: make_ctl(%s)\n" % fname_nuc)
    return fname_ctl


def run_codeml(fname_ctl):
    """
    Input:
        fname_ctl
    Output:
        codeml output, currently
    """
    os.system("codeml %s" % fname_ctl)
    # TODO: redirect output into more sensible format


if __name__ == "__main__":
    # take protein aln file in CSA format from caprasingh08 dataset
    parser = argparse.ArgumentParser(description="produce dn/ds output from simple aln file")
    parser.add_argument('-n', dest='n_bootstrap', type=int, default=1,
        help="number of bootstrapped trees")
    parser.add_argument('fname_aln')
    args = parser.parse_args()

    fname_aln = args.fname_aln
    n_bootstrap = args.n_bootstrap

    fname_nuc = fetch_nuc(fname_aln)
    fname_codon = run_pal2nal(fname_aln, fname_nuc)
    fname_tre = run_phyml(fname_aln, n_bootstrap)
    fname_ctl = make_ctl(fname_codon, fname_tree)
    run_codeml(fname_ctl)
