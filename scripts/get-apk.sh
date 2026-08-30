#!/usr/bin/env bash
set -euo pipefail

# Script para descargar el APK generado por el workflow android-build.yml
# desde GitHub Actions artifacts después de subir el repo y ejecutar el build.

# Configuration (can be overridden via environment variables):
#   APK_REPO          - GitHub repo owner/name (default: detected via gh)
#   APK_WORKFLOW      - Workflow file name (default: android-build.yml)
#   APK_DEST_DIR      - Destination directory (default: $PWD/.apk-builds)
#   APK_FLAVOR        - APK flavor filter (default: arm64; use 'all' for all)
#   APK_INSTALL       - If "1", attempt to install via adb/pm (default: 0)

APK_REPO="${APK_REPO:-rotalexdev/AgentAI}"
APK_WORKFLOW="${APK_WORKFLOW:-android-build.yml}"
APK_DEST_DIR="${APK_DEST_DIR:-$PWD/.apk-builds}"
APK_FLAVOR="${APK_FLAVOR:-arm64}"
APK_INSTALL="${APK_INSTALL:-0}"

command -v gh >/dev/null 2>&1 || { echo "error: gh CLI not found" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "error: gh is not authenticated" >&2; exit 1; }

# Find the most recent successful run of the workflow on main
run="$(gh run list --repo "$APK_REPO" --workflow "$APK_WORKFLOW" --branch main --status success --json databaseId --jq '.[0].databaseId')"
[ -n "$run" ] || { echo "error: no successful run of $APK_WORKFLOW on main branch of $APK_REPO" >&2; exit 1; }

mkdir -p "$APK_DEST_DIR"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Downloading APK artifact from run #$run ..."
gh run download "$run" --repo "$APK_REPO" -n apk --dir "$tmp"

installed=()
# This workflow produces APKs at: app/build/outputs/apk/*/release/*.apk
# Support both "app-$FLAVOR-release.apk" and generic "*.apk" patterns
for apk in "$tmp"/**/*.apk "$tmp"/*.apk; do
    [ -f "$apk" ] || continue
    name="$(basename "$apk")"

    case "$APK_FLAVOR" in
        all) ;;
        *)
            # Match flavors: app-arm64-release.apk, app-arm64.apk, etc.
            case "$name" in
                "app-$APK_FLAVOR-release"*) ;;
                *$APK_FLAVOR*.apk) ;;
                *) continue ;;
            esac
            ;;
    esac

    latest="$APK_DEST_DIR/$PROJECT-$APK_FLAVOR-latest.apk"

    # Determine PROJECT name from repo
    PROJECT="${APK_REPO##*/}"

    cp "$apk" "$latest"
    cp "$apk" "$APK_DEST_DIR/$PROJECT-$APK_FLAVOR-run$run-$(date +%Y%m%d-%H%M%S).apk"
    echo "Saved: $latest"
    installed+=("$latest")
done

[ "${#installed[@]}" -gt 0 ] || { echo "error: no APK matching flavor '$APK_FLAVOR' from run #$run" >&2; exit 1; }

if [ "${APK_INSTALL:-0}" = "1" ]; then
    if command -v adb >/dev/null 2>&1; then
        adb install -r "${installed[0]}"
    else
        pm install -r "${installed[0]}"
    fi
fi