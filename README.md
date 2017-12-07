This repository contains code code to assist installing the application stack as required by the NSL apps ([editor](https://github.com/bio-org-au/nsl-editor), [mapper](https://github.com/bio-org-au/mapper) and [services](https://github.com/bio-org-au/services))

Technologies used are:

* [Ansible](https://www.ansible.com/)
* [Packer](https://www.packer.io/)
* [Vagrant](https://www.vagrantup.com/)


### Steps to stand up a new environment: 

1. Provision a ubuntu / redhat machine possibly in AWS ( or elsewhere) ( If using Vagrant Oracle VirtualBox is used to automatically provision a VM) 
1. Include the public ip/hostname and the private ip in the inventory/*your_env_name_file*
1. Create corresponding dir and files under group_vars. Use the existing env dir/files as examples
1. Run the following ansible-playbook command to install
    1. Tomcat
    1. Postgres
    1. ApacheDS
```ansible-playbook  -i inventory/*your_env_name_file* -u ubuntu --private-key ../DAWRAWSSYD.pem playbooks/site.yml ```
1. Run the following ansible-playbook command to deploy war files for the NSL apps
```ansible-playbook  -i inventory/*your_env_name_file* -u ubuntu --private-key ../DAWRAWSSYD.pem playbooks/deploy.yml ```



### CI/CD set:

Jenkins is our CI server. Under the covers jenkins uses 
* shell scripts, 
* Ansible playbooks, 
* github wehooks 

As soon as a developer pushes changes to the remote github repo ; github webhook will trigger a build job in jenkins. 
Jenkins plugins used ( not an exaustive list) :
* Build Token Root Plugin (https://wiki.jenkins.io/display/JENKINS/Build+Token+Root+Plugin) to enable build trigger without authentication.
* Pipeline to enable pipeline as code (https://jenkins.io/solutions/pipeline/)


### Report bugs using github issues



## Dataloading
The ansible role to load data into the NSl DB is load-data. It can be invoked by running the following command: 
```ansible-playbook  -i inventory/poc2 -u ubuntu --private-key ../DAWRAWSSYD.pem playbooks/bootstrap_db.yml --tags "load-data"```
It does the following:
* copies the tab sperate file data.tsv into the server. 
* Then runs the postgres pl sql script data_load.sql. 
* Waits for a human to perform following steps in the service web UI.
    * Create BPNI and BPC trees
    * Rebuild BPNI
    * Reconstruct names
    * Construct reference citation string
* Human can continue the ansible script to finish tree creation.