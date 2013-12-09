import datetime
import os

def ts_str():
    return datetime.datetime.now().strftime("%Y%m%d-%H%M%S")

def bin_dir():
    current_dir = os.path.dirname(__file__)
    return os.path.abspath(os.path.join(current_dir, "..", "bin"))
