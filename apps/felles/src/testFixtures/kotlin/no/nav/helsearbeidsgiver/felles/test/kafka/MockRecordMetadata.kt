package no.nav.helsearbeidsgiver.felles.test.kafka

import org.apache.kafka.clients.producer.RecordMetadata

fun mockRecordMetadata(): RecordMetadata = RecordMetadata(null, 0, 0, 0, 0, 0)
