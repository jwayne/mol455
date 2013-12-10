import re
import sys


def postprocess(fn_rst):
    # Map of each model name to its results.
    all_results = {}
    current_model = None

    pat_probs = re.compile(r"^(\d+) ([A-Z])   (([.\d]+ )+)\(([ \d]+)\)  (.+)$")

    model_results = []
    with open(fn_rst, 'r') as f:
        while True: # for each model
            # Find next model to parse.
            line = f.readline()
            while line:
                if line.startswith("Model "):
                    current_model = int(line.strip().split(":")[0][6:])
                    break
                line = f.readline()
            # Did not find another model to parse.
            if not line:
                break

            # Sanity check.
            if current_model in all_results:
                raise ValueError("Model %d already parsed" % current_model)

            # Find dn/ds site classes.
            line = f.readline()
            count = 0
            while line:
                if line.startswith("dN/dS (w) for site classes (K="):
                    # Read K
                    K = int(re.search("[\d]+", line).group())
                    line = f.readline()
                    # Read ps
                    line = f.readline()
                    if not line.startswith("p:"):
                        raise ValueError("Bad data")
                    p_fields = line.strip().split()[1:]
                    if len(p_fields) != K:
                        raise ValueError("Bad data")
                    ps = map(float, p_fields)
                    # Read ws
                    line = f.readline()
                    if not line.startswith("w:"):
                        raise ValueError("Bad data")
                    w_fields = line.strip().split()[1:]
                    if len(w_fields) != K:
                        raise ValueError("Bad data")
                    ws = map(float, w_fields)
                    break
                count += 1
                if count > 8:
                    raise ValueError("Read too many lines without finding dn/ds site classes.")
                line = f.readline()
            # Did not find dn/ds site classes.
            if not line:
                raise ValueError("Bad data")

            # Find sequence name.
            line = f.readline()
            count = 0
            while line:
                if line.startswith("(amino acids refer to "):
                    seq_name = line.split(":")[1].strip()[:-1]
                    break
                count += 1
                if count > 3:
                    raise ValueError("Read too many lines without finding dn/ds site classes.")
                line = f.readline()
            if not line:
                raise ValueError("Early truncation")

            # Create the results object
            seq_results = SequenceResults(K, ps, ws, seq_name)

            # Find the probabilities for each site.
            f.readline()
            line = f.readline()
            pos = 1
            while line:
                match = re.match(pat_probs, line.strip())
                if not match:
                    break
                pos_str, aa, probs_str, _, mle_str, huh = match.groups()
                if int(pos_str) != pos:
                    raise ValueError("Bad data- aa pos doesn't match, expected %d got %d"
                        % (pos, pos_str))
                probs = map(float,probs_str.split())
                mle = float(mle_str.strip())
                seq_results.add_site(aa, probs, mle, huh)
                pos += 1
                line = f.readline()
            if not line:
                raise ValueError("Early truncation")

            count = 0
            while line:
                if line.startswith("lnL = "):
                    lnL = float(line[6:].strip())
                    seq_results.set_lnL(lnL)
                    break
                count += 1
                if count > 15:
                    raise ValueError("Read too many lines without finding lnL.")
                line = f.readline()
            if not line:
                raise ValueError("Early truncation")

            model_results.append(seq_results)

    return model_results



class SequenceResults(object):

    def __init__(self, K, ps, ws, seq_name):
        """
        @param K:
            Number of classes
        @param ps:
            p values for each class
        @param ws:
            w values for each class
        """
        self.K = K
        self.ps = ps
        self.ws = ws
        self.seq_name = seq_name
        #    1 K   0.56613 0.43387 ( 1)  0.434
        #    - aas (....probs....) mles  huhs
        self.aas = []
        self.probs = []
        self.mles = []
        self.huhs = []
        # likelihood?
        self.lnL = None

    def add_site(self, aa, probs, mle, huh):
        self.aas.append(aa)
        self.probs.append(probs)
        self.mles.append(mle)
        self.huhs.append(huh)

    def set_lnL(self, lnL):
        self.lnL = lnL


if __name__ == "__main__":
    postprocess(sys.argv[1])
