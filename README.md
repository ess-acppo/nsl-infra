This repository contains code code to assist installing the application stack as required by the NSL apps ([editor](https://github.com/bio-org-au/nsl-editor), [mapper](https://github.com/bio-org-au/mapper) and [services](https://github.com/bio-org-au/services))

Technologies used are:

* Ansible
* Packer
* Vagrant


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


### Report bugs using github issues