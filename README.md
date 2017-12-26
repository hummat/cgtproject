# Computational Game Theory Project

Code and report for the final project of the Computational Game Theory course at Vrije Universitetit Brussel (VUB) WS2017.

## Paper: Accelerating multi-agent reinforcement learning with dynamic co-learning 

## Directories
Explanation of the proposed directory structure. Feel free to change anything you don't like.
- code: All code written to reproduce the results.
- report: All files needed to generate the report (figures, bibliography...)
- assets: All additional material (referenced papers, templates, problem specification...)

## Tasks
Some possible tasks. Just add, change, delete whatever you see fit. I'd propose a meeting between christmas and new year to see where we all are on our tasks and if we need to put joint effort into something.
- Writing/formatting of the report
  - Responsible: Matthias (?)
  - Deadline: 08.01.2018
- Write code to reproduce results from the papes
  - Responsible:
  - Deadline: 05.01.2018 (?)
- Reproduce figures from the paper
  - Responsible: Matthias (?)
  - Deadline: 05.01.2018 (?)
- Additional work extending the paper to gain extra points:
  - Responsible:
  - Deadline: 05.01.2018 (?)
- Review article of other group:
  - Responsible:
  - Deadline: 15.01.2018
- (PowerPoint) Presentation of our work for the exam:
  - Responsible:
  - Deadline: 18.01.2018 (Exam is on the 19th)

## System components and parameterization
Summary of the parameters and components provided in the paper to implement the system.
- Agents: 100 (default), 324, 729
  - Each agent maintains a queue of tasks called the _processing queue_ (each with a service time s).
  - Agents can decide to work on the current task themselves (task moves from _routing queue_ to _processing queue_) or       to forward it to any of its neighbors.
  - Service time s: How many time units does this task take to be completed
  - Learning window K: 50, 75, 100, 115 (default), 200, 300, 400, 500
  - Reward function: 1/average service time over last K time steps
  - Reward: 1/s upon selection of a task
  - Q values: The actual service time s needed to complete the task?
  - Context features: (1) rate of task receival from the environment of neighbors, (2) rate of task receival from other agents of neighbors, (3) load (probably sum of service time s of tasks in the agents processing queue?) relative to mean load of neighbors
- Environment: Creates tasks and associates them with an agent by placing the task in the agents _routing queue_.
  - How are those tasks generated?
  - How does the agent association work?
- Supervisors: 0 (baseline), 1, 4, 9
  -
- Run length: 10000 time units
- Noise level: 0, 0.25, 0.5, 0.75, 1
- Simulations: 30 (for mean/variance plot)
- Primary measure of performance: area under the learning curve (AUC)
