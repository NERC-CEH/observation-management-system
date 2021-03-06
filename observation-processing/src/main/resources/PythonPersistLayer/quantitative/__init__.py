#!/usr/bin/env python

import logging
import os
import sys

from KafkaQCQuantitativePersist import KafkaQCQuantitativePersist

if __name__ == '__main__':

    action = sys.argv[1]

    # Store all output in one log file
    logfile = os.path.join(os.getcwd(), "dataloader.log")
    logging.basicConfig(filename=logfile, level=logging.WARNING)

    # Create a PID file for each of the daemon listeners
    observationPidFile = os.path.join(os.getcwd(), "dataloader.pid")

    obs = KafkaQCQuantitativePersist(pidfile=observationPidFile)

    if action == "start":
        obs.start()

    elif action == "stop":
        obs.stop()

    elif action == "restart":
        obs.restart()
