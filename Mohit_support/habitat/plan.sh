#!/bin/bash

# Fraud and Risk Scanner Habitat Plan
pkg_name=fraud-risk-scanner
pkg_origin=mastercard
pkg_version=0.1.0
pkg_maintainer="BizOps Bank <bizops-bank@mastercard.com>"
pkg_license=("Proprietary")
pkg_description="Fraud and Risk Scanner Application"
pkg_upstream_url="https://github.com/mastercard/fraud-risk-scanner"

# Dependencies
pkg_deps=(
  core/openjdk21
  core/curl
)

# Build dependencies
pkg_build_deps=(
  core/maven
  core/git
)

# Application configuration
pkg_exports=(
  [port]=server.port
  [management_port]=management.server.port
)

pkg_exposes=(port management_port)

# Default configuration
pkg_svc_user="hab"
pkg_svc_group="hab"

# Build process
do_build() {
  mvn clean package -DskipTests
}

do_install() {
  # Install the JAR file
  cp target/fraud-risk-scanner-*.jar "${pkg_prefix}/app.jar"
  
  # Install configuration files
  cp -r src/main/resources/* "${pkg_prefix}/config/" 2>/dev/null || true
  
  # Create logs directory
  mkdir -p "${pkg_prefix}/logs"
}

# Health check
do_check() {
  # Basic health check - verify JAR exists
  if [ ! -f "${pkg_prefix}/app.jar" ]; then
    echo "ERROR: Application JAR not found"
    return 1
  fi
  
  echo "Health check passed: Application JAR found"
  return 0
}
