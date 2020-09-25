

## Hoeffding Tree implementation in Scala

TECHNICAL UNIVERSITY OF CRETE

SCHOOL OF ELECTRICAL & COMPUTER ENGINEERING

Master course: ECE615

### Getting Started

The Hoeffding Tree algorithm is a well-known classifier that can be trained on streaming labeled data. In reality a 
Hoeffding Tree is an online version of a decision tree. This project is a From-Scratch implementation of the Hoeffding 
Tree classifier on a widely used functional programming language, Scala. The current project provides a fully documented 
Maven project library for training, making predictions and storing Hoeffding Trees, by using single or even multiple 
threads. The project:

* Provides a script for creating a dummy training data set for a classification problem.
* Contains Kafka clients for producing and consuming a training set.
* Implements a Hoeffding Tree for multiclass classification of discrete and numerical features. Hash Map counters are used 
in case of discrete features, and Gaussian distributions in case of numerical ones.
* Provides the ability for training a Hoeffding Tree, as well as using it as a predictor on unlabeled data.
* Implements a Memory Management mechanism for bounding the size of the Hoeffding Tree.
* The Hoeffding Tree can be Serialiazed and strored. The serialize method of the algorithm converts the tree into a JSON 
String that can be stored in simple text file. The same JSON String can be used to deserialize and load a trained tree, 
by using the deserialize method of the Hoeffding Tree.
* Provides the user with the ability to use many threads when training the tree.
 
### Concurrent implementation
 
The training procedure of a simple, single threaded, Hoeffding tree can be seen in the following pseudocode.

![alt text](https://github.com/ArisKonidaris/HoeffdingTrees/HoeffdingTreePseudocode.png?raw=true)


The above Algorithm  lists pseudo-code for inducing a Hoeffding tree from a data stream. Line 1 starts out the tree data 
structure as a single root node. Lines 2-18 form a loop that is performed for every training example. Every data point 
is filtered down the tree to an appropriate leaf, depending on the tests present in the decision tree built to that 
point (line 3). This leaf is then updated (line 4)—each leaf in the tree holds the sufficient statistics needed to make 
decisions about further growth. The sufficient statistics that are updated are those that make it possible to estimate 
the information gain of splitting on each attribute. Line 5 simply points out that n_l is the example count at the leaf, 
and it too is updated. Technically n_l can be computed from the sufficient statistics. For efficiency reasons the code 
block from lines 6-17 is only performed periodically, every n min examples for a particular leaf, and only when 
necessary, when a mix of observed classes permits further splitting. The delayed evaluation controlled by n min is 
discussed in Section 3.2.3. Lines 7-11 perform the test described in the previous section, using the Hoeffding bound to 
decide when a particular attribute has won against all of the others. G is the splitting criterion function 
(information gain) and G is its estimated value. If an attribute has been selected as the best choice, lines 12-15 split
the node, causing the tree to grow. Preventing the tree from using too much memory is essential for a robust online 
algorithm. The current implementation provides such a Memory Management procedure that bounds the tree size to an upper 
byte bound given by the user.

In the concurrent implementation, we emulate a parallel training procedure by using the parameter server paradigm. Each 
worker thread has a complete copy of the Hoeffding Tree. A worker trains its local tree on its receiving training data but
without splitting the leaves.

## Authors
* **Konidaris Vissarion**