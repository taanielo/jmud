#!/usr/bin/env bash
# backup-data.sh — create a compressed archive of the jmud data/ tree.
#
# Usage:
#   ./scripts/backup-data.sh [--retain-days N]
#
# The script creates a timestamped archive under backups/ and then prunes
# archives older than RETAIN_DAYS days (default: 14).
#
# Recommended crontab entry (runs every day at 03:00, retains 14 days):
#   0 3 * * * cd /opt/jmud && ./scripts/backup-data.sh --retain-days 14 >> /var/log/jmud-backup.log 2>&1
#
# Restore:
#   tar xzf backups/data-YYYY-MM-DD-HHmmSS.tar.gz
# The data/ directory is restored relative to the current working directory.
# See docs/runbook.md § "Automated archive backups" for full restore steps.

set -euo pipefail

# ── configuration ────────────────────────────────────────────────────────────
RETAIN_DAYS=14

# parse optional --retain-days argument
while [[ $# -gt 0 ]]; do
    case "$1" in
        --retain-days)
            RETAIN_DAYS="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

# ── paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${PROJECT_ROOT}/backups"
DATA_DIR="${PROJECT_ROOT}/data"
TIMESTAMP="$(date +%F-%H%M%S)"
ARCHIVE="${BACKUP_DIR}/data-${TIMESTAMP}.tar.gz"

# ── sanity checks ─────────────────────────────────────────────────────────────
if [[ ! -d "${DATA_DIR}" ]]; then
    echo "ERROR: data directory not found at ${DATA_DIR}" >&2
    exit 1
fi

mkdir -p "${BACKUP_DIR}"

# ── create archive ────────────────────────────────────────────────────────────
echo "[$(date -Iseconds)] Creating backup: ${ARCHIVE}"
tar czf "${ARCHIVE}" -C "${PROJECT_ROOT}" data/
echo "[$(date -Iseconds)] Backup complete: $(du -sh "${ARCHIVE}" | cut -f1)"

# ── retention: remove archives older than RETAIN_DAYS days ───────────────────
echo "[$(date -Iseconds)] Pruning archives older than ${RETAIN_DAYS} day(s) in ${BACKUP_DIR}/"
find "${BACKUP_DIR}" -name "data-*.tar.gz" -mtime +"${RETAIN_DAYS}" -print -delete
echo "[$(date -Iseconds)] Pruning done."
