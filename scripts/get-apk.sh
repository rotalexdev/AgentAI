#!/usr/bin/env bash
set -euo pipefail

REPO="${NOTESCRIBE_REPO:-ulite-Amr/NoteScribe}"
WORKFLOW="android-build.yml"
DEST_DIR="${1:-${APK_DEST_DIR:-$HOME/NoteScribe-builds}}"
FLAVOR="${2:-${APK_FLAVOR:-arm64}}"

command -v gh >/dev/null 2>&1 || { echo "error: gh CLI not found" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "error: gh is not authenticated" >&2; exit 1; }

run="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch main --status success --json databaseId --jq '.[0].databaseId')"
[ -n "$run" ] || { echo "error: no successful run of $WORKFLOW on main" >&2; exit 1; }

mkdir -p "$DEST_DIR"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Downloading apk artifact from run #$run ..."
gh run download "$run" --repo "$REPO" -n apk --dir "$tmp"

installed=()
for apk in "$tmp"/*/release/*.apk; do
    name="$(basename "$apk")"
    case "$FLAVOR" in
        all) ;;
        *)
            case "$name" in
                "app-$FLAVOR-release"*) ;;
                *) continue ;;
            esac
            ;;
    esac
    latest="$DEST_DIR/NoteScribe-$FLAVOR-latest.apk"
    cp "$apk" "$latest"
    cp "$apk" "$DEST_DIR/NoteScribe-$FLAVOR-run$run-$(date +%Y%m%d-%H%M%S).apk"
    echo "Saved: $latest"
    installed+=("$latest")
done

[ "${#installed[@]}" -gt 0 ] || { echo "error: no app-$FLAVOR-release APK on run #$run" >&2; exit 1; }

if [ "${NOTESCRIBE_INSTALL:-0}" = "1" ]; then
    if command -v adb >/dev/null 2>&1; then
        adb install -r "${installed[0]}"
    else
        pm install -r "${installed[0]}"
    fi
fi
