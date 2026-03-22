#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="$ROOT_DIR/vendor/tools"
PLATFORM_ARCH="$(uname -m)"
PLATFORM_FAMILY="$PLATFORM_ARCH"

case "$PLATFORM_ARCH" in
  x86_64|amd64)
    PLATFORM_ARCH="amd64"
    PLATFORM_FAMILY="x86"
    ;;
  arm64|aarch64)
    PLATFORM_ARCH="arm64"
    PLATFORM_FAMILY="arm"
    ;;
esac

mkdir -p "$TOOLS_DIR"

echo "Project root: $ROOT_DIR"
echo "Detected architecture: ${PLATFORM_ARCH} -> ${PLATFORM_FAMILY}"

install_tidb_lightning() {
  local arch="$1"
  local family="$2"
  local version="v8.5.5"
  local archive_name="tidb-community-toolkit-${version}-linux-${arch}.tar.gz"
  local install_dir="$TOOLS_DIR/${family}/tidb-lightning"
  local temp_archive

  mkdir -p "$install_dir"
  temp_archive="$(mktemp "${TMPDIR:-/tmp}/tidb-community-toolkit-${arch}.XXXXXX")"

  curl -fL "https://download.pingcap.com/${archive_name}" -o "$temp_archive"
  tar -xzf "$temp_archive" -C "$install_dir" --strip-components=1 "tidb-community-toolkit-${version}-linux-${arch}/tidb-lightning"
  chmod +x "$install_dir/tidb-lightning"
  rm -f "$temp_archive"
  echo "Installed tidb-lightning to $install_dir/tidb-lightning"
}

install_dumpling() {
  local arch="$1"
  local family="$2"
  local version="v8.5.5"
  local archive_name="tidb-community-toolkit-${version}-linux-${arch}.tar.gz"
  local install_dir="$TOOLS_DIR/${family}/dumpling"
  local temp_archive

  mkdir -p "$install_dir"
  temp_archive="$(mktemp "${TMPDIR:-/tmp}/tidb-community-toolkit-${arch}.XXXXXX")"

  curl -fL "https://download.pingcap.com/${archive_name}" -o "$temp_archive"
  tar -xzf "$temp_archive" -C "$install_dir" --strip-components=1 "tidb-community-toolkit-${version}-linux-${arch}/dumpling"
  chmod +x "$install_dir/dumpling"
  rm -f "$temp_archive"
  echo "Installed dumpling to $install_dir/dumpling"
}

install_mssql_tools18_binary() {
  local arch="$1"
  local family="$2"
  local tool_name="$3"
  local deb_url="https://packages.microsoft.com/ubuntu/24.04/prod/pool/main/m/mssql-tools18/mssql-tools18_18.6.1.1-1_${arch}.deb"
  local install_dir="$TOOLS_DIR/${family}/${tool_name}"
  local work_dir
  local archive

  mkdir -p "$install_dir"
  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/mssql-tools18-${arch}.XXXXXX")"
  archive="$work_dir/mssql-tools18.deb"

  curl -fL "$deb_url" -o "$archive"
  (
    cd "$work_dir"
    ar x "$archive" debian-binary control.tar.xz data.tar.xz >/dev/null
    tar -xf data.tar.xz -C "$work_dir"
  )
  cp "$work_dir/opt/mssql-tools18/bin/${tool_name}" "$install_dir/${tool_name}"
  chmod +x "$install_dir/${tool_name}"
  rm -rf "$work_dir"
  echo "Installed ${tool_name} to $install_dir/${tool_name}"
}

install_go_sqlcmd() {
  local arch="$1"
  local family="$2"
  local target_name="${3:-sqlcmd-go}"
  local archive_name="sqlcmd-linux-${arch}.tar.bz2"
  local install_dir="$TOOLS_DIR/${family}/sqlcmd"
  local work_dir
  local archive

  mkdir -p "$install_dir"
  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/go-sqlcmd-${arch}.XXXXXX")"
  archive="$work_dir/${archive_name}"

  curl -fL "https://github.com/microsoft/go-sqlcmd/releases/download/v1.9.0/${archive_name}" -o "$archive"
  tar -xjf "$archive" -C "$work_dir"
  cp "$work_dir/sqlcmd" "$install_dir/${target_name}"
  chmod +x "$install_dir/${target_name}"
  rm -rf "$work_dir"
  echo "Installed go-sqlcmd to $install_dir/${target_name}"
}

install_windows_sqlcmd() {
  local variant="$1"
  local archive_name="sqlcmd-windows-${variant}.zip"
  local install_dir="$TOOLS_DIR/windows/sqlcmd/${variant}"
  local archive

  mkdir -p "$install_dir"
  archive="$(mktemp "${TMPDIR:-/tmp}/sqlcmd-windows-${variant}.XXXXXX.zip")"
  curl -fL "https://github.com/microsoft/go-sqlcmd/releases/download/v1.9.0/${archive_name}" -o "$archive"
  unzip -oq "$archive" -d "$install_dir"
  rm -f "$archive"
  echo "Installed Windows sqlcmd (${variant}) to $install_dir"
}

install_windows_mssql_utils() {
  local variant="$1"
  local link_path="$2"
  local install_dir="$TOOLS_DIR/windows/bcp"

  mkdir -p "$install_dir"
  curl -fL "$link_path" -o "$install_dir/MsSqlCmdLnUtils-${variant}.msi"
  echo "Downloaded Windows SQL Server command-line utilities (${variant}) to $install_dir/MsSqlCmdLnUtils-${variant}.msi"
}

install_tidb_lightning "amd64" "x86"
install_tidb_lightning "arm64" "arm"
install_dumpling "amd64" "x86"
install_dumpling "arm64" "arm"
install_mssql_tools18_binary "amd64" "x86" "bcp"
install_mssql_tools18_binary "arm64" "arm" "bcp"
install_mssql_tools18_binary "amd64" "x86" "sqlcmd"
install_mssql_tools18_binary "arm64" "arm" "sqlcmd"
install_go_sqlcmd "amd64" "x86" "sqlcmd-go"
install_go_sqlcmd "arm64" "arm" "sqlcmd-go"
install_windows_sqlcmd "amd64"
install_windows_sqlcmd "arm"
install_windows_sqlcmd "arm64"
install_windows_mssql_utils "x64" "https://download.microsoft.com/download/a/a/4/aa47b3b0-9f67-441d-8b00-e74cd845ea9f/EN/x64/MsSqlCmdLnUtils.msi"
install_windows_mssql_utils "x86" "https://download.microsoft.com/download/a/a/4/aa47b3b0-9f67-441d-8b00-e74cd845ea9f/EN/x86/MsSqlCmdLnUtils.msi"

mkdir -p "$TOOLS_DIR/x86/sqluldr2" "$TOOLS_DIR/arm/sqluldr2"
SQLULDR2_TARGET="$TOOLS_DIR/${PLATFORM_FAMILY}/sqluldr2/sqluldr2"
echo "Place x86 sqluldr2 binary at: $TOOLS_DIR/x86/sqluldr2/sqluldr2"
echo "Place arm sqluldr2 binary at: $TOOLS_DIR/arm/sqluldr2/sqluldr2"
echo "Or set SQLULDR2_DOWNLOAD_URL and let Java runtime install it on first use."
echo "Install PostgreSQL psql / HANA hdbsql on the runtime host, or provide explicit binary paths in job config."
