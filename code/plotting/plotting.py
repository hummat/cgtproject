#!/usr/bin/python
"""
========
PLOTTING
========

Those Python scripts can be used to generate the results found in the report of the project assignment of Computational
 Game Theory course at Vrije Universiteit Brussel (VUB).
"""

__author__ = "Matthias Humt (0545773)"
__email__ = "matthias.humt@tum.de"
__data__ = "04.01.2017"

import numpy as np
#import statsmodels.api as sm
import pandas as pd
from scipy import stats
from matplotlib import pyplot as plt
#from sklearn.linear_model import LogisticRegressionCV

# Official Tableau 20 colors used for plotting
# http://tableaufriction.blogspot.nl/2012/11/finally-you-can-use-tableau-data-colors.html
TABLEAU20 = [(31, 119, 180), (174, 199, 232), (255, 127, 14), (255, 187, 120),
             (44, 160, 44), (152, 223, 138), (214, 39, 40), (255, 152, 150),
             (148, 103, 189), (197, 176, 213), (140, 86, 75), (196, 156, 148),
             (227, 119, 194), (247, 182, 210), (127, 127, 127), (199, 199, 199),
             (188, 189, 34), (219, 219, 141), (23, 190, 207), (158, 218, 229)]


def _init():
    """Initializes color parameters to be used for plotting (rescale to matplotlib 0 to 1 encoding) and get a random
    seed from the system to generate random numbers.
    """
    np.random.seed()
    for index, color in enumerate(TABLEAU20):
        TABLEAU20[index] = (color[0] / 255., color[1] / 255., color[2] / 255.)


def _generate_data(samples=10000, scale=10, skew=(2, 10)):
    x = np.linspace(0, 1, samples)
    data = scale * stats.beta.pdf(x, skew[0], skew[1])
    # Todo: make better test data with pandas
    np.savetxt('test_data.csv', data, delimiter=',')


def _auc(means):
    plt.plot(means)
    plt.show()


def figure1():
    df = pd.read_csv("baseline4.csv", delimiter=',')
    data = pd.DataFrame({'step': df['step'] + df['complete'], 'time': df['complete'] - df['original']})
    groups = data.groupby('step')
    means = groups.mean()

    ewm_means = means['time'].ewm(span=10000, ignore_na=True).mean()
    tmp = np.zeros(10050)
    tmp[:] = np.nan
    tmp[means.index] = ewm_means
    _auc(means=tmp)

_init()
figure1()
