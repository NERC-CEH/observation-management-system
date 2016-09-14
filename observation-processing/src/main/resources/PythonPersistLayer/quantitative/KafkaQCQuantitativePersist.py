from daemons.prefab import step
from kafka import KafkaConsumer
from cassandra.cluster import Cluster
import string


class KafkaQCQuantitativePersist(step.StepDaemon):
    def step(self):

        # Connect to Cassandra
        cluster = Cluster(['192.168.3.2'],
                          port= 9042)

        session = cluster.connect()

        # Link to kafka
        consumer = KafkaConsumer('qc-quantitative-persist',
                                 bootstrap_servers="192.168.3.5:9092")


        # Process observations
        for msg in consumer:
            split_msg = string.split(msg.value,"::")

            if(len(split_msg) == 9):

                session.execute(
                    """
                    INSERT INTO observation.observations_qc_quantitative (feature, procedure, observableproperty,
                    year, month, phenomenontimestart, qualifier, qualifiervalue, comment)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (split_msg[0], split_msg[1], split_msg[2], int(split_msg[3]), int(split_msg[4]),
                     int(split_msg[5]), split_msg[6], float(split_msg[7]), split_msg[8])
                )

        # Close link to kafka
        consumer.close()
        cluster.shutdown()
