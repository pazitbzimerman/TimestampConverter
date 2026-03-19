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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    // Joda-Time timezone objects (for org.joda.time.DateTime)
    private DateTimeZone sourceZone;
    private DateTimeZone targetZone;

    // java.time timezone objects (for java.time.LocalDateTime, ZonedDateTime, etc.)
    private ZoneId sourceZoneId;
    private ZoneId targetZoneId;

    private boolean enableLogging = false;

    @Override
    public void start() throws Exception {
        super.start();
        Map<String, Object> props = getProperties();

        this.enableLogging = Boolean.parseBoolean(Objects.toString(props.get("EnableLogging"), "false"));
        String sourceZoneId = Objects.toString(props.get("SourceTimeZoneID"), "");
        String targetZoneId = Objects.toString(props.get("TargetTimeZoneID"), "");

        // 1. Determine the source timezone to assume (Joda-Time)
        try {
            this.sourceZone = sourceZoneId.isEmpty() ? DateTimeZone.getDefault() : DateTimeZone.forID(sourceZoneId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid SourceTimeZoneID: " + sourceZoneId + ". Falling back to system default.", e);
            this.sourceZone = DateTimeZone.getDefault();
        }

        // 2. Determine the target timezone for conversion (Joda-Time)
        try {
            this.targetZone = targetZoneId.isEmpty() ? this.sourceZone : DateTimeZone.forID(targetZoneId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid TargetTimeZoneID: " + targetZoneId + ". Falling back to source timezone.", e);
            this.targetZone = this.sourceZone;
        }

        // 3. Initialize java.time ZoneId objects (for java.time.LocalDateTime support)
        try {
            this.sourceZoneId = sourceZoneId.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(sourceZoneId);
        } catch (Exception e) {
            logger.error("Invalid SourceTimeZoneID for java.time: " + sourceZoneId + ". Falling back to system default.", e);
            this.sourceZoneId = ZoneId.systemDefault();
        }

        try {
            this.targetZoneId = targetZoneId.isEmpty() ? this.sourceZoneId : ZoneId.of(targetZoneId);
        } catch (Exception e) {
            logger.error("Invalid TargetTimeZoneID for java.time: " + targetZoneId + ". Falling back to source timezone.", e);
            this.targetZoneId = this.sourceZoneId;
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
            if (!BuiltInFunc.IS_PRESENT(waevent, arr, i) || arr[i] == null) {
                continue;
            }

            Object original = arr[i];
            Object converted = null;

            // Handle org.joda.time.DateTime
            if (original instanceof DateTime) {
                converted = convertJodaDateTime((DateTime) original);
            }
            // Handle java.time.LocalDateTime
            else if (original instanceof LocalDateTime) {
                converted = convertLocalDateTime((LocalDateTime) original);
            }
            // Handle java.time.ZonedDateTime
            else if (original instanceof ZonedDateTime) {
                converted = convertZonedDateTime((ZonedDateTime) original);
            }
            // Handle java.sql.Timestamp
            else if (original instanceof Timestamp) {
                converted = convertSqlTimestamp((Timestamp) original);
            }

            // Replace the value if conversion occurred
            if (converted != null) {
                arr[i] = converted;
            }
        }
    }

    /**
     * Convert org.joda.time.DateTime
     */
    private DateTime convertJodaDateTime(DateTime originalDateTime) {
        // STEP 1: Assume the source timezone. This re-interprets the "wall clock" time
        // in the specified zone, establishing the correct instant.
        DateTime assumedDateTime = originalDateTime.withZoneRetainFields(this.sourceZone);

        // STEP 2: Convert the now-correct instant to the target timezone.
        DateTime convertedDateTime = assumedDateTime.withZone(this.targetZone);

        if (enableLogging) {
            logger.debug("Converted Joda DateTime: [Original: " + originalDateTime +
                    "] -> [Assumed in " + sourceZone.getID() + ": " + assumedDateTime +
                    "] -> [Converted to " + targetZone.getID() + ": " + convertedDateTime + "]");
        }

        return convertedDateTime;
    }

    /**
     * Convert java.time.LocalDateTime to org.joda.time.DateTime
     */
    private DateTime convertLocalDateTime(LocalDateTime localDateTime) {
        // STEP 1: Assume the source timezone for the LocalDateTime
        ZonedDateTime assumedZonedDateTime = localDateTime.atZone(this.sourceZoneId);

        // STEP 2: Convert to target timezone
        ZonedDateTime convertedZonedDateTime = assumedZonedDateTime.withZoneSameInstant(this.targetZoneId);

        // STEP 3: Convert to Joda DateTime for consistency with Striim
        DateTime result = new DateTime(
                convertedZonedDateTime.toInstant().toEpochMilli(),
                DateTimeZone.forID(this.targetZoneId.getId())
        );

        if (enableLogging) {
            logger.debug("Converted LocalDateTime: [Original: " + localDateTime +
                    "] -> [Assumed in " + sourceZoneId + ": " + assumedZonedDateTime +
                    "] -> [Converted to " + targetZoneId + ": " + convertedZonedDateTime +
                    "] -> [Joda DateTime: " + result + "]");
        }

        return result;
    }

    /**
     * Convert java.time.ZonedDateTime to org.joda.time.DateTime
     */
    private DateTime convertZonedDateTime(ZonedDateTime zonedDateTime) {
        // ZonedDateTime already has timezone info, so just convert to target timezone
        ZonedDateTime convertedZonedDateTime = zonedDateTime.withZoneSameInstant(this.targetZoneId);

        // Convert to Joda DateTime
        DateTime result = new DateTime(
                convertedZonedDateTime.toInstant().toEpochMilli(),
                DateTimeZone.forID(this.targetZoneId.getId())
        );

        if (enableLogging) {
            logger.debug("Converted ZonedDateTime: [Original: " + zonedDateTime +
                    "] -> [Converted to " + targetZoneId + ": " + convertedZonedDateTime +
                    "] -> [Joda DateTime: " + result + "]");
        }

        return result;
    }

    /**
     * Convert java.sql.Timestamp to org.joda.time.DateTime
     */
    private DateTime convertSqlTimestamp(Timestamp timestamp) {
        // Convert Timestamp to LocalDateTime, then process like LocalDateTime
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return convertLocalDateTime(localDateTime);
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