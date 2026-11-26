# Module test-cookbook-server

A Cookbook server in memory for tests, served through `MockWebServer`. It behaves like Cookbook 0.11 where the
sync depends on it: dates on every save, read-only fields added when reading, `409` for a taken name, `422` for
a missing one, and `406` for a missing image. Tests change its recipes like a user of the web interface would
and inject failures such as server errors and dropped connections.

The module is used only by the tests of `sync` and `app`; no product code depends on it.
