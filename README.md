This repository contains code code to assist installing the application stack as required by the NSL apps ([editor!](https://github.com/bio-org-au/nsl-editor), [mapper!](https://github.com/bio-org-au/mapper) and [services!](https://github.com/bio-org-au/services))

Technologies used are:

* Ansible
* Packer
* Vagrant


## Steps to stand up a new environment: 

1. Provision a ubuntu / redhat machine ( if not using Vagrant in which case Oracle VirtualBox is used) 
1. Include the pulic ip/hostname and the private ip in the inventory/*your_env_name_file*
1. Create corresponding dir under group_vars.
1. Run the following ansible-playbook command to install
    1. Tomcat
    1. Postgres
    1. ApacheDS
```ansible-playbook  -i inventory/*your_env_name_file* -u ubuntu --private-key ../DAWRAWSSYD.pem playbooks/site.yml ```
1. Run the following ansible-playbook command to deploy war files for the NSL apps
```ansible-playbook  -i inventory/*your_env_name_file* -u ubuntu --private-key ../DAWRAWSSYD.pem playbooks/deploy.yml ```