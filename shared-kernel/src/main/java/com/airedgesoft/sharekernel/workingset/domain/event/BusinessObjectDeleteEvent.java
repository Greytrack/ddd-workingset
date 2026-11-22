package com.airedgesoft.sharekernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;



/**
 * The business object committed a soft deletion or restoration.
 */
public record BusinessObjectDeleteEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long localId, boolean deleted) {
}
