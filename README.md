# TimestampConverter for Striim

## 1. Overview

The `TimestampConverter` is a Striim Open Processor designed to correctly handle timestamps that do not contain timezone information (e.g., from a database `TIMESTAMP` column). It gives meaning to these "wall clock" timestamps by assigning an explicit source timezone and then, optionally, converting them to a target timezone for downstream systems.

This processor is essential for ensuring all timestamps in your data pipelines are accurate, consistent, and unambiguous.

---

## 2. The Problem: Timezone-Unaware Timestamps

Many source systems store timestamps without any timezone information. For example, a value like `2023-11-01 09:00:00` is ambiguous—is it 9 AM in New York, London, or Tokyo?

Without a defined timezone, this data is unreliable for analytics, reporting, or any time-sensitive operations. This processor solves that problem by making these timestamps explicit.

**Common Sources of Timezone-Unaware Timestamps:**
- Oracle `TIMESTAMP(6)` (without `WITH TIME ZONE`)
- MySQL `DATETIME` columns
- PostgreSQL `TIMESTAMP WITHOUT TIME ZONE`
- CSV files with timestamp columns
- Legacy systems that don't track timezone information

---

## 3. How It Works: The Two-Step Process

The processor intelligently applies a two-step logic to every timestamp field it finds in an event's data.

### Step 1: Assume a Source Timezone
The processor takes the original "wall clock" timestamp and **assumes** it belongs to the timezone specified in the `SourceTimeZoneID` property. This establishes the correct, unambiguous instant in time.

*   **If `SourceTimeZoneID` is specified:** It uses that timezone (e.g., `America/New_York`).
*   **If `SourceTimeZoneID` is left blank:** It defaults to the system timezone of the Striim server where it is running.

### Step 2: Convert to a Target Timezone
After establishing the correct instant, the processor then **converts** it to the timezone specified in the `TargetTimeZoneID` property. This prepares the data for the target system.

*   **If `TargetTimeZoneID` is specified:** It converts the timestamp to that zone (e.g., `UTC`).
*   **If `TargetTimeZoneID` is left blank:** It defaults to the `SourceTimeZoneID`. This effectively turns the processor into a **"Timezone Assigner,"** which is perfect for simply making the source data timezone-aware without further conversion.

---

## 4. Configuration Properties

The processor is configured using the following properties in your TQL application.

| Property Name | Type | Required | Description | Default Behavior |
| :--- | :--- | :--- | :--- | :--- |
| `SourceTimeZoneID` | String | No | The timezone to **assume** for the source's timezone-unaware timestamps. Use IANA timezone IDs (e.g., `America/New_York`, `Asia/Kolkata`, `UTC`). | If empty, uses the Striim server's system default timezone. |
| `TargetTimeZoneID` | String | No | The final timezone for the output data. Use IANA timezone IDs. | If empty, defaults to the resolved `SourceTimeZoneID`. |
| `EnableLogging` | Boolean | No | Set to `true` to enable detailed debug logging in the Striim node logs. Useful for troubleshooting. | `false` |

### Important Notes on Timezone IDs:
- **Use IANA timezone identifiers** (e.g., `America/Chicago`, `Asia/Kolkata`, `Europe/London`, `UTC`)
- **Avoid ambiguous abbreviations** like `IST` (could mean India, Israel, or Irish Standard Time)
- **Common timezone IDs:**
  - UTC: `UTC`
  - US Eastern: `America/New_York`
  - US Central: `America/Chicago`
  - US Pacific: `America/Los_Angeles`
  - India: `Asia/Kolkata`
  - Israel: `Asia/Jerusalem`
  - UK: `Europe/London`

---

## 5. Usage Scenarios (TQL Examples)

### Scenario 1: Assign a Timezone (No Conversion)
**Goal:** Your source timestamps are from servers in New York. You want to make them timezone-aware before writing to Kafka, without converting them to a different zone.

**TQL:**
```sql
CREATE OPEN PROCESSOR AssignTimezone USING Global.TimestampConverter (
  SourceTimeZoneID: 'America/New_York',
  TargetTimeZoneID: '',  -- Empty means no conversion
  EnableLogging: false
)
INSERT INTO KafkaOutputStream
SELECT * FROM DatabaseSource;
```

**Result:** Timestamps remain as New York time but are now explicitly marked as `America/New_York`.

---

### Scenario 2: Convert from One Timezone to Another
**Goal:** Your Oracle database stores timestamps in India Standard Time (IST), but your analytics platform expects UTC.

**TQL:**
```sql
CREATE OPEN PROCESSOR ConvertToUTC USING Global.TimestampConverter (
  SourceTimeZoneID: 'Asia/Kolkata',  -- Source is IST
  TargetTimeZoneID: 'UTC',            -- Convert to UTC
  EnableLogging: false
)
INSERT INTO AnalyticsTarget
SELECT * FROM OracleSource;
```

**Result:** A timestamp like `2023-11-01 14:30:00` (IST) becomes `2023-11-01 09:00:00` (UTC).

---

### Scenario 3: Debug Timezone Conversion
**Goal:** You're not sure if the conversion is working correctly and want to see detailed logs.

**TQL:**
```sql
CREATE OPEN PROCESSOR DebugConversion USING Global.TimestampConverter (
  SourceTimeZoneID: 'America/Chicago',
  TargetTimeZoneID: 'UTC',
  EnableLogging: true  -- Enable detailed logging
)
INSERT INTO OutputStream
SELECT * FROM InputStream;
```

**Check Logs:** Look in your Striim node logs for entries like:
```
TimestampConverter initialized. Assuming source timezone: America/Chicago, Converting to target timezone: UTC
Converted timestamp at index 2: [Original: 2023-11-01T10:30:00.000-06:00] 
  -> [Assumed in America/Chicago: 2023-11-01T10:30:00.000-06:00] 
  -> [Converted to UTC: 2023-11-01T16:30:00.000Z]
```

---

### Scenario 4: Use Server's Default Timezone
**Goal:** Your Striim server runs in the same timezone as your source data, and you want to convert to UTC.

**TQL:**
```sql
CREATE OPEN PROCESSOR UseServerTimezone USING Global.TimestampConverter (
  SourceTimeZoneID: '',  -- Use Striim server's timezone
  TargetTimeZoneID: 'UTC',
  EnableLogging: false
)
INSERT INTO UTCOutputStream
SELECT * FROM LocalSource;
```

---

## 6. Installation

### Step 1: Download the SCM File

Download the latest `TimestampConverter.scm` file from the [Releases](https://github.com/pazitbzimerman/TimestampConverter/releases) page.

### Step 2: Deploy to Striim

1. Copy the downloaded `.scm` file to your Striim installation's `models` directory:
   ```bash
   cp TimestampConverter.scm /path/to/Striim/models/
   ```

2. Restart Striim according to the [Striim documentation](https://www.striim.com/docs/platform/en/starting-and-stopping-striim-platform.html)

### Step 3: Verify Installation

1. Create a new app in the Striim UI
2. Drag an OP (Open Processor) component onto the canvas
3. Look for `TimestampConverter` in the adapter list

---

## 7. Technical Details

### How It Processes Events
The processor automatically:
- Scans all fields in each event's `data` array
- Scans all fields in each event's `before` array (for CDC operations)
- Identifies timestamp fields of the following types:
  - `org.joda.time.DateTime` (Striim's default timestamp type)
  - `java.time.LocalDateTime` (Java 8+ timezone-unaware timestamps)
  - `java.time.ZonedDateTime` (Java 8+ timezone-aware timestamps)
  - `java.sql.Timestamp` (JDBC timestamp type)
- Applies the two-step timezone conversion to each timestamp field
- Converts all timestamp types to `org.joda.time.DateTime` for consistency
- Passes through all other data types unchanged

### Error Handling
- **Invalid Timezone IDs:** If an invalid timezone ID is provided, the processor logs an error and falls back to safe defaults:
  - Invalid `SourceTimeZoneID` → Falls back to Striim server's system timezone
  - Invalid `TargetTimeZoneID` → Falls back to the resolved `SourceTimeZoneID`
- **Event Processing Errors:** If an error occurs while processing an event, it logs the error and skips that event (does not crash the pipeline)

### Performance Considerations
- The processor is lightweight and adds minimal latency
- Timezone conversions are performed using Joda-Time's optimized algorithms
- No external dependencies are required at runtime (all dependencies are provided by Striim)

---

## 8. Troubleshooting

### Problem: "Open Processor class not found"
**Cause:** The module is not properly imported into Striim.

**Solution:**
1. Verify the `.scm` file is in the `models` directory
2. Restart Striim
3. Run `SHOW MODULES;` to confirm the module is loaded
4. Use `Global.TimestampConverter` (not `com.striim.field.TimestampConverter`) in your TQL

### Problem: Timestamps are not being converted
**Cause:** The timezone IDs might be invalid or the processor is not receiving supported timestamp objects.

**Solution:**
1. Enable logging: `EnableLogging: true`
2. Check Striim logs for error messages
3. Verify you're using IANA timezone IDs (e.g., `America/New_York`, not `EST`)
4. Verify the source data contains supported timestamp types (use `DESCRIBE TYPE` in TQL):
   - `org.joda.time.DateTime`
   - `java.time.LocalDateTime`
   - `java.time.ZonedDateTime`
   - `java.sql.Timestamp`

### Problem: Timestamps show in server's local timezone after conversion
**Cause:** Some Striim targets (like File Writer) may convert DateTime objects back to the server's local timezone when writing.

**Solution:**
1. Use a target that preserves timezone information (e.g., database with `TIMESTAMP WITH TIME ZONE`)
2. Add a CQ to format timestamps as strings before writing:
   ```sql
   CREATE CQ FormatTimestamps
   INSERT INTO FileTarget
   SELECT
     EVENT_ID,
     TO_STRING(CREATED_AT, 'yyyy-MM-dd HH:mm:ss Z') as CREATED_AT_STR
   FROM AfterConversion;
   ```

### Problem: Build fails with "cannot find symbol" errors
**Cause:** The Striim library paths in `pom.xml` are incorrect.

**Solution:**
1. Verify `STRIIMBUILDPATH` points to your Striim installation directory
2. Verify the Striim version matches your installation
3. Check that `Platform-${STRIIM_VERSION}.jar` and `Common-${STRIIM_VERSION}.jar` exist in `${STRIIMBUILDPATH}/lib/`

---

## 9. Example: Complete Pipeline

Here's a complete example of using TimestampConverter in a CDC pipeline from Oracle to PostgreSQL:

```sql
-- Create Oracle CDC source
CREATE SOURCE OracleSource USING OracleReader (
  ConnectionURL: 'jdbc:oracle:thin:@localhost:1521:ORCL',
  Username: 'striim',
  Password: 'striim',
  Tables: 'MYSCHEMA.EVENT_LOGS'
)
OUTPUT TO OracleStream;

-- Convert timestamps from IST to UTC
CREATE OPEN PROCESSOR ConvertTimezones USING Global.TimestampConverter (
  SourceTimeZoneID: 'Asia/Kolkata',  -- Oracle DB is in India
  TargetTimeZoneID: 'UTC',            -- PostgreSQL expects UTC
  EnableLogging: false
)
INSERT INTO ConvertedStream
SELECT * FROM OracleStream;

-- Write to PostgreSQL
CREATE TARGET PostgresTarget USING DatabaseWriter (
  ConnectionURL: 'jdbc:postgresql://localhost:5432/mydb',
  Username: 'postgres',
  Password: 'postgres',
  Tables: 'public.EVENT_LOGS'
)
INPUT FROM ConvertedStream;
```

---

## 10. License and Support

**Version:** 5.2.0
**Author:** Striim Field Engineering
**Package:** `com.striim.field.TimestampConverter`

For questions or issues, please contact your Striim support representative.

---

## 11. Changelog

### Version 5.2.0 (2026-03-19)
- Initial release
- Support for multiple timestamp types:
  - `org.joda.time.DateTime` (Striim default)
  - `java.time.LocalDateTime` (Java 8+ timezone-unaware)
  - `java.time.ZonedDateTime` (Java 8+ timezone-aware)
  - `java.sql.Timestamp` (JDBC timestamps)
- Configurable source and target timezones using IANA timezone IDs
- Debug logging capability for troubleshooting
- Automatic processing of both `data` and `before` arrays for CDC support
- All timestamp types converted to `org.joda.time.DateTime` for consistency