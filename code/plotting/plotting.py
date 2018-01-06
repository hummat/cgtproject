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
__date__ = "06.01.2017"

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
    return df


def _auc(mean):
    tmp = pd.DataFrame({'y': np.nan * np.zeros(mean.index.max())})
    tmp['y'][mean.index - 1] = mean['y']
    y = tmp['y'].ewm(span=mean.index.max(), ignore_na=True).mean()
    data = y[~np.isnan(y)]
    data = data - data.min()
    return np.trapz(data, dx=1)


def _curve(mean, ax, color, label=None, fill=False, area=False):
    # Data processing
    tmp = pd.DataFrame({'y': np.nan * np.zeros(mean.index.max())})
    tmp['y'][mean.index - 1] = mean['y']
    y = tmp['y'].ewm(span=mean.index.max(), ignore_na=True).mean()
    x = np.arange(mean.index.max())

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
    if fill:
        ax.plot(x, y, color='black', linewidth=.5, label='_nolegend_')
        ax.fill_between(x, y, 0, color=color, label=label)
    else:
        ax.plot(x, y, color=color, linewidth=1)
    if area:
        plt.text(1000, y.max() - y.mean() / 3, 'Area=' + str(np.round(_auc(mean) / 100000, 2)) + r'$\cdot 10^5$', fontsize=20)


def _box(trials, base_trials=None):
    if base_trials is None:
        box = np.empty(len(trials), dtype=np.ndarray)
        for name, trial in trials:
            mean = trial.groupby('x').mean()
            box[name - 1] = _auc(mean)
        return box
    else:
        base_box = np.empty(len(base_trials), dtype=np.ndarray)
        for name, trial in base_trials:
            base_mean = trial.groupby('x').mean()
            base_box[name - 1] = _auc(base_mean)
        box = np.empty(len(trials), dtype=np.ndarray)
        for name, trial in trials:
            mean = trial.groupby('x').mean()
            box[name - 1] = _auc(mean)
        return box / base_box.mean()


def line_plot(filename, avrg=False, save=False):
    ig, ax = plt.subplots(figsize=(12, 9))
    input_ = _process_input(filename)
    if avrg:
        mean = input_.groupby('x').mean()
        _curve(mean, ax, color=TABLEAU20[0], fill=True, area=True)
    else:
        trials = input_.groupby('trial')
        for name, trial in trials:
            mean = trial.groupby('x').mean()
            _curve(mean, ax, color=TABLEAU20[name], label=str(name), fill=False, area=False)
    if save:
        plt.savefig("figures/line_" + filename + ".png", bbox_inches='tight')
    else:
        plt.show()


def box_plot(data, compare=None, ax=None, labels=None, colors='black', median=False, notch=False, save=False):
    input_ = _process_input(data)
    trials = input_.groupby('trial')
    windows = list()
    if compare is not None:
        for window in compare:
            comp = _process_input(window)
            windows.append(comp.groupby('trial'))
    if ax is None:
        fig, ax = plt.subplots(figsize=(12, 9))
    ax.spines['top'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.tick_params(axis='both', which='both', bottom='off', top='off',
                   labelbottom='on', left='on', right='off', labelleft='on', direction='out')
    plt.xticks(fontsize=14)
    plt.yticks(np.arange(0, 3, .5), fontsize=14)
    ax.set_ylabel('Relative Area Under\nLearning Curve', fontsize=16, labelpad=20)
    ax.set_xlabel('Window Size', fontsize=16, labelpad=20)
    ax.yaxis.grid(True, linestyle='dashed', linewidth=.5, color='black', alpha=.3)
    ax.set_ylim([0, 3])
    if compare is None:
        ax.boxplot([_box(trials)], labels=labels, notch=notch)
    else:
        box_list = list()
        for window in windows:
            box_list.append(_box(window, trials))
        params = ax.boxplot(box_list, labels=labels, notch=notch)
        if median:
            median_y = list()
            median_x = list()
            for med in params['medians']:
                median_y.append(med.get_ydata()[0])
                median_x.append(.5 * (med.get_xdata()[0] + med.get_xdata()[1]))
                med.set_color('black')
            ax.plot(median_x, median_y, color=TABLEAU20[6], linestyle='dashed', marker='.', markersize=10)
    if ax is None:
        if save:
            plt.savefig("figures/box_" + data + ".png", bbox_inches='tight')
        else:
            plt.show()


def figure4(baseline, windows, labels=None, save=False):
    fig, ax = plt.subplots(figsize=(12, 9))
    if labels is None:
        box_plot(baseline, windows, ax, median=True)
    else:
        box_plot(baseline, windows, ax, labels=labels, median=True)
    if save:
        plt.savefig("figures/figure4_" + str(datetime.datetime.now()) + ".png", bbox_inches='tight')
    else:
        plt.show()


def figure5(baseline, supervised, save=False):
    fig, ax = plt.subplots(figsize=(12, 9))
    base = _process_input(baseline)
    base_mean = base.groupby('x').mean()
    sup = _process_input(supervised)
    sup_mean = sup.groupby('x').mean()
    if base['y'].mean() >= sup['y'].mean():
        _curve(base_mean, ax, color=TABLEAU20[0], label='Baseline', fill=True, area=True)
        _curve(sup_mean, ax, color=TABLEAU20[2], label='1 Supervisor', fill=True, area=True)
    else:
        _curve(sup_mean, ax, color=TABLEAU20[2], label='1 Supervisor', fill=True, area=True)
        _curve(base_mean, ax, color=TABLEAU20[0], label='Baseline', fill=True, area=True)
    ax.legend(fontsize=14, frameon=False)
    if save:
        plt.savefig("figures/figure5_" + str(datetime.datetime.now()) + ".png", bbox_inches='tight')
    else:
        plt.show()


def figure7(baseline, filenames, labels=None, save=False):
    fig, ax = plt.subplots(figsize=(12, 9))
    if labels is None:
        box_plot(data=baseline, compare=filenames, ax=ax, notch=True)
    else:
        box_plot(data=baseline, compare=filenames, ax=ax, labels=labels, notch=True)
    if save:
        plt.savefig("figures/figure4_" + str(datetime.datetime.now()) + ".png", bbox_inches='tight')
    else:
        plt.show()


_init()
fig7_csv = [
    'csv_data/N_one_sup_w25.csv',
    'csv_data/N_one_sup_w25n100s4.csv',
    'csv_data/N_one_sup_w25n100s9.csv',
    'csv_data/baseline_w10.csv'
]
fig7_labels = [
    '1 Sup',
    '4 Sup',
    '9 Sup',
    'Baseline / No Sup'
]
#figure7(baseline=fig7_csv[3], filenames=fig7_csv, labels=fig7_labels)
fig4_windows = [
    'csv_data/N_one_sup_w25.csv',
    'csv_data/N_one_sup_w50.csv',
    'csv_data/N_one_sup_w115.csv'
]
#figure4(baseline='csv_data/baseline_w10.csv', windows=fig4_windows, labels=['25', '50', '115'])
#figure5('csv_data/baseline_w10.csv', 'csv_data/N_one_sup_w25n100s1.csv', save=True)
