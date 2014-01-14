#!/usr/bin/python
"""
Helper functions for preprocessing codeml input data.

These functions often create new files in the current directory, so make sure
you are in a directory you're OK with making a lot of new files in before
running them.
"""
import os
import re
import subprocess
import sys
from Bio import Entrez
from Bio import Phylo
from Bio import SeqIO
from Bio.Blast.NCBIWWW import qblast
from Bio.Blast import NCBIXML
from Bio.Seq import Seq
from utils import ts_str, bin_dir


Entrez.email = "ldiao@princeton.edu"


################################################################################
# Helper functions for use in this module
################################################################################

def _fetch_dna_records(dna_ids):
    prot_ids = []
    dna_handle = Entrez.efetch(
        db="nucleotide",
        id=",".join(dna_ids),
        rettype="gb"
    )
    dna_records = []
    for dna_record in SeqIO.parse(dna_handle, "gb"):
        got_data = False
        # Get id of protein sequence
        for feature in dna_record.features:
            if feature.type == "CDS":
                prot_id = feature.qualifiers['protein_id']
                prot_ids.append(prot_id[0])
                got_data = True
        if got_data:
            dna_records.append(dna_record)
        else:
            sys.stderr.write("Skipping nucleotide %s due to no genbank protein_id\n" % dna_record.id)
    dna_handle.close()
    return dna_records, prot_ids


pat_complement = re.compile(r"complement\((.*)\)")
pat_join = re.compile(r"join\((.*)\)")

def _fetch_prot_records(prot_ids):
    prot_records = []
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
                match_complement = re.match(pat_complement, feature_data)
                if match_complement:
                    feature_data = match_complement.group(1)
                match_join = re.match(pat_join, feature_data)
                if match_join:
                    # Disable this feature for now.  Too annoying.
                    got_data = False
                    break
                    feature_data = match_join.group(1)
                    items = [x.strip() for x in feature_data.split(',')]
                else:
                    items = [feature_data]
                dna_inds = []
                for item in items:
                    dna_id, dna_positions = item.split(":")
                    dna_start, dna_end = dna_positions.split("..")
                    dna_start = int(re.sub("[^0-9]","",dna_start))
                    dna_end = int(re.sub("[^0-9]","",dna_end))
                    dna_inds.append((dna_start, dna_end))
                dna_metadata.append((dna_id, dna_inds, bool(match_complement)))
                got_data = True
                break
        if got_data:
            prot_records.append(prot_record)
        else:
            sys.stderr.write("Skipping protein %s due to no genbank CDS entry\n" % prot_record.id)
    prot_handle.close()
    return prot_records, dna_metadata


################################################################################
# Main preprocessing functions
################################################################################

def fetch_homologs(refseq_id):
    """
    Query BLAST for homologs, using a blastp search.

    @param refseq_id:
        The accession number to get homologs for.  Either protein or DNA is OK.
    @return:
        List of protein accession numbers of homologs.
    """
    sys.stderr.write("\nSTEP: fetch homologs(%s)\n" % refseq_id)

    # Determine if protein or dna
    # http://www.ncbi.nlm.nih.gov/Sitemap/sequenceIDs.html
    if refseq_id[2].isalpha():
        prot_id = refseq_id
    else:
        _, prot_ids = _fetch_dna_records([refseq_id])
        prot_id = prot_ids[0]

    # If I don't do this, I seem to get--
    # ValueError: Error message from NCBI: Message ID#68 Error:
    # Error occurred while trying to set up a Blast Object from CGI context:
    # CFastaReader: Segmented set not properly terminated around line 1
    prot_id = prot_id.split('.')[0]

    # Query BLAST for homologs
    sys.stderr.write("\tQuerying BLAST, please be patient (may take minutes)...\n")
    blast_res = qblast("blastp", "nr", prot_id)
    blast_nr = NCBIXML.read(blast_res)
    prot_ids = [alignment.accession for alignment in blast_nr.alignments]
    sys.stderr.write("\tBLAST query successful, %s homologs found\n" % len(prot_ids))

    # Write to disk.
    fname_prot_ids = "homologs.protids"
    if os.path.exists(fname_prot_ids):
        raise Exception("File %s aleady exists" % (fname_prot_ids))
    with open(fname_prot_ids, 'w') as f:
        for prot_id in prot_ids:
            f.write(prot_id + '\n')
    return fname_prot_ids


def fetch_seqs(fname_prot_ids):
    """
    Fetch the NUC and AA sequences for the protein accession numbers in the input.
    
    @param fname_prot_ids:
        File containing list of protein accession #'s of homologs to get sequence data for.
        Each line contains 1 id.
    @return:
        (fname_nuc, fname_prot) = 2 FASTA files containing nucleotide and amino acid
        sequences, respectively, of the ids in fname_prot_ids
    """
    sys.stderr.write("\nSTEP: fetch_seqs(%s)\n" % fname_prot_ids)

    with open(fname_prot_ids, 'r') as f:
        prot_ids = [line.strip() for line in f if line.strip()]
    
    fname_prot = "homologs.aa.fasta"
    fname_nuc = "homologs.dna.fasta"
    if os.path.exists(fname_nuc) or os.path.exists(fname_prot):
        raise Exception("File %s and/or %s already exists" % (fname_nuc, fname_prot))

    prot_records, dna_metadata = _fetch_prot_records(prot_ids)

    f_prot = open(fname_prot, "w")
    for prot_record in prot_records:
        SeqIO.write(prot_record, f_prot, "fasta")
    f_prot.close()

    f_nuc = open(fname_nuc, "w")
    # Fetch DNA sequence.
    dna_handle = Entrez.efetch(
        db="nucleotide",
        id=",".join([dna_id for dna_id,_,_ in dna_metadata]),
        rettype="fasta")
    for dna_record, (dna_id, dna_inds, use_complement) in \
                zip(SeqIO.parse(dna_handle, "fasta"), dna_metadata):
        if len(dna_inds) == 1:
            dna_start, dna_end = dna_inds[0]
            dna_record.seq = dna_record.seq[dna_start-1:dna_end]
        else:
            seq = Seq('')
            for dna_start, dna_end in dna_inds:
                seq += dna_record.seq[dna_start-1:dna_end]
            dna_record.seq = seq
        # Get the correct range of nucleotides in the genome.
        if use_complement:
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

    fname_aln = "homologs.aa.aln"
    fname_log = "clustalw2.log"
    sys.stderr.write("\tRunning clustalw2, please be patient (may take minutes)...\n")
    proc = subprocess.Popen(
            "%s/clustalw2 -INFILE=%s -OUTFILE=%s" % (
                bin_dir(),
                os.path.abspath(fname_prot),
                os.path.abspath(fname_aln)),
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    stdout, stderr = proc.communicate()
    with open(fname_log, 'w') as f_log:
        f_log.write(stderr)
    sys.stderr.write("\tclustalw2 run successful, log available at %s\n"
            % os.path.abspath(fname_log))

    # Remove the extra file...
    if '.' in os.path.split(fname_prot)[-1]:
        fname_dnd = ".".join(fname_prot.split(".")[:-1]) + ".dnd"
    else:
        fname_dnd = fname_prot + ".dnd"
    os.remove(fname_dnd)

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
    1"""
    sys.stderr.write("\nSTEP: run_pal2nal(%s, %s)\n" % (fname_aln, fname_nuc))

    # Reorder fname_nuc according to the order of the proteins in fname_aln, which
    # was reordered due to CLUSTALW2.  Note that the first protein in each of
    # these files remains the same as at the start, however; this first protein
    # is our original query protein.
    nuc_records = [record for record in SeqIO.parse(fname_nuc, "fasta")]
    prot_records = [record for record in SeqIO.parse(fname_prot, "fasta")]
    records_map = dict((pr.id, nr) for pr, nr in zip(prot_records, nuc_records))
    fname_nuc2 = "homologs_ordered.dna.fasta"
    with open(fname_nuc2, "w") as f:
        for record in SeqIO.parse(fname_aln, "clustal"):
            SeqIO.write(records_map[record.id], f, "fasta")
    fname_codon = "homologs.codon.aln"
    # TODO: use subprocess
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
    fname_phy = "homologs.aa.phy"
    with open(fname_aln, "rU") as f_in:
        with open(fname_phy, "w") as f_out:
            SeqIO.convert(f_in, "clustal", f_out, "phylip-relaxed")

    current_dir = os.getcwd()
    fname_tree = fname_phy + "_phyml_tree.txt"
    if n_bootstrap > 1:
        bootstrap_str = "-b %d" % n_bootstrap
        fname_boot_trees = fname_phy + "_phyml_boot_trees.txt"
    else:
        bootstrap_str = ""
        fname_boot_trees = None

    fname_log = "phyml.log"
    sys.stderr.write("\tRunning phyml, please be patient (may take minutes)...\n")
    proc = subprocess.Popen(
            "%s/phyml -i %s -d aa %s %s" % (
                bin_dir(),
                os.path.abspath(fname_phy),
                bootstrap_str,
                current_dir),
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    stdout, stderr = proc.communicate()
    with open(fname_log, 'w') as f_log:
        f_log.write(stderr)
    sys.stderr.write("\tclustalw2 run successful, log available at %s\n"
            % os.path.abspath(fname_log))

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


CTL_TEMPLATE = os.path.join(os.path.dirname(__file__), "codonml_template.ctl")
def make_ctl(fname_codon, fname_tree, ctl_template=None, suffix=None):
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
    if not suffix:
        dirname_ctl = "codeml-main"
    else:
        dirname_ctl = "codeml%s" % suffix
    os.mkdir(dirname_ctl)
    fname_ctl = os.path.join(dirname_ctl, "codonml.ctl")
    wrote_seqfile = False
    wrote_treefile = False
    with open(fname_ctl, "w") as fw:
        with open(ctl_template, "r") as fr:
            for line in fr:
                if line.strip().startswith("seqfile"):
                    if wrote_seqfile:
                        raise ValueError("Bad ctl template file; multiple seqfile lines")
                    if not os.path.isabs(fname_codon):
                        fname_codon = "../" + fname_codon
                    fw.write("seqfile = %s\n" % fname_codon)
                    wrote_seqfile = True
                elif line.strip().startswith("treefile"):
                    if wrote_treefile:
                        raise ValueError("Bad ctl template file; multiple treefile lines")
                    if not os.path.isabs(fname_tree):
                        fname_tree = "../" + fname_tree
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
