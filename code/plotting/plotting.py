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
# import statsmodels.api as sm
import pandas as pd
# from scipy import stats
from matplotlib import pyplot as plt
import datetime
# from sklearn.linear_model import LogisticRegressionCV

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


def _generate_data(samples=10000, scale=10, a=2, b=10):
    # Todo: work in progress
    noise = scale * np.random.beta(a, b, samples)
    data = np.random.uniform(0, 50, samples)
    csv_data = pd.DataFrame(data={
        'trial': np.ones(samples).astype(int),
        'step': np.random.randint(1, samples+1, samples),
        'original': data,
        'complete': data + noise,
        'window': 115 * np.ones(samples).astype(int),
        'sups': np.zeros(samples).astype(int),
        'size': 100 * np.ones(samples).astype(int)
    })
    print(csv_data.head())
    csv_data.to_csv('test_data.csv', sep=',', index=False)


def _process_input(filename):
    df = pd.read_csv(filename, delimiter=',', dtype=int, header=0)
    df['x'] = df['step'] + df['complete']
    df['y'] = df['complete'] - df['original']
    groups = df.groupby('x')
    return groups.mean()


def _auc(y):
    data = y[~np.isnan(y)]
    data = data - data.min()
    return np.trapz(data, dx=1)


def _curve(data, ax, color, label):
    # Data processing
    means = _process_input(data)
    tmp = pd.DataFrame({'y': np.nan * np.zeros(means.index.max())})
    tmp['y'][means.index - 1] = means['y']
    y = tmp['y'].ewm(span=means.index.max(), ignore_na=True).mean()
    x = np.arange(means.index.max())

    # Plotting
    ax.spines['top'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_visible(False)
    ax.tick_params(axis='both', which='both', bottom='off', top='off',
                   labelbottom='on', left='off', right='off', labelleft='on', direction='in')
    plt.xticks(fontsize=14)
    plt.yticks(fontsize=14)
    ax.set_ylabel('Service Time', fontsize=16, labelpad=20)
    ax.set_xlabel('Time', fontsize=16, labelpad=20)
    ax.grid(True, linestyle='dashed', linewidth=.5, color='black', alpha=.3)
    ax.plot(x, y, color='black', linewidth=.5, label='_nolegend_')
    ax.fill_between(x, y, 0, color=color, label=label)
    plt.text(1000, y.max() - y.mean() / 3, 'Area=' + str(np.round(_auc(y) / 100000, 2)) + r'$\cdot 10^5$', fontsize=20)


def figure4(baseline, window50):
    fig, ax = plt.subplots(figsize=(12, 9))
    df = pd.read_csv(baseline, delimiter=',', dtype=int, header=0)
    df['x'] = df['step'] + df['complete']
    df['y'] = df['complete'] - df['original']
    groups = df.groupby('x')



def figure5(baseline, supervised, save=False):
    fig, ax = plt.subplots(figsize=(12, 9))
    base = _process_input(baseline)
    sup = _process_input(supervised)
    if base['y'].mean() >= sup['y'].mean():
        _curve(baseline, ax, TABLEAU20[0], 'Baseline')
        _curve(supervised, ax, TABLEAU20[2], '1 Supervisor')
    else:
        _curve(supervised, ax, TABLEAU20[2], '1 Supervisor')
        _curve(baseline, ax, TABLEAU20[0], 'Baseline')
    ax.legend(fontsize=14, frameon=False)
    if save:
        plt.savefig("figures/figure5_" + str(datetime.datetime.now()) + ".png", bbox_inches='tight')
    else:
        plt.show()


_init()
#figure4('csv_data/baseline_w5.csv', 'csv_data/N_one_sup_w25.csv')
