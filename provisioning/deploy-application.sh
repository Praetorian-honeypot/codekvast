#!/usr/bin/env bash
#---------------------------------------------------------------------------------------------------
# Deploys Codekvast to the production environment
#---------------------------------------------------------------------------------------------------

for f in ~/.boto ~/.ssh/codekvast-amazon.pem; do
    if [ ! -f ${f} ]; then
        echo "Missing required file: $f" 1>&2
        exit 1
    fi
done

cd $(dirname $0)
ansible-playbook --private-key ~/.ssh/codekvast-amazon.pem playbooks/application.yml --limit tag_Env_staging $*
ansible-playbook --private-key ~/.ssh/codekvast-amazon.pem playbooks/application.yml --limit tag_Env_prod $*

