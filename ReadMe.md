

## Hoeffding Tree implementation in Scala

TECHNICAL UNIVERSITY OF CRETE

SCHOOL OF ELECTRICAL & COMPUTER ENGINEERING

Master course: ECE615


### Getting Started

The Hoeffding Tree algorithm is a well-known classifier that can be trained on streaming labeled data. In reality, a 
Hoeffding Tree is an online version of a decision tree. This project is a From-Scratch implementation of the Hoeffding 
Tree classifier on a widely used functional programming language, Scala. The current project provides a fully documented 
Maven project library for training, making predictions and storing Hoeffding Trees, by using single or even multiple 
threads. The project:

* Provides a script for creating a dummy training data set for a classification problem.
* Contains Kafka clients for producing and consuming a training set.
* Implements a Hoeffding Tree for multiclass classification of discrete and numerical features. Hash Map counters are 
used in case of discrete features, and Gaussian distributions in case of numerical ones.
* Provides the ability for training a Hoeffding Tree, as well as using it as a predictor on unlabeled data.
* Implements a Memory Management mechanism for bounding the size of the Hoeffding Tree.
* The Hoeffding Tree can be Serialized and stored. The serialize method of the algorithm converts the tree into a JSON 
String that can be stored in a simple text file. The same JSON String can be used to deserialize and load a trained tree, 
by using the deserialize method of the Hoeffding Tree.
* Provides the user with the ability to use many threads when training the tree for better performance.
 

### Concurrent implementation
 
The training procedure of a simple, single threaded, Hoeffding tree can be seen in the following pseudocode.

<p align="center">
  <img width="579" height="429" src="https://github.com/ArisKonidaris/HoeffdingTrees/blob/master/HoeffdingTreePseudocode.png">
</p>

The above Algorithm  lists pseudo-code for inducing a Hoeffding tree from a labeled data stream. Line 1 starts out the 
tree data structure as a single root node. Lines 2-18 form a loop that is performed for every training example. Every 
data point is filtered down the tree to an appropriate leaf, depending on the tests present in the decision tree built 
to that point (line 3). This leaf is then updated (line 4). Each leaf in the tree holds the sufficient statistics needed 
to make decisions about further growth. The sufficient statistics that are updated are those that make it possible to 
estimate the information gain of splitting on each attribute. Line 5 simply points out that n_l is the example count at 
the leaf, and it too is updated. Technically n_l can be computed from the sufficient statistics. For efficiency reasons 
the code block from lines 6-17 is only performed periodically, every n_min examples for a particular leaf, and only when 
necessary, when a mix of observed classes permits further splitting. Lines 7-11 perform the test described in the 
previous section, using the Hoeffding bound to decide when a particular attribute has won against all of the others. G 
is the splitting criterion function (information gain) and G is its estimated value. If an attribute has been selected 
as the best choice, lines 12-15 split the node, causing the tree to grow. Preventing the tree from using too much memory
is essential for a robust online algorithm. The current implementation provides such a Memory Management procedure that 
bounds the tree size to an upper byte bound given by the user.

In the concurrent implementation, we emulate a parallel training procedure by using the parameter server paradigm. Each 
worker thread has a complete copy of the Hoeffding Tree and receives its own data stream of labeled examples. Thread
workers train their trees on their receiving stream by only updating the sufficient statistics of the leaves, without 
any splitting permitted. Each time a worker has updated each leaf on its own tree for n_l / k times, where k is the 
number of thread workers, it sends a signal to the hub/coordinator with the id of that leaf. The coordinator then 
increments a counter for that leaf id. When a counter for a leaf reaches k, then synchronization needs to happen in 
order to combine those parallel corresponding leaves with the same leaf id. All worker threads suspend and wait for the 
synchronization to happen after they complete training on the last received data point before or during the issuing of 
the synchronization. The coordinator combines the corresponding leaves that triggered the synchronization by using
their unique ids. In the discrete features case, combining is simply done by adding the sufficient statistics counters 
of those leaves. In the numerical features case, the Gaussian distributions of the attributes for each class are 
combined. Then, the hub computes the splitting criterion function (information gain) G for each attribute and the 
Hoeffding bound to determine whether to split the combined leaf or not. If so, the hub splits the leaf into a test node 
with two leaf nodes and updates all the parallel Hoeffding trees so that they are similar. I not, no further action is 
done by the hub. After the synchronization procedure, all the workers resume their training procedure. In two words, the
workers are responsible for updating the sufficient statistics of the leaves in a parallel, and the coordinator is 
responsible for combining the leaves and splitting them if necessary while ensuring that all the trees are exact copies 
after each such an action. 


### Tests
We performed tests to examine the performance of the algorithm as the parallelism. We used a binary classification data
set consisted of one million examples with thirty numerical (real valued) features. The data set is streamed through a 
Kafka topic with that many partitions as the parallelism of the training procedure. This is done so that each thread 
worker can read from one partition. Below are the figures that provide the test results for n_min = 400.

<p align="center">
  <img src="https://github.com/ArisKonidaris/HoeffdingTrees/blob/master/Accuracy_vs_Parallelism.png" width="400" />
  <img src="https://github.com/ArisKonidaris/HoeffdingTrees/blob/master/Duration_vs_Parallelism.png" width="400" /> 
</p>

On the figure to the left we can observe the predictive performance of the algorithm slowly degrades as the number of 
concurrent workers increases. This may be due to the fact that fewer leaf splits are performed. This is the case because 
each worker sends a signal to the hub after n_l / k data points are fitted into each leaf. The coordinator issues a 
synchronization of the leaf when the number of signals reaches k. However, the workers need to finish what they were
doing before the coordinator issues the synchronization. This means that in the worst case scenario, at most 
$\frac{n_l}{k}\left(2k-1\right)=n_l\left(2-\frac{1}{k}\right)$ are globally observed on a leaf before splitting is 
attempted. This leaves to reduction in the size of the tree that may account to the loss of predictive performance.

## Authors
* **Konidaris Vissarion**