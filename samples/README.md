# TimestampConverter Sample Applications

This directory contains sample TQL applications demonstrating how to use the TimestampConverter processor in various scenarios.

## Samples

### 1. OracleToOracle.tql

**Description:** Reads data from an Oracle database and converts timestamps to Asia/Kolkata timezone before writing to a JSON file.

**Key Features:**
- Uses `DatabaseReader` with `ReturnDateTimeAs: 'JODA'` to get Joda DateTime objects
- Applies `TimestampConverter` to convert timestamps to Asia/Kolkata timezone
- Outputs to JSON file with formatted timestamps
- Enables logging for debugging

**Configuration:**
- **Source:** Oracle database (PAZIT.event_logs table)
- **Target Timezone:** Asia/Kolkata (IST)
- **Output:** JSON files in `OF_18_3_2026` directory

**Usage:**
1. Update the database connection details (Username, Password, ConnectionURL, Tables)
2. Import the TQL into Striim
3. Deploy and start the application
4. Monitor logs to see timestamp conversions

**Notes:**
- The `SourceTimeZoneID` is not specified, so it defaults to the server's timezone
- If your source timestamps are in a specific timezone, add `SourceTimeZoneID: 'Your/Timezone'`
- Enable logging with `EnableLogging: true` to see conversion details in Striim logs

## How to Use These Samples

1. **Import the module:**
   - Copy `TimestampConverter.scm` to your Striim `modules` directory
   - Restart Striim or import via the UI

2. **Customize the TQL:**
   - Update connection details for your environment
   - Modify timezone settings as needed
   - Adjust output paths and formats

3. **Deploy:**
   - Import the TQL file into Striim UI
   - Deploy and start the application
   - Monitor the output to verify timestamp conversion

## Common Timezone IDs

- **US Timezones:** `America/New_York`, `America/Chicago`, `America/Denver`, `America/Los_Angeles`
- **European Timezones:** `Europe/London`, `Europe/Paris`, `Europe/Berlin`
- **Asian Timezones:** `Asia/Kolkata`, `Asia/Tokyo`, `Asia/Shanghai`, `Asia/Dubai`
- **Australian Timezones:** `Australia/Sydney`, `Australia/Melbourne`

For a complete list, see: https://en.wikipedia.org/wiki/List_of_tz_database_time_zones

