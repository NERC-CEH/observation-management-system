# Infrastructure

## Introduction

All the software currently used is designed to support multi-machine deployment through clustering.  The only component setup in this way for this project is Cassandra.  Kafka, Redis, and Flink are setup as single node clusters at present.

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
sudo mkdir /cassandra
# For Kafka
sudo mkdir /kafka
# For Redis
sudo mkdir /redis

# To retrieve the UUID for fstab
sudo -i blkid
  
# In fstab added the following
UUID=output_of_above_goes_here /cassandra xfs  defaults,noatime,nobootwait  1   2
```


#### Securing Ports

By default on the JASMIN hosted machines, ufw is enabled, and allows OpenSSH connections only on port 22.  For internal communication between nodes it is necessary to open up the respective ports each application requires.  As the IP addresses assigned to the nodes are static, this is a feasible approach.

```
# For connections from CEH development group
sudo ufw allow from CEH_GROUP_IP to EXTERNAL_IP port REQUIRED_NUMBER
# For connections between internal nodes
sudo ufw allow from INTERNAL_IP to INTERNAL_IP port REQUIRED_NUMBER
```

##### Cassandra

For Cassandra to support internal communication the following ports need to be opened up on the internal IP addresses:
```
# 7000: intra-node communication
sudo ufw allow from 192.168.3.3 to 192.168.3.2 port 7000
sudo ufw allow from 192.168.3.4 to 192.168.3.2 port 7000

sudo ufw allow from 192.168.3.2 to 192.168.3.3 port 7000
sudo ufw allow from 192.168.3.4 to 192.168.3.3 port 7000

sudo ufw allow from 192.168.3.2 to 192.168.3.4 port 7000
sudo ufw allow from 192.168.3.3 to 192.168.3.4 port 7000

# 7001: TLS intra-node communication
# NA

# 7199: JMX
# NA

# 9042: CQL
sudo ufw allow from 192.168.3.3 to 192.168.3.2 port 9042
sudo ufw allow from 192.168.3.4 to 192.168.3.2 port 9042

sudo ufw allow from 192.168.3.2 to 192.168.3.3 port 9042
sudo ufw allow from 192.168.3.4 to 192.168.3.3 port 9042

sudo ufw allow from 192.168.3.2 to 192.168.3.4 port 9042
sudo ufw allow from 192.168.3.3 to 192.168.3.4 port 9042

```

While the following external port is to be opened up to allow connection from 
```
# 9042: CQL - this is used rather than the older 9160 (thrift service)
# As this is an external IP, it is necessary to protect it, in this instance
#  using a username and password combination
sudo ufw allow from CEH_GROUP_IP to EXTERNAL_IP port REQUIRED_PORT
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

sudo apt-get install docker.io
```
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

```
docker run --name cassandra_node_one -d  -e CASSANDRA_LISTEN_ADDRESS=192.168.3.2 -e CASSANDRA_START_RPC=false -e CASSANDRA_SEEDS=192.168.3.2,192.168.3.3 -e CASSANDRA_CLUSTER_NAME=ObservationStore -e CASSANDRA_DC=JASMIN -e CASSANDRA_RACK=default -e CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch -p 7000:7000 -p 9042:9042 -v /cassandra:/var/lib/cassandra --net=host cassandra:3.7

docker run --name cassandra_node_two -d  -e CASSANDRA_LISTEN_ADDRESS=192.168.3.3 -e CASSANDRA_START_RPC=false -e CASSANDRA_SEEDS=192.168.3.2,192.168.3.3 -e CASSANDRA_CLUSTER_NAME=ObservationStore -e CASSANDRA_DC=JASMIN -e CASSANDRA_RACK=default -e CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch -p 7000:7000 -p 9042:9042 -v /cassandra:/var/lib/cassandra --net=host cassandra:3.7

docker run --name cassandra_node_three -d  -e CASSANDRA_LISTEN_ADDRESS=192.168.3.4 -e CASSANDRA_START_RPC=false -e CASSANDRA_SEEDS=192.168.3.2,192.168.3.3 -e CASSANDRA_CLUSTER_NAME=ObservationStore -e CASSANDRA_DC=JASMIN -e CASSANDRA_RACK=default -e CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch -p 7000:7000 -p 9042:9042 -v /cassandra:/var/lib/cassandra --net=host cassandra:3.7


docker run -it --link cassandra_node_one:cassandra --rm cassandra sh -c 'exec cqlsh "$CASSANDRA_PORT_9042_TCP_ADDR"'

```

#### Kafka

(The Docker file has been changed for the version?)

docker run --name kafka_ukleon -d -p 2181:2181 -p 9092:9092 --env ADVERTISED_HOST=192.171.183.108 --env ADVERTISED_PORT=9092 spotkaf

#### Redis

docker run --name redis_registry -d -p 6379:6379 redis 

#### Flink
