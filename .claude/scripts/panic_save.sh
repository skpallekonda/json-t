#!/bin/bash
# Check if 5-hour usage is above 95%
USAGE=$(claude /usage --json | jq '.rate_limits.five_hour.used_percentage')

if [ "$USAGE" -gt 95 ]; then
  echo "TOKEN CRITICAL ($USAGE%). Forcing Panic Save..."
  # This creates a signal for the next turn
  echo "UPDATE STATE.md IMMEDIATELY: Summarize the current line, file, and 3-step recovery plan. Do not write more code." > .claude/panic_signal.tmp
fi