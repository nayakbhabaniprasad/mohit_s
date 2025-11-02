#!/bin/bash

# Fraud and Risk Scanner - Scanner Habitat Plan
pkg_name='scanner'
pkg_origin='bizopsbank'
pkg_maintainer="BizOps Bank <bizopsbank@mastercard.com>"
pkg_license=("Mastercard")
pkg_description="scanner"
pkg_scaffolding='mastercard/scaffolding_java_standalone'
scaffold_java_major_minor='1.21'

# Dependencies
#pkg_deps=(mastercard/zulujdk11)
pkg_build_deps=(mastercard/maven mastercard/zulujdk11)

# Scaffolding specific variables
standalone_app_artifactory_url="https://artifacts.mastercard.int/artifactory/snapshots/com/mastercard/fraud-risk-scanner-scanner/0.1.0-SNAPSHOT/fraud-risk-scanner-scanner-0.1.0-SNAPSHOT.jar"
#standalone_app_artifactory_url="https://artifacts.mastercard.int/artifactory/releases/com/mastercard/fraud-risk-scanner-scanner/0.1.0/fraud-risk-scanner-scanner-0.1.0.jar"
standalone_app_name="scanner"

do_prepare() {
  pkg_svc_user='hab'
  pkg_svc_group='hab'
}

pkg_version='0.1.0-SNAPSHOT'
#pkg_version='0.1.0'

#pkg_version() {
#  pushd /src > /dev/null
#  $(pkg_path_for mastercard/maven)/bin/mvn help:evaluate -Dexpression=project.version -q -DforceStdout
#  popd > /dev/null
#}

#do_before() {
#  update_pkg_version
#}

#do_build() {
#  $(pkg_path_for mastercard/maven)/bin/mvn clean install
#}

#do_install() {
#  cp /src/target/${pkg_name}-${pkg_version}.jar ${pkg_prefix}/${pkg_name}.jar
#}
