#!/usr/bin/python
"""
Helper functions for preprocessing codeml input data.

These functions often create new files in the current directory, so make sure
you are in a directory you're OK with making a lot of new files in before
running them.
"""
import os
import re
import sys
from Bio import Entrez
from Bio import Phylo
from Bio import SeqIO
from Bio.Blast.NCBIWWW import qblast
from Bio.Blast import NCBIXML
from utils import ts_str, bin_dir


def fetch_homologs(prot_id):
    """
    @param prot_id:
        The protein genbank id to get homologs for.
    @return:
        List of genbank ids of protein homologs.
    """
    sys.stderr.write("\nSTEP: fetch homologs(%s)\n" % prot_id)
    
    blast_res = qblast("blastp", "nr", prot_id)
    blast_nr = NCBIXML.read(blast_res)

    prot_ids = [alignment.accession for alignment in blast_nr.alignments]

    fname_prot_ids = "homologues.protids"
    if os.path.exists(fname_prot_ids):
        raise Exception("File %s aleady exists" % (fname_prot_ids))
    with open(fname_prot_ids, 'w') as f:
        for prot_id in prot_ids:
            f.write(prot_id + '\n')
    return fname_prot_ids
        

Entrez.email = "ldiao@princeton.edu"
pat_complement = re.compile(r"complement\((.*)\)")

def fetch_seqs(fname_prot_ids):
    """
    Fetch the NUC and AA sequences for the genbank IDs in the input.
    
    @param fname_prot_ids:
        File containing list of protein genbank ids of homologs to get sequence data for.
        Each line contains 1 id.
    @return:
        (fname_nuc, fname_prot) = 2 FASTA files containing nucleotide and amino acid
        sequences, respectively, of the ids in fname_prot_ids
    """
    sys.stderr.write("\nSTEP: fetch_seqs(%s)\n" % fname_prot_ids)

    with open(fname_prot_ids, 'r') as f:
        prot_ids = [line.strip() for line in f if line.strip()]
    
    fname_prot = "homologues.aa.fasta"
    fname_nuc = "homologues.dna.fasta"
    if os.path.exists(fname_nuc) or os.path.exists(fname_prot):
        raise Exception("File %s and/or %s already exists" % (fname_nuc, fname_prot))

    f_prot = open(fname_prot, "w")
    prot_handle = Entrez.efetch(
        db="protein",
        id=",".join(prot_ids),
        rettype="gb"
    )   
    dna_metadata = []
    for prot_record in SeqIO.parse(prot_handle, "gb"):
        got_data = False
        # Get id and ends of DNA sequence.
        for feature in prot_record.features:
            if feature.type == "CDS":
                feature_data = feature.qualifiers['coded_by'][0]
                got_data = True
                match_complement = re.match(pat_complement, feature_data)
                if match_complement:
                    feature_data = match_complement.group(1)
                dna_id, dna_positions = feature_data.split(":")
                dna_start, dna_end = dna_positions.split("..")
                dna_start = int(re.sub("[^0-9]","",dna_start))
                dna_end = int(re.sub("[^0-9]","",dna_end))
                dna_metadata.append((dna_id, dna_start, dna_end, match_complement))
                break
        if got_data:
            SeqIO.write(prot_record, f_prot, "fasta")
        else:
            sys.stderr.write("Skipping protein %s due to no nucleotide sequence\n" % prot_record.id)
    prot_handle.close()
    f_prot.close()

    f_nuc = open(fname_nuc, "w")
    # Fetch DNA sequence.
    dna_handle = Entrez.efetch(
        db="nucleotide",
        id=",".join([dna_id for dna_id,_,_,_ in dna_metadata]),
        rettype="fasta")
    for dna_record, dna_md in zip(SeqIO.parse(dna_handle, "fasta"), dna_metadata):
        dna_id, dna_start, dna_end, match = dna_md
        # Get the correct range of nucleotides in the genome.
        dna_record.seq = dna_record.seq[dna_start-1:dna_end]
        if match_complement:
            dna_record.seq = dna_record.seq.reverse_complement()
        # Omit the stop codon.
        if str(dna_record.seq[-3:]) in ('TAG', 'TAA', 'TGA'):
            dna_record.seq = dna_record.seq[:-3]
        # Write the data.
        SeqIO.write(dna_record, f_nuc, "fasta")
    dna_handle.close()
    f_nuc.close()

    return (fname_nuc, fname_prot)


def run_clustalw2(fname_prot):
    """
    Generate a MSA of the amino acids (in fasta format) via clustalw.

    @param fname_prot:
        Protein sequences in FASTA format (.fasta)
    @return:
        MSA of protein sequences in CLUSTAL format (.aln)
    """
    sys.stderr.write("\nSTEP: run_clustalw2(%s)\n" % fname_prot)
    # Note: clustalw2 is assumed to have already been installed. We can change
    # this assumption by adding clustalw2 to bin/
    os.system("clustalw2 %s" % fname_prot)
    fname_aln = ".".join(fname_prot.split(".")[:-1]) + ".aln"
    return fname_aln


def run_pal2nal(fname_aln, fname_nuc, fname_prot):
    """
    Generate a codon alignment via PAL2NAL.

    @param fname_aln:
        MSA of protein sequences in CLUSTAL format (.aln)
    @param fname_nuc:
        Nucleotide sequences in FASTA format (.fasta)
    @param fname_prot:
        Protein sequences in FASTA format (.fasta)
    @return:
        Codon alignment in CLUSTAL format (.aln), suitable for codeml
    """
    sys.stderr.write("\nSTEP: run_pal2nal(%s, %s)\n" % (fname_aln, fname_nuc))

    # Reorder fname_nuc according to the order of the proteins in fname_aln, which
    # was reordered due to CLUSTALW2.  Note that the first protein in each of
    # these files remains the same as at the start, however; this first protein
    # is our original query protein.
    nuc_records = [record for record in SeqIO.parse(fname_nuc, "fasta")]
    prot_records = [record for record in SeqIO.parse(fname_prot, "fasta")]
    records_map = dict((pr.id, nr) for pr, nr in zip(prot_records, nuc_records))
    fname_nuc2 = "homologues_ordered.dna.fasta"
    with open(fname_nuc2, "w") as f:
        for record in SeqIO.parse(fname_aln, "clustal"):
            SeqIO.write(records_map[record.id], f, "fasta")
    fname_codon = "homologues.codon.aln"
    os.system("%s/pal2nal.pl %s %s -output paml > %s" %
            (bin_dir(), fname_aln, fname_nuc2, fname_codon))
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

    fname_tree = ".".join(fname_aln.split(".")[:-1]) + ".phy_phyml_tree.txt"
    if n_bootstrap > 1:
        bootstrap_str = " -b %d" % n_bootstrap
        fname_boot_trees = ".".join(fname_aln.split(".")[:-1]) + ".phy_phyml_boot_trees.txt"
    else:
        bootstrap_str = ""
        fname_boot_trees = None
    os.system("%s/phyml -i %s -d aa%s" % (bin_dir(), fname_phy, bootstrap_str))
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
    fname_ctl = "codonml.ctl"
    wrote_seqfile = False
    wrote_treefile = False
    with open(fname_ctl, "w") as fw:
        with open(ctl_template, "r") as fr:
            for line in fr:
                if line.strip().startswith("seqfile"):
                    if wrote_seqfile:
                        raise ValueError("Bad ctl template file; multiple seqfile lines")
                    fw.write("seqfile = %s\n" % fname_codon)
                    wrote_seqfile = True
                elif line.strip().startswith("treefile"):
                    if wrote_treefile:
                        raise ValueError("Bad ctl template file; multiple treefile lines")
                    fw.write("treefile = %s\n" % fname_tree)
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
    #homologs = fetch_homologs(sys.argv[1])
    #fetch_seqs(homologs) 
    fetch_seqs(sys.argv[1])

if __name__ == "__main__":
    main()
