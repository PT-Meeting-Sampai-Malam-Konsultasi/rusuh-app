#!/bin/sh
# Info-Debug.plist must equal Info.plist plus the debug-only NSAppTransportSecurity entry.
set -e
cd "$(dirname "$0")/../iosApp/iosApp"

# Nothing may be missing from (or changed in) the debug plist.
removed=$(diff Info.plist Info-Debug.plist | grep '^<' || true)
if [ -n "$removed" ]; then
  echo "Info.plist lines missing from Info-Debug.plist:"
  echo "$removed"
  exit 1
fi

# Everything the debug plist adds must be the ATS exception (or its comment).
unexpected=$(diff Info.plist Info-Debug.plist | grep '^>' | sed 's/^>[[:space:]]*//' |
  grep -v -e '^<!--' -e '^Keep every other' -e '^<key>NSAppTransportSecurity' -e '^<key>NSExceptionDomains' \
    -e '^<key>localhost' -e '^<key>NSExceptionAllowsInsecureHTTPLoads' -e '^<dict>' -e '^</dict>' -e '^<true/>' || true)
if [ -n "$unexpected" ]; then
  echo "Info-Debug.plist adds more than the ATS exception:"
  echo "$unexpected"
  exit 1
fi
echo "Info-Debug.plist is in sync with Info.plist"
