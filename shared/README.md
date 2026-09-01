# Shared cross-platform artifacts

This directory contains portable contracts and fixtures, not application code.
Android, web, and future iOS keep independent implementations and consume these
artifacts to verify equivalent product outcomes.

- `fixtures/`: named inputs and expected outputs for product rules.

Do not add UI components, platform services, or a shared runtime here. Add an
artifact only after at least two platform implementations need the same
contract or behavior.
