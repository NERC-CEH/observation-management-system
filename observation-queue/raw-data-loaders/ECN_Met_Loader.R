# Load Libraries
# =============================================================================
library(lubridate)
library(reshape2)
library(rkafka)

# Data Loading
# =============================================================================
setwd('N:/Temporary_ECN')

to_process <- list.files(path = '.',
                         full.names = TRUE,
                         include.dirs = FALSE,
                         ignore.case = TRUE,
                         pattern = '(dat)')

for(curr_file in to_process){

  # Site specific logger meta-data
  moorMeta <- read.table(curr_file,
                         sep = ',',
                         nrow = 1,
                         header = FALSE)
  
  # Header with three rows relating to: observed phenomena, measurement unit, measurement type
  moorHeader <- read.table(curr_file,
                           sep = ',',
                           skip = 1,
                           nrow = 3,
                           header = FALSE)
  
  # Observation data
  moorData <- read.table(curr_file,
                         sep = ',',
                         skip = 4,
                         header = FALSE)  

  
  # Data Formatting
  # =============================================================================
  
  # Create meta-data headers
  colnames(moorMeta) <- c('sitecode',
                          'site_logger',
                          'logger_code',
                          'unk1',
                          'unk2',
                          'unk3',
                          'unk4',
                          'unk5')
  
  # Create observation data headers, add site information, reshape to long format, ready
  #  for adding to Kafka queue
  colnames(moorData) <- as.vector(t(moorHeader[1,]))
  moorData$SITE = moorMeta$sitecode
  moorDataLong = melt(moorData, id = c('TIMESTAMP','SITE'))
  
  # Send to queue and move file to processed directory
  # =============================================================================
  
  # https://cran.r-project.org/web/packages/rkafka/vignettes/rkafka.pdf
  queue_label = 'raw_sensor_queue'
  broker_ip_port = '127.0.0.1:8080'
  ecn_producer = rkafka.createProducer(broker_ip_port)
  
  for(i in 1:dim(moorDataLong)[1]){
    rkafka.send(ecn_producer,
                queue_label,
                broker_ip_port,
                str_c(moorDataLong$SITE[i],
                      moorDataLong$variable[i],
                      moorDataLong$TIMESTAMP[i],
                      moorDataLong$value[i],
                      sep = ','))  
  }
  
  file.copy(curr_file, str_c('./processed/',curr_file))
  file.remove(curr_file)

  # Close the Kafka connection
  rkafka.closeProducer(ecn_producer)
}






