#!/usr/bin/python
from __future__ import division
import matplotlib as mpl
import matplotlib.pyplot as plt
import numpy as np
import re
import sys


class ModelResults(object):

    def __init__(self, model_id, seq_name, K, ps, ws):
        """
        @param K:
            Number of classes
        @param ps:
            p values for each class
        @param ws:
            w values for each class
        """
        self.model_id = model_id
        self.seq_name = seq_name
        self.K = K
        self.ps = ps
        self.ws = ws
        #    1 K   0.56613 0.43387 ( 1)  0.434
        #    - aas (....probs....) mles  huhs
        self.aas = []
        self.probs = []
        self.evs = None
        self.mles = []
        self.huhs = []
        # likelihood of entire analysis?
        self.lnL = None

    def add_site(self, aa, probs, mle, huh):
        self.aas.append(aa)
        self.probs.append(probs)
        self.mles.append(mle)
        self.huhs.append(huh)

    def calc_evs(self):
        self.evs = [np.dot(prob, self.ws) for prob in self.probs]

    def set_lnL(self, lnL):
        self.lnL = lnL



def postprocess(fn_rst):
    # Map of each model name to its results.
    all_results = {}
    model_id = None

    pat_probs = re.compile(r"^(\d+) ([A-Z])   (([.\d]+ )+)\(([ \d]+)\)  (.+)$")

    with open(fn_rst, 'r') as f:
        while True: # for each model
            # Find next model to parse.
            line = f.readline()
            while line:
                if line.startswith("Model "):
                    model_id = int(line.strip().split(":")[0][6:])
                    break
                if line.startswith("TREE #"):
                    # No model id in output because only 1 model was computed
                    # in this codeml analysis.
                    model_id = -1
                    break
                line = f.readline()
            # Did not find another model to parse.
            if not line:
                break

            # Sanity check.
            if model_id in all_results:
                raise ValueError("Model %d already parsed" % model_id)

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
                if count > 6:
                    raise ValueError("Read too many lines without finding sequence name.")
                line = f.readline()
            if not line:
                raise ValueError("Early truncation")

            # Create the results object
            model_results = ModelResults(model_id, seq_name, K, ps, ws)

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
                model_results.add_site(aa, probs, mle, huh)
                pos += 1
                line = f.readline()
            if not line:
                raise ValueError("Early truncation")
            model_results.calc_evs()

            count = 0
            while line:
                if line.startswith("lnL = "):
                    lnL = float(line[6:].strip())
                    model_results.set_lnL(lnL)
                    break
                count += 1
                if count > 30:
                    raise ValueError("Read too many lines without finding lnL.")
                line = f.readline()
            if not line:
                raise ValueError("Early truncation")

            all_results[model_id] = model_results
    return all_results


def draw_bars_permodel(all_results):
    n_models = len(all_results)
    n_sites = len(all_results.values()[0].probs)
    plt.figure(1, figsize=(n_sites/10,3*n_models))
    cmap = mpl.cm.PiYG

    for i, model_id in enumerate(sorted(all_results.keys())):
        model_results = all_results[model_id]
        plt.subplot("%d1%d" % (n_models, i+1))
        probs = np.array(model_results.probs)
        probs_t = np.transpose(probs)
        n_classes = len(probs_t)

        # Draw stacked bar chart
        width = 1
        inds = np.arange(0,n_sites*width,width)
        colors = [cmap(min(w,2)/2) for w in model_results.ws]

        probs_layer_prev = np.zeros(len(probs))
        bars = []
        for probs_layer, color in reversed(zip(probs_t, colors)):
            bar = plt.bar(inds, probs_layer, width, bottom=probs_layer_prev, color=color)
            bars.append(bar)
            probs_layer_prev += probs_layer
        plt.title("Model %d" % model_id)
        plt.xlabel('Sites')
        plt.xticks(inds+width/2, model_results.aas, fontsize=8)
        plt.ylabel('Probability')
        plt.ylim(0,1)
        labels = reversed(["w = %.3f" % w for w in model_results.ws])
        plt.legend(bars, labels, prop={'size':10})
    plt.tight_layout()


def draw_ev(all_results):
    n_models = len(all_results)
    n_sites = len(all_results.values()[0].probs)
    plt.figure(1, figsize=(n_sites/10,3))

    plt.subplot("111")
    width = 1
    inds = np.arange(0,n_sites*width,width)
    model_ids = sorted(all_results.keys())
    for model_id in model_ids:
        model_results = all_results[model_id]
        evs = model_results.evs
        plt.plot(inds, evs, ".-")
    plt.xlabel('Sites')
    plt.xticks(inds+width/2, model_results.aas, fontsize=8)
    plt.ylabel('w (dN/dS)')
    labels = ["Model %d" % mid for mid in model_ids]
    plt.legend(labels, prop={'size':10})
    plt.tight_layout()


def draw_ev_bootstrap(allall_results):
    n_bootstrap = len(allall_results)
    model_ids = sorted(allall_results[0].keys())
    n_models = len(model_ids)
    n_sites = len(allall_results[0].values()[0].probs)
    plt.figure(1, figsize=(n_sites/10,3*n_models))

    for i, model_id in enumerate(model_ids):
        plt.subplot("%d1%d" % (n_models, i+1))
        width = 1
        inds = np.arange(0,n_sites*width,width)
        for all_results in allall_results:
            model_results = all_results[model_id]
            evs = model_results.evs
            plt.plot(inds, evs, ".-")
        plt.xlabel('Sites')
        plt.xticks(inds+width/2, model_results.aas, fontsize=8)
        plt.ylabel('w (dN/dS)')
        if model_id >= 0:
            plt.title("Model %d" % model_id)
        else:
            plt.title("Model unknown")
    plt.tight_layout()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise Exception("python %s <codeml rst output files>" % sys.argv[0])
    allall_results = []
    for codeml_rst in sys.argv[1:]:
        all_results = postprocess(codeml_rst)
        allall_results.append(all_results)
    if len(allall_results) == 1:
        draw_bars_permodel(all_results)
        draw_ev(all_results)
        plt.show()
    else:
        draw_ev_bootstrap(allall_results)
        plt.show()
