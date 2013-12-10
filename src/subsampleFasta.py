import os, sys, random
from Bio import SeqIO

if __name__ == '__main__':
#check for legal number of args
#    if len(sys.argv) != 5:
#        sys.exit("expect cmd args: python %s <seq.dna.fasta> <seq.aa.fasta> num_seqs output_name" % sys.argv[0])
    if len(sys.argv) != 6:
        sys.exit("expect cmd args: python %s <seq.dna.fasta> <seq.aa.fasta> num_seqs num_codons output_name" % sys.argv[0])

    # read command args
    dna_finput = sys.argv[1]
    aa_finput = sys.argv[2]
    num_samples = int(sys.argv[3])
    num_codons = int(sys.argv[4])
    output_fname = sys.argv[5]

# read all seqs
    dna_oseqs = list(SeqIO.parse(dna_finput, "fasta"))
    aa_oseqs = list(SeqIO.parse(aa_finput, "fasta"))

# generate subsample of these seqs
    (dna_sseqs, aa_sseqs) = zip(* random.sample( zip(dna_oseqs, aa_oseqs), num_samples))

# keep the first num_codons for each
    dna_seqs = []
    aa_seqs = []
    for rec in dna_sseqs:
        dna_seqs.append(rec[0:num_codons*3])
    for rec in aa_sseqs:
        aa_seqs.append(rec[0:num_codons])
# write to new file
    output_dna = output_fname + ".dna.fasta"
    output_aa = output_fname + ".aa.fasta"

    SeqIO.write(dna_seqs, output_dna, "fasta")
    SeqIO.write(aa_seqs, output_aa, "fasta")
