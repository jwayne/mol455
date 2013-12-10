import re


def postprocess(fn_rst):
    # Map of each model name to its results.
    all_results = {}
    current_model = None

    pat_probs = re.compile("^ +(\d+) ([A-Z])   (([.\d]+) )+\(([\w\d]+)\)  (.+)$")

    results = []
    with open(fn_rst, 'r') as f:
        while True: # for each model
            # Find next model to parse.
            line = f.readline()
            while line:
                if line.startswith("Model "):
                    current_model = int(line.split(":")[0][6:])
                    break
                line = f.readline()
            # Did not find another model to parse.
            if not line:
                break
            # Sanity check.
            if current_model in all_results:
                raise ValueError("%d already parsed" % current_model)

            # Find dn/ds site classes.
            line = f.readline()
            count = 1
            while line:
                if line.startswith("dN/dS (w) for site classes (K="):
                    # Read K
                    K = int(re.search("[\d]+", line).group())
                    line = f.readline()
                    # Read ps
                    line = f.readline()
                    if not line.startswith("p:"):
                        raise ValueError("Bad data")
                    p_fields = line.split()[1:]
                    if len(p_fields) != K:
                        raise ValueError("Bad data")
                    ps = map(float, p_fields)
                    # Read ws
                    line = f.readline()
                    if not line.startswith("w:"):
                        raise ValueError("Bad data")
                    w_fields = line.split()[1:]
                    if len(w_fields) != K:
                        raise ValueError("Bad data")
                    ws = map(float, w_fields)
                    break
                count += 1
                if count > 8:
                    raise ValueError("Read too many lines without finding dn/ds site classes.")
            # Did not find dn/ds site classes.
            if not line:
                raise ValueError("Bad data")

            # Find sequence name.
            line = f.readline()
            count = 1
            while line:
                if line.startswith("(amino acids refer to "):
                    seq_name = line.split(":")[1].strip()[:-1]
                    break
                count += 1
                if count > 3:
                    raise ValueError("Read too many lines without finding dn/ds site classes.")
            if not line:
                raise ValueError("Bad data")

            # Create the results object
            results = SequenceResults(K, ps, ws, seq_name)

            # Find the probabilities for each site.
            f.readline()
            line = f.readline()
            while re.match(
                z
            if not line:
                raise ValueError("Bad data")

class SequenceResults(object):

    def __init__(self, K, ps, ws):
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
        #    1 K   0.56613 0.43387 ( 1)  0.434
        #    - aas (....probs....) mles  huhs
        self.aas = []
        self.probs = []
        self.mles = []
        self.huhs = []


    def add_site(self, aa, prob, mle, huh):
        z
