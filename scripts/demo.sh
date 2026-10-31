#!/usr/bin/env bash
# Fills a local meuquarto with a demo profile: signs up (or logs in), links accounts, syncs them,
# highlights the first items of each category and prints the profile address.
#
#   scripts/demo.sh --email demo@example.com --password 'a-long-password' --slug demo \
#       [--anilist USER] [--letterboxd USER] [--add ANIME:frieren ...] [--base http://localhost:3000]
#
# The password can also come from DEMO_PASSWORD (keeps it out of the process list). BASE defaults to
# http://localhost:3000 (the web app, which proxies /api to the backend) or the BASE variable.
# Everything goes through the public HTTP API, like the browser does. Nothing is stored: the cookie jar
# is a temp file removed on exit. Needs bash, curl, awk and sed (Git Bash is enough on Windows).
set -u
STATUS=""

BASE="${BASE:-http://localhost:3000}"
EMAIL=""
PASSWORD="${DEMO_PASSWORD:-}"
SLUG=""
ANILIST=""
LETTERBOXD=""
ADDS=()
HIGHLIGHTS_PER_CATEGORY=4
CATEGORIES="MOVIE SERIES ANIME MANGA BOOK GAME MUSIC"

usage() {
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

die() {
  echo "error: $*" >&2
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    --email) EMAIL="${2:-}"; shift 2 ;;
    --password) PASSWORD="${2:-}"; shift 2 ;;
    --slug) SLUG="${2:-}"; shift 2 ;;
    --anilist) ANILIST="${2:-}"; shift 2 ;;
    --letterboxd) LETTERBOXD="${2:-}"; shift 2 ;;
    --add) ADDS+=("${2:-}"); shift 2 ;;
    --base) BASE="${2:-}"; shift 2 ;;
    -h|--help) usage 0 ;;
    *) echo "unknown option: $1" >&2; usage 1 ;;
  esac
done

[ -n "$EMAIL" ] || die "--email is required"
[ -n "$PASSWORD" ] || die "--password (or DEMO_PASSWORD) is required"
[ -n "$SLUG" ] || die "--slug is required"
command -v curl >/dev/null 2>&1 || die "curl not found"
BASE="${BASE%/}"

JAR="$(mktemp)"
BODY="$(mktemp)"
HEADERS="$(mktemp)"
trap 'rm -f "$JAR" "$BODY" "$HEADERS"' EXIT

# --- HTTP helpers -------------------------------------------------------------------------------

csrf_token() {
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR" | tail -n 1
}

refresh_csrf() {
  curl -sS -o /dev/null -b "$JAR" -c "$JAR" "$BASE/api/auth/csrf" || die "cannot reach $BASE (is the stack up?)"
}

# json_escape STRING: for values we put inside a JSON string.
json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

# request METHOD PATH [JSON]: sets STATUS (HTTP code), reads the response into $BODY and headers into $HEADERS.
request() {
  local method="$1" path="$2" data="${3:-}" attempt
  for attempt in 1 2; do
    local args=(-sS -o "$BODY" -D "$HEADERS" -w '%{http_code}' -X "$method" -b "$JAR" -c "$JAR" -H 'Accept: application/json')
    if [ "$method" != "GET" ]; then
      [ -n "$(csrf_token)" ] || refresh_csrf
      args+=(-H "X-XSRF-TOKEN: $(csrf_token)")
    fi
    [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data "$data")
    STATUS="$(curl "${args[@]}" "$BASE$path")" || die "cannot reach $BASE (is the stack up?)"
    # A 403 on a write usually means the CSRF token rotated (new session): refresh once and retry.
    if [ "$STATUS" = "403" ] && [ "$method" != "GET" ] && [ "$attempt" = "1" ]; then
      refresh_csrf
      continue
    fi
    break
  done
}

error_code() {
  sed -n 's/.*"error"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$BODY" | head -n 1
}

retry_after() {
  awk 'tolower($1) == "retry-after:" { gsub("\r", "", $2); print $2 }' "$HEADERS" | tail -n 1
}

describe_failure() {
  local code wait
  code="$(error_code)"
  wait="$(retry_after)"
  if [ "$STATUS" = "429" ]; then
    echo "rate limited (HTTP 429${code:+, $code})${wait:+; try again in ${wait}s}"
  else
    echo "HTTP $STATUS${code:+ ($code)}"
  fi
}

# ids_of FILE: every top-level "id":N of a list response, one per line.
ids_of() {
  grep -o '"id":[0-9]*' "$1" | sed 's/"id"://'
}

# --- 1. account ---------------------------------------------------------------------------------

echo "Using $BASE"
refresh_csrf

EM="$(json_escape "$EMAIL")"
PW="$(json_escape "$PASSWORD")"
SL="$(json_escape "$SLUG")"

request POST /api/auth/signup "{\"email\":\"$EM\",\"password\":\"$PW\",\"slug\":\"$SL\"}"
if [ "$STATUS" = "201" ]; then
  echo "Signed up $EMAIL as /$SLUG"
else
  signup_failure="$(describe_failure)"
  request POST /api/auth/login "{\"email\":\"$EM\",\"password\":\"$PW\"}"
  if [ "$STATUS" = "200" ]; then
    echo "Signed in as $EMAIL (account already existed)"
  else
    echo "Sign up failed: $signup_failure" >&2
    die "sign in failed too: $(describe_failure)"
  fi
fi
refresh_csrf # the session changed: pick up the token that goes with it

request GET /api/auth/me
ACTUAL_SLUG="$(sed -n 's/.*"slug"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$BODY" | head -n 1)"
[ -n "$ACTUAL_SLUG" ] || die "signed in, but the account has no profile address"
if [ "$ACTUAL_SLUG" != "$SLUG" ]; then
  echo "note: this account already uses the address /$ACTUAL_SLUG (ignoring --slug $SLUG)"
fi

# --- 2. linked accounts + sync ------------------------------------------------------------------

# link_and_sync PROVIDER USERNAME
link_and_sync() {
  local provider="$1" username="$2" id
  request POST /api/linked-accounts "{\"provider\":\"$provider\",\"username\":\"$(json_escape "$username")\"}"
  if [ "$STATUS" = "201" ]; then
    echo "Linked $provider account \"$username\""
  else
    local failure
    failure="$(describe_failure)"
    # Already linked? Then reuse the existing link.
    request GET /api/linked-accounts
    id="$(tr '{' '\n' < "$BODY" | grep "\"provider\":\"$provider\"" | grep -o '"id":[0-9]*' | head -n 1 | sed 's/"id"://')"
    if [ -z "$id" ]; then
      echo "warning: could not link $provider \"$username\": $failure" >&2
      return 1
    fi
    echo "$provider is already linked (reusing it)"
  fi
  if [ -z "${id:-}" ]; then
    request GET /api/linked-accounts
    id="$(tr '{' '\n' < "$BODY" | grep "\"provider\":\"$provider\"" | grep -o '"id":[0-9]*' | head -n 1 | sed 's/"id"://')"
  fi
  [ -n "$id" ] || { echo "warning: linked $provider but could not read its id" >&2; return 1; }

  request POST "/api/linked-accounts/$id/sync"
  if [ "$STATUS" = "200" ]; then
    local state count err
    state="$(sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$BODY" | head -n 1)"
    count="$(sed -n 's/.*"itemsCount"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$BODY" | head -n 1)"
    err="$(sed -n 's/.*"error"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$BODY" | head -n 1)"
    echo "Synced $provider: ${state:-?}, ${count:-0} items${err:+ ($err)}"
  else
    echo "warning: sync of $provider did not run: $(describe_failure)" >&2
    return 1
  fi
}

[ -n "$ANILIST" ] && { link_and_sync ANILIST "$ANILIST" || true; }
[ -n "$LETTERBOXD" ] && { link_and_sync LETTERBOXD "$LETTERBOXD" || true; }

# --- 3. hand-added items (optional): --add CATEGORY:search words --------------------------------

for spec in "${ADDS[@]+"${ADDS[@]}"}"; do
  category="${spec%%:*}"
  query="${spec#*:}"
  if [ "$category" = "$spec" ] || [ -z "$query" ]; then
    echo "warning: --add expects CATEGORY:query (for example ANIME:frieren), got \"$spec\"" >&2
    continue
  fi
  encoded="$(printf '%s' "$query" | sed -e 's/%/%25/g' -e 's/ /%20/g' -e 's/&/%26/g' -e 's/+/%2B/g' -e 's/#/%23/g')"
  request GET "/api/catalog/search?category=$category&q=$encoded"
  external="$(sed -n 's/.*"externalId"[[:space:]]*:[[:space:]]*"\([A-Za-z0-9_-]*\)".*/\1/p' "$BODY" | head -n 1)"
  if [ "$STATUS" != "200" ] || [ -z "$external" ]; then
    echo "warning: no catalog result for $category \"$query\" ($(describe_failure))" >&2
    continue
  fi
  request POST /api/collection "{\"category\":\"$category\",\"externalId\":\"$external\"}"
  if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
    echo "Added $category \"$query\" to the collection"
  else
    echo "warning: could not add $category \"$query\": $(describe_failure)" >&2
  fi
done

# --- 4. highlights: first N items of each category ----------------------------------------------

total_highlighted=0
for category in $CATEGORIES; do
  request GET "/api/collection?category=$category&size=$HIGHLIGHTS_PER_CATEGORY"
  [ "$STATUS" = "200" ] || { echo "warning: cannot list $category: $(describe_failure)" >&2; continue; }
  ids="$(ids_of "$BODY")"
  [ -n "$ids" ] || continue
  added=0
  for id in $ids; do
    request POST /api/highlights "{\"category\":\"$category\",\"collectionItemId\":$id}"
    if [ "$STATUS" = "201" ]; then
      added=$((added + 1))
    elif [ "$STATUS" != "409" ]; then
      # 409 would mean already highlighted; anything else (for example the plan limit) ends this category.
      echo "note: stopped highlighting $category: $(describe_failure)"
      break
    fi
  done
  [ "$added" -gt 0 ] && echo "Highlighted $added item(s) in $category"
  total_highlighted=$((total_highlighted + added))
done
[ "$total_highlighted" -eq 0 ] && echo "No new highlights (nothing in the collection yet, or already highlighted). Empty categories fall back to the most recent items."

# --- 5. result ----------------------------------------------------------------------------------

echo
echo "Profile: $BASE/$ACTUAL_SLUG"
