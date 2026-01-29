package no.nav.hag.simba.utils.kafka.test

import org.apache.kafka.clients.producer.RecordMetadata

fun mockRecordMetadata(): RecordMetadata = RecordMetadata(null, 0, 0, 0, 0, 0)
