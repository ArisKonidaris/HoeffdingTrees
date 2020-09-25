

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
* Provides the user with the ability to use many treads when training the tree.
 
### Concurrent implementation
 
The training procedure of a simple, single threaded, Hoeffding tree can be seen in the following pseudocode.


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

### KafkaStreamProducer Class
This class is a simple implementation for a kafka producer that evenly partitions the data (based on the number of kafka 
partitions) and sends the corresponding data points to the specified topic. The program terminates when all data is send
to the kafka topic. In order for this program to work you have to create beforehand the kafka topic in the kafka cluster 
with the same topic name and number of partitions. A simple command is the following:

```
kafka/bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --topic OpenAQ
```

Class variables which need to be specified:
```
String filePath: Dataset file path, DEFAULT VALUE="/src/main/java/dataset/2018-04-04.csv"
String topicName: Name of Kafka Topic, DEFAULT VALUE="OpenAQ"
Integer partitions: Number of Kafka Partitions, DEFAULT VALUE="4"
```

### StratumStatistics Class
This class reads the input data from the kafka cluster based on KafkaStreamProducer.topicName and calculates the statistics 
for each group. This class is implemented as a Flink DataStream Job and can be executed with parallelism > 1. This Job does not
terminate by default and results are emitted once the KeyedProcessFunction timer fires for each stratum. The final result (i.e. 
set of strata) is sorted based on the group bys. This gives the user the opportunity to choose the values of the weight vector for 
the second phase of the algorithm. You have to specify the following parameters:
```
String kafka_data_topic: Name of kafka topic that holds the data, DEFAULT VALUE="OpenAQ"
String kafka_broker_list: Addresses of kafka brokers, DEFAULT VALUE="localhost:9092"
String parallelism: Number of Flink parallel task slots, DEFAULT VALUE="4"
String groupBys: Indexes of group bys columns (starting from 1 to n), DEFAULT VALUE="3,6,8"
Integer aggregate_column: Aggregate column (starting from 1 to n), DEFAULT VALUE="7"
String kafka_sink_topic: Name of kafka sink topic used to store stratistics per stratum, DEFAULT VALUE="Statistics"
Long timeout: Time in msec for KeyedProcessFunction timer. This is the maximum waiting period of each stratum, DEFAULT VALUE="1000"
``` 

```
/usr/local/flink-1.10.0/bin/flink run -c cvopt_jobs.StratumStatistics /home/user/../CVOPT/target/CVOPT-1.0-SNAPSHOT.jar --parallelism 4
```
A sample output of the above command is the following.Each line represents a stratum: 

(stratum group bys, stratum mean, stratum standard deviation, stratum size)
```
3> (CL,co,µg/m³,700.91,0.0,1)
3> (CL,no2,µg/m³,2.95,0.0,1)
3> (CL,o3,µg/m³,26.4,0.0,1)
3> (CL,pm10,µg/m³,23.47,0.0,1)
3> (CL,pm25,µg/m³,9.370000000000001,8.541174782585044,3)
3> (CL,so2,µg/m³,42.394,70.4238094965048,5)
3> (CO,pm25,µg/m³,27.0,0.0,1)
3> (IN,co,µg/m³,54010.98901098901,24793.42987478585,91)
3> (IN,no2,µg/m³,47.20289855072465,30.77198976207493,69)
3> (IN,o3,µg/m³,80.15384615384613,66.71448482306596,78)
3> (IN,pm10,µg/m³,138.25454545454545,56.02052162355512,55)
3> (IN,pm25,µg/m³,138.79104477611943,72.0461165149003,67)
3> (IN,so2,µg/m³,20.491803278688526,20.792198214336345,61)
3> (LV,no2,µg/m³,2.848877,0.0,1)
3> (LV,o3,µg/m³,97.429688,0.0,1)
``` 

### Sampling Class
This class is the second phase of the CVOPT algorithm and reads both the stratum statistics and the input data in order to apply the 
reservoir sampling algorithm. This class is implemented as a Flink DataStream Job and can be executed with parallelism > 1. This Job 
does not terminate by default and results are emitted once the KeyedProcessFunction timer fires for each stratum sample. In addition, 
we have implemented the Largest Remainder Method for the correct memory budget distribution M (i.e. si) and also we have implemented 
the Optimal Reservoir Sampling. You have to specify the following parameters:
```
String kafka_data_topic: Name of kafka topic that holds the data, DEFAULT VALUE="OpenAQ"
String kafka_statistics_topic: Name of kafka topic that holds the strata statistics, DEFAULT VALUE="Statistics"
String kafka_broker_list: Addresses of kafka brokers, DEFAULT VALUE="localhost:9092"
String parallelism: Number of Flink parallel task slots, DEFAULT VALUE="4"
String groupBys: Indexes of group bys columns (starting from 1 to n), DEFAULT VALUE="3,6,8"
int aggregate_column: Aggregate column (starting from 1 to n), DEFAULT VALUE="7"
Integer memory_budget: Size of memory budget, DEFAULT VALUE="150"
String weight_vector: Weight coefficients, DEFAULT VALUE="1,1,1,1,1,1,1,1,1,1,1,1,1,1,1"
Long timeout: Time in msec for KeyedProcessFunction timer. This is the maximum waiting period of each stratum, DEFAULT VALUE="1000"
```

```
/usr/local/flink-1.10.0/bin/flink run -c cvopt_jobs.Sampling  /home/user/.../CVOPT/target/CVOPT-1.0-SNAPSHOT.jar --parallelism 4
```
A sample output of the above command is the following.Each line represents a stratum sample: 

(stratum group bys, stratum mean, stratum standard deviation, stratum size, si, sample size, sample mean, sample standard deviation, sample records)
```
3> CL,so2,µg/m³, 42.394, 70.4238094965048, 5, 5, 5, 42.39399999999999, 70.4238094965048, 
CL, so2, µg/m³, 6.0
CL, so2, µg/m³, 2.97
CL, so2, µg/m³, 183.0
CL, so2, µg/m³, 15.0
CL, so2, µg/m³, 5.0

2> CO,pm25,µg/m³, 27.0, 0.0, 1, 1, 1, 27.0, 0.0, 
CO, pm25, µg/m³, 27.0

1> CL,pm25,µg/m³, 9.370000000000001, 8.541174782585044, 3, 3, 3, 9.370000000000001, 8.541174782585044, 
CL, pm25, µg/m³, 2.62
CL, pm25, µg/m³, 4.07
CL, pm25, µg/m³, 21.42
``` 

### Default Dataset
We used a real-world dataset, "OpenAQ, 2018-04-04.csv" with 436 data tuples and 11 columns of  157 KB. OpenAQ is a collection of air 
quality measurements of different substances, such as carbon monoxide, sulphur dioxide, etc. We downloaded the data set from:
 
http://openaq-data.s3.amazonaws.com/index.html

## Authors
* **Konidaris Vissarion**