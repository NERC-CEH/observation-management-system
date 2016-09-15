from daemons.prefab import step
from kafka import KafkaConsumer
from cassandra.cluster import Cluster
import string


class KafkaObservationPersist(step.StepDaemon):
    def step(self):

        # Connect to Cassandra
        cluster = Cluster(['192.168.3.2'],
                          port= 9042)

        session = cluster.connect()

        # Link to kafka
        consumer = KafkaConsumer('observation-persist',
                                 bootstrap_servers="192.168.3.5:9092")


        # Process observations
        for msg in consumer:
            split_msg = string.split(msg.value,"::")

            if(len(split_msg) == 16)    :

                session.execute(
                    """
                    INSERT INTO observation.observations_numeric (feature, procedure, observableproperty,
                    year, month, phenomenontimestart, phenomenontimeend, value, quality, accuracy, status,
                    processing, uncertml, comment, location, parameters)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (split_msg[0],split_msg[1],split_msg[2],int(split_msg[3]),int(split_msg[4]),int(split_msg[5]),int(split_msg[6]),
                     float(split_msg[7]),split_msg[8],float(split_msg[9]),split_msg[10],split_msg[11],split_msg[12],
                     split_msg[13],split_msg[14],split_msg[15])
                )

        # Close link to kafka
        consumer.close()
        cluster.shutdown()


