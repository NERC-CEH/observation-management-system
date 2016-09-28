# Infrastructure

## Introduction

All the software currently used is designed to support multi-machine deployment through clustering.  The only component setup in this way for this project is Cassandra.  Kafka, Redis, and Flink are setup as single node/local clusters at present.

## Running on JASMIN Infrastructure

Within the JASMIN infrastructure at present we are running five virtual machines: three Cassandra nodes, a single Kafka node that also runs Redis, and a single Flink node.  There are only three available outward facing network connections, and so these have been assigned to a single Cassandra node, the Kafka node, and the Flink node.  The diagram below shows the machine setup:

![VM Config](graphics/JASMIN_setup.png?raw=true "Virtual machine configuration")

For connecting to the virtual machines without internet facing network connections, we can proxy in via one of the internet facing machines using a ssh config entry like below:

```
Host castwo
        ProxyCommand ssh username@internetFacingMachineAddress -W %h:%p
        HostName internalAddress
        User username
        ForwardAgent yes

```
### VM Setup

#### Initial Creation and Configuration

The following steps are the same across all virtual machines:

* Built from the Ubuntu-14.04-LTS-x86-64 template
* Inbound traffic from the internet: allowed when applicable as limited number of addresses
* Reconfigure to either: 4, 8, or 12 CPU
* Reconfigure to either: 4, 8, or 11 GB RAM
* Reconfigure new disk with ~ 260 GB or 150 GB disk space

#### Formatting Extra Disk

The disk was formatted with XFS, recommended for Cassandra and used for the rest out of consistency:

```
# To create the primary first partition with default values: select n followed by all defaults, then w.
sudo fdisk /dev/sdb
 
# To allow formatting as XFS
sudo apt-get install xfsprogs xfsdump
sudo mkfs.xfs /dev/sdb1
```

Once the disk has been formatted a mount point needs to be created and an entry added to the fstab:

```
# For Cassandra
sudo mkdir /mnt/cassandra
# For Kafka
sudo mkdir /mnt/kafka
# For Redis
sudo mkdir /mnt/redis

# To retrieve the UUID for fstab
sudo -i blkid
  
# In fstab added the following
UUID=output_of_above_goes_here /mnt/cassandra xfs  defaults,noatime,nobootwait  1   2
```


#### Securing Ports

By default on the JASMIN hosted machines, ufw is enabled, and allows OpenSSH connections on port 22 only; no other ports are open.  For internal communication between nodes it is necessary to open up the respective ports each application requires.  As the IP addresses assigned to the nodes are static, this is a feasible approach.

To further secure the nodes, fail2ban was installed on the SSH bastion machines, the local configuration created as below, and modified to suit.

```
sudo apt-get install fail2ban
sudo cp /etc/fail2ban/jail.conf /etc/fail2ban/jail.local
```

#### Installing Docker

Docker is installed following the instructions found here:

https://docs.docker.com/engine/installation/linux/ubuntulinux/

```
sudo apt-get update
sudo apt-get install apt-transport-https ca-certificates
sudo apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D

echo "deb https://apt.dockerproject.org/repo ubuntu-trusty main" | sudo tee -a /etc/apt/sources.list.d/docker.list

sudo apt-get update
sudo apt-get install linux-image-extra-$(uname -r)

sudo apt-get install docker-engine
```

It should be noted that while the containers that have persistent data such as Cassandra, Kafka, and Flink have their data directories mounted to the host using the `-v` flag, this data appears to be ignored if the container is removed and re-instantiated with the Kafka instance.  The data continues to stay in the host folder, but it is not picked up by the container.  For example, with Kafka, create a number of topics, then remove and re-instantiate the container.  Try listing the topics, nothing will be returned, however if you browse to the host data directory, you will see the topic folders are still located there.

TODO: 

* Create Flink docker image with state setup correctly
   
#### Cassandra

The official image is used from:

https://hub.docker.com/_/cassandra/

And a very good introduction to production level deployments is found here:

http://www.slideshare.net/planetcassandra/cassandra-and-docker-lessons-learnt

When setting up the docker container there are a number of environment variables that must be set:

* -v /cassandra:/var/lib/cassandra
    + map the host directory /cassandra to the data directory /var/lib/cassandra on the docker node
* CASSANDRA_LISTEN_ADDRESS
    + set to the internal IP address of the node
* CASSANDRA_START_RPC
    + set to false 
* CASSANDRA_SEEDS
    + the internal IP addresses of the first two nodes
* CASSANDRA_CLUSTER_NAME
    + The ID of the cluster
* CASSANDRA_DC
    + The datacentre of the node, set to "JASMIN" for all nodes
* CASSANDRA_RACK
    + The rack of the node within the datacentre, set to "Default" for all nodes
* CASSANDRA_ENDPOINT_SNITCH
    + The snitch used by the node, set to SimpleSnitch

Because the flag `--net=host` is used, the port flags (e.g. -p 7000:7000 -p 9042:9042 -p 7199:7199 -p 7001:7001) are unnecessary as the ports are mapped directly to either the listen_host, 0.0.0.0, or the local 127.0.0.1 as applicable.

The data directory in the Docker container is mounted to the `/mnt/cassandra` host directory.

```
docker run --name cassandra_node_one -d  -e CASSANDRA_LISTEN_ADDRESS=192.168.3.2 -e CASSANDRA_BROADCAST_ADDRESS=192.168.3.2 -e CASSANDRA_START_RPC=false -e CASSANDRA_SEEDS=192.168.3.2,192.168.3.3 -e CASSANDRA_CLUSTER_NAME=ObservationStore -e CASSANDRA_DC=JASMIN -e CASSANDRA_RACK=default -e CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch -v /mnt/cassandra:/var/lib/cassandra --net=host cassandra:3.7

docker run --name cassandra_node_two -d  -e CASSANDRA_LISTEN_ADDRESS=192.168.3.3 -e CASSANDRA_BROADCAST_ADDRESS=192.168.3.3 -e CASSANDRA_START_RPC=false -e CASSANDRA_SEEDS=192.168.3.2,192.168.3.3 -e CASSANDRA_CLUSTER_NAME=ObservationStore -e CASSANDRA_DC=JASMIN -e CASSANDRA_RACK=default -e CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch -v /mnt/cassandra:/var/lib/cassandra --net=host cassandra:3.7

docker run --name cassandra_node_three -d  -e CASSANDRA_LISTEN_ADDRESS=192.168.3.4 -e CASSANDRA_BROADCAST_ADDRESS=192.168.3.4 -e CASSANDRA_START_RPC=false -e CASSANDRA_SEEDS=192.168.3.2,192.168.3.3 -e CASSANDRA_CLUSTER_NAME=ObservationStore -e CASSANDRA_DC=JASMIN -e CASSANDRA_RACK=default -e CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch -v /mnt/cassandra:/var/lib/cassandra --net=host cassandra:3.7
```

To support internal communication the following ports need to be opened up on the internal IP addresses:
```
# 7000: intra-node communication
sudo ufw allow from 192.168.3.3 to 192.168.3.2 port 7000
sudo ufw allow from 192.168.3.4 to 192.168.3.2 port 7000

sudo ufw allow from 192.168.3.2 to 192.168.3.3 port 7000
sudo ufw allow from 192.168.3.4 to 192.168.3.3 port 7000

sudo ufw allow from 192.168.3.2 to 192.168.3.4 port 7000
sudo ufw allow from 192.168.3.3 to 192.168.3.4 port 7000

# 7001: TLS intra-node communication
sudo ufw allow from 192.168.3.3 to 192.168.3.2 port 7001
sudo ufw allow from 192.168.3.4 to 192.168.3.2 port 7001

sudo ufw allow from 192.168.3.2 to 192.168.3.3 port 7001
sudo ufw allow from 192.168.3.4 to 192.168.3.3 port 7001

sudo ufw allow from 192.168.3.2 to 192.168.3.4 port 7001
sudo ufw allow from 192.168.3.3 to 192.168.3.4 port 7001

# 7199: JMX
sudo ufw allow from 192.168.3.3 to 192.168.3.2 port 7199
sudo ufw allow from 192.168.3.4 to 192.168.3.2 port 7199

sudo ufw allow from 192.168.3.2 to 192.168.3.3 port 7199
sudo ufw allow from 192.168.3.4 to 192.168.3.3 port 7199

sudo ufw allow from 192.168.3.2 to 192.168.3.4 port 7199
sudo ufw allow from 192.168.3.3 to 192.168.3.4 port 7199

# 9042: CQL
sudo ufw allow from 192.168.3.3 to 192.168.3.2 port 9042
sudo ufw allow from 192.168.3.4 to 192.168.3.2 port 9042

sudo ufw allow from 192.168.3.2 to 192.168.3.3 port 9042
sudo ufw allow from 192.168.3.4 to 192.168.3.3 port 9042

sudo ufw allow from 192.168.3.2 to 192.168.3.4 port 9042
sudo ufw allow from 192.168.3.3 to 192.168.3.4 port 9042

```

Connections from the local machine are made using ssh:

```
ssh -L local_socket:host:hostport
ssh -L 9042:192.168.3.2:9042 casone
ssh -L 7199:192.168.3.2:7199 casone
```

As an interim measure until a reliable Flink to Cassandra Scala data sink is supported (issues [here](https://issues.apache.org/jira/browse/FLINK-4497), [here](https://issues.apache.org/jira/browse/FLINK-4177), and [here](https://issues.apache.org/jira/browse/FLINK-4498)), a Python daemon client has been created to bridge the gap between the Kafka persistence queues and the Cassandra database.  This runs on the Cassandra node to allow connection without cause for authentication to the cluster, with the code found in the `observation-processing` resources directory.

#### Kafka

The Kafka installation has both zookeeper and kafka based logs to keep track of, and those directories are added to the host as follows:

```
mkdir /mnt/kafka/kafka_logs
mkdir /mnt/kafka/zookeeper_logs
```

The container uses host networking, but still takes the advertised host and port parameters to configure internal settings correctly.

```
docker run --name kafka_node_one -d --env ADVERTISED_HOST=192.168.3.5 --env ADVERTISED_PORT=9092 -v /mnt/kafka/kafka_logs:/tmp/kafka-logs -v /mnt/kafka/zookeeper_logs:/tmp/zookeeper --net=host dbciar/docker-kafka
```

Once created, if the binaries have been downloaded to a local location, commands like the following can be used to create topics:
```
./kafka-topics.sh --create --zookeeper localhost:2181 --partitions 1 --replication-factor 1 --topic raw-observations

./kafka-topics.sh --create --zookeeper localhost:2181 --partitions 1 --replication-factor 1 --topic raw-observations-malformed

./kafka-topics.sh --create --zookeeper localhost:2181 --partitions 1 --replication-factor 1 --topic observation-persist

./kafka-topics.sh --create --zookeeper localhost:2181 --partitions 1 --replication-factor 1 --topic observation-qc-logic
```

The connections below allow the Apache Flink node, and the Apache Cassandra node to connect respectively.

```
# Apache Flink
sudo ufw allow from 192.168.3.6 to 192.168.3.5 port 2181
sudo ufw allow from 192.168.3.6 to 192.168.3.5 port 9092

# Apache Cassandra
sudo ufw allow from 192.168.3.2 to 192.168.3.5 port 2181
sudo ufw allow from 192.168.3.2 to 192.168.3.5 port 9092
```

Finally, to connect transparently from the local host, the following connections can be made:

```
ssh -L 9092:127.0.0.1:9092 kafka
ssh -L 2181:127.0.0.1:2181 kafka
```

#### Redis

The Redis container runs as is with only the --net parameter being set:
```
docker run --name redis_registry -d --net=host redis
```

To allow Apache Flink to connect the host port must be opened up:
```
sudo ufw allow from 192.168.3.6 to 192.168.3.5 port 6379
```

To connect from the local host the following is used:
```
ssh -L 6379:127.0.0.1:6379 kafka
```

For loading bulk data, the redis-mass project is used (though on Ubuntu the redis-mass.js file needs the shell changed: `#!/usr/bin/env node` to `#!/usr/bin/env nodejs`

Once this change is made, bulk loading can be performed as follows using a file, PrimerData.txt, that has a number of "SET KEY VALUE" entries.

```
./redis-mass/lib/redis-mass.js PrimerData.txt | /usr/share/redis/src/redis-cli --pipe
```

#### Flink

TODO: dockerise the local execution or cluster, depending on setup.

For viewing the GUI interface in the local browser, the following connection must be made:
```
ssh -L 8081:127.0.0.1:8081 flink
```