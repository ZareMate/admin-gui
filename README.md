# Admin GUI

NeoForge 1.21.1 / 21.1.249 administration dashboard.

Open it with:

    /adm-gui
    /admin-gui

Requires operator permission level 3.

## Dashboard

The left side contains a searchable list of known online and offline players. Selecting a player shows:

- UUID and online status
- TSA Anticheat packet checks and stored detections
- Airport Security System offense/check history
- Admin Notes with add/edit/remove controls
- FTB Teams information when available
- Discord Link information when available
- ClockIn information when available

## Optional integrations

All integrations are discovered at runtime and are optional:

- https://github.com/ZareMate/tsa-anticheat
- https://github.com/ZareMate/airport-security-system
- https://github.com/ZareMate/admin-notes
- https://github.com/ZareMate/ftb-teams-util
- https://github.com/ZareMate/discord-link
- https://github.com/ZareMate/clockin

The GUI does not duplicate their databases. It reads their public APIs where available; ClockIn is read through its existing server-side data model because that project currently does not expose a public API.

Admin Notes changes are made through AdminNotesAPI, so normal Admin Notes persistence and note IDs remain authoritative.

## Build

Java 21:

    gradle clean build

The jar is generated in build/libs.
