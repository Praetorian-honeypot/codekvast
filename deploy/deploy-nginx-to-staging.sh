#!/usr/bin/env bash
#---------------------------------------------------------------------------------------------------
# Deploys Nginx to the staging environment
#---------------------------------------------------------------------------------------------------

source $(dirname $0)/.check-requirements.sh

ansible-playbook --private-key ~/.ssh/codekvast-amazon.pem playbooks/nginx.yml --limit tag_Env_staging $*
