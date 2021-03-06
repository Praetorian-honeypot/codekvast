#!/usr/bin/env bash
#---------------------------------------------------------------------------------------------------
# Deploys Codekvast login to all environments
#---------------------------------------------------------------------------------------------------

source $(dirname $0)/.check-requirements.sh

ansible-playbook --private-key ~/.ssh/codekvast-amazon.pem playbooks/login.yml $*

