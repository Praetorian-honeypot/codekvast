#!/usr/bin/env bash
#---------------------------------------------------------------------------------------------------
# Deploys all Codekvast components to the staging environment
#---------------------------------------------------------------------------------------------------

source $(dirname $0)/.check-requirements.sh

ansible-playbook --private-key ~/.ssh/codekvast-amazon.pem playbooks/servers.yml --limit tag_Env_staging $*
