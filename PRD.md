# Product Requirements Document (PRD)

## Requirements

| ID      | Description                                                                                                                                                                                                                                                         | Status            |
|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| REQ-001 | Display track statistics (distance, elevation gain/loss, duration, avg speed)                                                                                                                                                                                       | ❌ Not Implemented |
| REQ-002 | Support multi-track comparison on single map with different colors                                                                                                                                                                                                  | ❌ Not Implemented |
| REQ-003 | Add database integration for metadata persistence                                                                                                                                                                                                                   | ❌ Not Implemented |
| REQ-004 | Implement user authentication and file ownership                                                                                                                                                                                                                    | ❌ Not Implemented |
| REQ-005 | Add track editing capabilities (crop, merge segments)                                                                                                                                                                                                               | ❌ Not Implemented |
| REQ-006 | Export tracks to different formats (KML, GeoJSON)                                                                                                                                                                                                                   | ❌ Not Implemented |
| REQ-007 | Add search and filter functionality for stored tracks                                                                                                                                                                                                               | ❌ Not Implemented |
| REQ-008 | Store gpx metadata in postgres database (id, filename, checksum of file). Use flyway to track db changes since this is just the start                                                                                                                               | ✅ Implemented     |
| REQ-009 | Add an optional tour description freetext field to the upload screen and the database                                                                                                                                                                               | ✅ Implemented     |
| REQ-010 | Add a mandatory name field to the database. it should be derived from the from the gpx metadata. if not present, use the file basename without extension. update existing records with the value of the original_filename column so we can make the field mandatory | ✅ Implemented     |
| REQ-011 | Add Native Image support                                                                                                                                                                                                                                            | ✅ Implemented     |
---

## Usage

When requesting implementation:
- Reference by ID: "Implement REQ-001"
- Status will be updated to: ✅ Implemented (with commit reference)

## Status Legend
- ❌ Not Implemented
- ✅ Implemented
