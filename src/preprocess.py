import os
import sys
from Bio import Entrez
from Bio import SeqIO


def fetch_homologs(fname_gbid):
    """
    @param fname_gbid:
        File containing the genbank id to get homologs for.
    @return:
        (homologs_file, nucs_file, aas_file) = File containing a list of gbids
        of homologs for the input id, FASTA file containing nucleotide sequences,
        FASTA file containing aa sequences.
        The first entry in each file should be the input gbid in the appropriate format.
    """
    #TODO
    pass


Entrez.email = "ldiao@princeton.edu"
def fetch_seqs(fname_gbids):
    """
    Fetch the NUC and AA sequences for the genbank IDs listed in fname_gbids.
    
    @param fname_gbids:
        File containing list of genbank ids of homologs to get sequence data for.
        Each line contains 1 id.
    @return:
        (nuc_file, aa_file) = 2 FASTA files containing nucleotide and amino acid
        sequences, respectively, of the ids in fname_gbids
    """
    #TODO
    sys.stderr.write("\nSTEP: fetch_nuc(%s)\n" % fname_aln)

	with open(fname_aln, 'r') as f:
		for seq in SeqIO.parse(f, 'clustal'):
			pass
	

    return fname_nuc


def run_pal2nal(fname_aln, fname_nuc):
    """
    Generate a codon alignment via PAL2NAL.

    @param fname_aln:
        protein alignment either in CLUSTAL or FASTA format
    @param fname_nuc:
        Nucleotide data for sequences in alignment (single multi-fasta or separated files)
    @return:
        Clustal file (.aln) containing codon delimited alignment, suitable for codeml.
    """
    sys.stderr.write("\nSTEP: run_pal2nal(%s, %s)\n" % (fname_aln, fname_nuc))
    fname_codon = ".".join(fname_aln.split(".")[:-1]) + ".codon.nuc"
    os.system("pal2nal.pl %s %s --output paml > %s" % (fname_aln, fname_nuc, fname_codon))
    return fname_codon


def run_phyml(fname_aln, n_bootstrap):
    """
    Generate a phylogenetic tree via PHYML.

    @param fname_aln:
        protein alignment either in CLUSTAL or FASTA format
    @return:
        (tree_file, bootstrap_file) = File of phylo tree with clade confidences
    """
    sys.stderr.write("\nSTEP: run_phyml(%s, %s)\n" % (fname_aln, n_bootstrap))
    fname_phy = ".".join(fname_aln.split(".")[:-1]) + ".phy"
    with open(fname_aln, "rU") as f_in:
        with open(fname_phy, "w") as f_out:
            SeqIO.convert(f_in, "clustal", f_out, "phylip-relaxed")
    os.system("phyml -i %s -d aa -b %d" % (fname_phy, n_bootstrap))
    fname_tree = ".".join(fname_aln.split(".")[:-1]) + ".phy_phyml_tree.txt"
    return fname_tree


def make_ctl(fname_codon, fname_tree):
    """
    Generate the PAML config file.

    @param fname_nuc:
        codon ailgnment file to analyze
    @param fname_tree:
        phylogenetic tree file to analyze
    @return:
        Config file
    """
    # TODO: need to get rid of clade confidences in tree file
    sys.stderr.write("\nSTEP: make_ctl(%s, %s)\n" % (fname_nuc, fname_tree)
    fname_ctl = ".".join(fname_nuc.split(".")[:-1]) + ".ctl"
    with open(fname_nuc, "w") as fw:
        with open("codonml.ctl", "r") as fr:
            line = fr.readline()
            fw.write(fr.readline() % fname_nuc)
            fw.write(fr.readline() % fname_tree)
            for line in fr:
                fw.write(line)
    return fname_ctl


def run_codeml(fname_ctl):
    """
    XXX
    Input:
        fname_ctl
    Output:
        codeml output, currently
    """
    os.system("codeml %s" % fname_ctl)
    # TODO: redirect output into more sensible format

