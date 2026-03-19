// filename: TimestampConverter.java
package com.striim.field;

import com.webaction.anno.AdapterType;
import com.webaction.anno.PropertyTemplate;
import com.webaction.anno.PropertyTemplateProperty;
import com.webaction.runtime.BuiltInFunc;
import com.webaction.runtime.components.openprocessor.StriimOpenProcessor;
import com.webaction.runtime.containers.IBatch;
import com.webaction.runtime.containers.WAEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Map;
import java.util.Objects;

@PropertyTemplate(
        name = "TimestampConverter",
        type = AdapterType.process,
        properties = {
                @PropertyTemplateProperty(
                        name = "SourceTimeZoneID",
                        type = String.class,
                        required = false,
                        defaultValue = "",
                        label = "Source Timezone ID",
                        description = "The timezone to assume for the source's timezone-unaware timestamps. If empty, Striim server's default is used."
                ),
                @PropertyTemplateProperty(
                        name = "TargetTimeZoneID",
                        type = String.class,
                        required = false,
                        defaultValue = "",
                        label = "Target Timezone ID",
                        description = "The final timezone for the output data. If empty, defaults to the Source Timezone."
                ),
                @PropertyTemplateProperty(
                        name = "EnableLogging",
                        type = Boolean.class,
                        required = false,
                        defaultValue = "false"
                )
        },
        inputType = com.webaction.proc.events.WAEvent.class,
        outputType = com.webaction.proc.events.WAEvent.class
)
public class TimestampConverter extends StriimOpenProcessor {

    private static final Logger logger = LogManager.getLogger(TimestampConverter.class);
    private DateTimeZone sourceZone;
    private DateTimeZone targetZone;
    private boolean enableLogging = false;

    @Override
    public void start() throws Exception {
        super.start();
        Map<String, Object> props = getProperties();

        this.enableLogging = Boolean.parseBoolean(Objects.toString(props.get("EnableLogging"), "false"));
        String sourceZoneId = Objects.toString(props.get("SourceTimeZoneID"), "");
        String targetZoneId = Objects.toString(props.get("TargetTimeZoneID"), "");

        // 1. Determine the source timezone to assume
        try {
            this.sourceZone = sourceZoneId.isEmpty() ? DateTimeZone.getDefault() : DateTimeZone.forID(sourceZoneId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid SourceTimeZoneID: " + sourceZoneId + ". Falling back to system default.", e);
            this.sourceZone = DateTimeZone.getDefault();
        }

        // 2. Determine the target timezone for conversion
        try {
            this.targetZone = targetZoneId.isEmpty() ? this.sourceZone : DateTimeZone.forID(targetZoneId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid TargetTimeZoneID: " + targetZoneId + ". Falling back to source timezone.", e);
            this.targetZone = this.sourceZone;
        }

        if (enableLogging) {
            logger.info("TimestampConverter initialized. Assuming source timezone: " + this.sourceZone.getID() +
                    ", Converting to target timezone: " + this.targetZone.getID());
        }
    }

    @Override
    public void run() {
        for (WAEvent event : getAdded()) {
            try {
                com.webaction.proc.events.WAEvent waevent = (com.webaction.proc.events.WAEvent) event.data;
                processDataArray(waevent, waevent.data);
                processDataArray(waevent, waevent.before);
                send(waevent);
            } catch (Exception e) {
                logger.error("TimestampConverter: Error processing event. Skipping.", e);
            }
        }
    }

    private void processDataArray(com.webaction.proc.events.WAEvent waevent, Object[] arr) {
        if (arr == null) {
            return;
        }

        for (int i = 0; i < arr.length; i++) {
            if (BuiltInFunc.IS_PRESENT(waevent, arr, i) && arr[i] instanceof DateTime) {
                DateTime originalDateTime = (DateTime) arr[i];

                // STEP 1: Assume the source timezone. This re-interprets the "wall clock" time
                // in the specified zone, establishing the correct instant.
                DateTime assumedDateTime = originalDateTime.withZoneRetainFields(this.sourceZone);

                // STEP 2: Convert the now-correct instant to the target timezone.
                DateTime convertedDateTime = assumedDateTime.withZone(this.targetZone);

                arr[i] = convertedDateTime;

                if (enableLogging) {
                    logger.debug("Converted timestamp at index " + i + ": [Original: " + originalDateTime +
                            "] -> [Assumed in " + sourceZone.getID() + ": " + assumedDateTime +
                            "] -> [Converted to " + targetZone.getID() + ": " + convertedDateTime + "]");
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
    }

    @Override
    public Map getAggVec() {
        return null;
    }

    @Override
    public void setAggVec(Map aggVec) {
        // No-op
    }
}