#!/bin/bash
#
# Modbus Schema Toolkit
# Copyright (C) 2019-2025 Niels Basjes
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Release procedure.
# This uses the maven-release-plugin which has been configured to ONLY modify the local git repo.

# ----------------------------------------------------------------------------------------------------
SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

echo "PWD: ${SCRIPTDIR}"

cd "${SCRIPTDIR}" || ( echo "This should not be possible" ; exit 1 )

# Working directory is now the root of the project

# ----------------------------------------------------------------------------------------------------
#https://wiki.archlinux.org/index.php/Color_Bash_Prompt
# Reset
export Color_Off='\e[0m'      # Text Reset

# High Intensity
export IRed='\e[0;91m'        # Red
export IYellow='\e[0;93m'     # Yellow
export IBlue='\e[0;94m'       # Blue
export IWhite='\e[0;97m'      # White

# Bold High Intensity
export BIRed='\e[1;91m'       # Red
export BIYellow='\e[1;93m'     # Yellow
export BIGreen='\e[1;92m'     # Green
export BIBlue='\e[1;94m'      # Blue

function info() {
  echo -e "${Color_Off}${IWhite}[${BIBlue}INFO${IWhite}] ${Color_Off}${1}"
}

function pass() {
  echo -e "${Color_Off}${IWhite}[${BIGreen}PASS${IWhite}] ${Color_Off}${1}"
}

function warn() {
  echo -e "${Color_Off}${IWhite}[${BIYellow}WARN${IWhite}] ${IYellow}${1}${Color_Off}"
}

function fail() {
  echo -e "${Color_Off}${IWhite}[${BIRed}FAIL${IWhite}] ${IRed}${1}${Color_Off}"
}

function die() {
  echo -e "${Color_Off}"
  echo -e "${IWhite}[${BIRed}FAIL${IWhite}] ${IYellow}/========================================================================"
  echo -e "${IWhite}[${BIRed}FAIL${IWhite}] ${IYellow}| ${BIRed} ---------->>> PROCESS WAS ABORTED <<<---------- ${IYellow}"
  echo -e "${IWhite}[${BIRed}FAIL${IWhite}] ${IYellow}| ${BIRed} $* ${IYellow}"
  echo -e "${IWhite}[${BIRed}FAIL${IWhite}] ${IYellow}\\========================================================================"
  echo -e "${Color_Off}"
  exit 1
}

# ----------------------------------------------------------------------------------------------------

# Pre flight checks
## Ensure all has been committed
info "Checking tree status"
if [[ -z $(git status -s) ]]
then
  pass "Tree is clean"
else
  git status
  die "Tree is dirty, must commit everything"
fi

## Ensure we have all upstream updates (like patches from Renovate)
info "Checking up to date status"
git pull
gitPullStatus=$?
if [ ${gitPullStatus} -ne 0 ];
then
    fail "We just received changes."
    exit ${gitPullStatus}
else
    pass "Everything is up to date."
fi

# ----------------------------------------------------------------------------------------------------
# Forcing a manual gpg signing action to ensure the password is known
(
  cd /tmp || die "Unable to enter /tmp"
  echo x > ReleaseProcess-$$.txt
  gpg --clearsign ReleaseProcess-$$.txt
  rm ReleaseProcess-$$.txt ReleaseProcess-$$.txt.asc
)

info "GPG workaround: Starting"
runGpgSignerInBackGround(){
  while : ; do date ; echo "test" | gpg --clearsign ; sleep 10s ; done
}

runGpgSignerInBackGround > /dev/null 2>&1 &
GpgSignerPID=$!

info "GPG workaround: Running (PID=${GpgSignerPID})"

killSigner() {
  info "GPG workaround: Killing (PID=${GpgSignerPID})"
  kill ${GpgSignerPID}
  info "GPG workaround: Killed"
}

trap killSigner EXIT
trap killSigner SIGINT

# ----------------------------------------------------------------------------------------------------
## Prepare the release: Make releasable version and make tag.
info "Doing release:prepare"
mvn release:prepare -B
prepareStatus=$?
if [ ${prepareStatus} -ne 0 ];
then
    fail "Release prepare failed."
    exit ${prepareStatus}
else
    pass "Release prepare Success."
fi

# ----------------------------------------------------------------------------------------------------
# Check if build for this tag is reproducible
git checkout "$(git describe --abbrev=0)"
# ----------------------------------------------------------------------------------------------------
info "Publishing for reproduction check to Local repo"
mvn clean install -PpackageForRelease -PskipQuality
reproCheckPublishStatus=$?
if [ ${reproCheckPublishStatus} -ne 0 ];
then
    git switch -
    fail "Publishing for reproduction check failed."
    exit ${reproCheckPublishStatus}
else
    pass "Publishing for reproduction check Success."
fi

# ----------------------------------------------------------------------------------------------------
info "Checking build reproducibility ... "
mvn clean verify -PpackageForRelease -PskipQuality -PartifactCompare
reproducibleStatus=$?
git switch -
if [ ${reproducibleStatus} -ne 0 ];
then
    fail "Build is NOT reproducible."
    exit ${reproducibleStatus}
else
    pass "Build is reproducible."
fi

# ----------------------------------------------------------------------------------------------------
# Actually run the release: Effectively mvn deploy towards Sonatype
info "Doing release:perform"
mvn release:perform
performStatus=$?
if [ ${performStatus} -ne 0 ];
then
    fail "Release perform failed."
    exit ${performStatus}
else
    pass "Release perform Success."
fi

# ----------------------------------------------------------------------------------------------------
#
# Now check SONATYPE
#
RELEASEVERSION=$(git describe --abbrev=0| sed 's/^v//')

info "Now verify Sonatype to release version ${RELEASEVERSION}"
info "Go to https://central.sonatype.com/publishing/deployments"
warn "Press any key abort or 'c' to continue and push it upstream"
read -n 1 k <&1
if [[ $k = c ]] ;
then
  pass "Release worked, pushing results"
else
  die "Aborting, nothing was pushed."
fi

warn "Now go and manually push it all"

# ----------------------------------------------------------------------------------------------------
echo "git push"
echo "git push --tags"
echo ""
echo "# Now build and push the docker images"
echo "git checkout v${RELEASEVERSION}"
echo "mvn docker:push -pl :modbus-tcp-graphql"
echo "mvn docker:push -pl :sunspec-graphql"
echo "git checkout main"

# ----------------------------------------------------------------------------------------------------
