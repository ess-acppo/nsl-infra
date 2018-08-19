This repository contains code code to assist installing the application stack as required by the NSL apps ([editor](https://github.com/bio-org-au/nsl-editor), [mapper](https://github.com/bio-org-au/mapper) and [services](https://github.com/bio-org-au/services))

Technologies used are:

* [Ansible](https://www.ansible.com/)
* [Packer](https://www.packer.io/)
* [Vagrant](https://www.vagrantup.com/)


### Steps to stand up a new environment: 

# Manual steps in any cloud or Datacenter
1. Provision a ubuntu / redhat machine possibly in AWS ( or elsewhere) ( If using Vagrant Oracle VirtualBox is used to automatically provision a VM) 
1. Include the public ip/hostname and the private ip in the inventory/*your_env_name_file*
1. You could also use a IP based discovery dynamic inventory file
1. Create corresponding dir and files under group_vars. Use the existing env dir/files as examples
1. Run the following ansible-playbook command to install
    1. Tomcat
    1. Postgres
    1. ApacheDS
    1. Command: ```ansible-playbook  -i inventory/*your_env_name_file* -u ubuntu --private-key ../KEY.pem playbooks/site.yml ```
1. Run the following ansible-playbook command to deploy war files for the NSL apps
```ansible-playbook  -i inventory/*your_env_name_file* -u ubuntu --private-key ../KEY.pem playbooks/deploy.yml ```

# Automated provisioning in AWS
1. The following ansible command stands up a set of AWS resources ```ansible-playbook -vvv playbooks/infra.yml -e "nxl_env_name=$ENVIRONMENT_NAME"```. The anisble provisioners are not run. We use a pre-existing AMI which contains all previously provisioned software components. The full list of such components can be found in ansible ( site.yml , Step 4 under [manual deployment](#manual-steps-in-any-cloud-or-datacenter)])
1. The following ansible command will deploy a default set of war files and corresponding configuration into tomcat ```sed -ie \'s/.*instance_filters = tag:env=.*$/instance_filters = tag:env=$ENVIRONMENT_NAME/g\' aws_utils/ec2.ini && ansible-playbook -i aws_utils/ec2.py -u ubuntu  playbooks/deploy.yml -e '{"nxl_env_name":"$ENVIRONMENT_NAME","apps":[{"app": "services"},{"app": "editor"},{"app": "mapper"}], "war_names": [{"war_name": "nsl#services##1.0123"},{"war_name": "nsl#editor##1.44"},{"war_name": "nsl#mapper##1.0017"}   ],   "war_source_dir": "~/agri/nsl-infra"}'```

The above ansible commands has also been configured to run via Jenkins using the jenkins/aws_infra.gvy and jenkins/services.gvy ( for services)

# In Vagrant
1. Have [Vagrant](https://www.vagrantup.com/) installed int he machine
1. Have a virtualization software like [Virtual box](https://www.virtualbox.org/) installed
1. Run `vagrant up` from the repo root.  _This will be slow for the first time as it will download a Ubuntu image as defined in Varantfile_. This step also runs ansible provisioners


# Create ami with ansible provisioned software components
1. Simply run `packer build packer-template.json`. This will use AWS EBS to create a new ami and output the ami id. This ami may be used in while standing up a AWS env(automated-provisioning-in-aws)

### CI/CD set:

Jenkins is our CI server. Under the covers jenkins uses 
* shell scripts, 
* Ansible playbooks, 
* github wehooks 

As soon as a developer pushes changes to the remote github repo ; github webhook will trigger a build job in jenkins. 
Jenkins plugins used ( not an exaustive list) :
* Build Token Root Plugin (https://wiki.jenkins.io/display/JENKINS/Build+Token+Root+Plugin) to enable build trigger without authentication.
* Pipeline to enable pipeline as code (https://jenkins.io/solutions/pipeline/)

To stand up an environment run the Jenkins jobs in the following order:
```
    1. create_environment
        1. Stands up a linux debian server with our default SOE
        1. Creates all underlying aws infrastructure for a private/public subnet isolated architecture
        1. Attach ssl certficates
        1. Create DNS entries using alias

    2. build_deploy_NXL
        1. prep the server
        1. Install three war files to tomcat
        1. Finally import data from one of the below options)

    3. bootstrap_db
        1. Drops the existing database
        1. Creates necessary db and sschemas
        1. Reloads data from a coice of three options
            * baseline working dataset tested throughly
            * today's data import
            * dataset extract from certain date
        1. Perform inistial cleanup on the dataset
        1. Create NSL tables, mapper tables and tree tables
        1. Perform configuration management of the NSL applications nessesary for the data to be imported
```

### Report bugs using github issues
Please raise issues on github if any of the above does not work and we are happy to assist you...

## Dataloading
We also have an ansible role to just load data into the NSL DB. It can be invoked by running the following command: 
```ansible-playbook  -i inventory/poc2 -u ubuntu --private-key ../KEY.pem playbooks/bootstrap_db.yml --tags "load-data"```
or
```sed -ie 's/.*instance_filters = tag:env=.*$/instance_filters = tag:env=aristotle-ICN/g' aws_utils/ec2.ini & ansible-playbook -i aws_utils/ec2.py -u ubuntu --private-key ../KEY.pem playbooks/bootstrap_db.yml --tags "load-data" --extra-vars "@shard_vars/icn.json"```

It does the following:
```
* copies the tab sperate file data.tsv into the server. 
* Then runs the postgres pl sql script data_load.sql. 
* Waits for a human to perform following steps in the service web UI.
    * Create BPNI and BPC trees
    * Rebuild BPNI
    * Reconstruct names
    * Construct reference citation string
* Human can continue the ansible script to finish tree creation.

How to create the data.tsv file
* Export from excel as UTF-16LE file named "data16.tsv"
* run command 
    iconv -f UTF-16LE -t UTF-8 data16.tsv > data.tsv
```
## Known issues
1. At times during provisioning of a fresh machine ldap user creation doesn't work. If login fails due to domain/user not found then ldap config and user needs to be done manually. Relevant code to be run is: [create.ldif](https://github.com/ess-acppo/nsl-infra/blob/6ff2c4b78719592e405a2e4554a0383877b1c86e/playbooks/roles/apacheds/tasks/main.yml#L40) and [add_user.ldif](https://github.com/ess-acppo/nsl-infra/blob/6ff2c4b78719592e405a2e4554a0383877b1c86e/playbooks/roles/apacheds/tasks/main.yml#L43)


## Notes
### Getting a fresh instance up in AWS:
Some of these have been incorporated into above sections but this section gives the basic building blocks.
1. Provision an new AWS env using [AWS env section](automated-provisioning-in-aws)
1. To install all war files using ansible. ```sed -ie 's/.*instance_filters = tag:env=.*$/instance_filters = tag:env=aristotle/g' aws_utils/ec2.ini && ansible-playbook -i aws_utils/ec2.py -u ubuntu -vvv  playbooks/deploy.yml -e '{"nxl_env_name":"aristotle","apps":[{"app": "services"},{"app": "editor"},{"app": "mapper"}], "war_names": [{"war_name": "nsl#services##1.0123"},{"war_name": "nsl#editor##1.44"},{"war_name": "nsl#mapper##1.0017"}   ],   "war_source_dir": "~/agri/nsl-infra"}'```. Ensure that war_source_dir contains the matching war files
1. Load data ( NXL specific ) ported from taxatree using [data loading](#Dataloading)
1. Sets up tunnel to instance behind bastion `ssh -L 55432:localhost:5432 ubuntu@ip-172-31-52-196.ap-southeast-2.compute.internal`
1. Command to update shard images :
- ```sed -ie 's/.*instance_filters = tag:env=.*$/instance_filters = tag:env=aristotle/g' aws_utils/ec2.ini && ansible-playbook -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml --tags "configuration" --extra-vars "@shard_vars/iczn.json" -e '{"nxl_env_name":"dev-iczn","elb_dns":"dev-iczn.oztaxa.com"}```
- OR ```ansible-playbook -i inventory/poc2 -u ubuntu playbooks/deploy.yml --tags "configuration" --extra-vars "@shard_vars/icn.json"```

# How create a fresh AMI
1. Install mapper --> sed -ie 's/.*instance_filters = tag:env=.*$/instance_filters = tag:env=dis-iczn /g' aws_utils/ec2.ini && ansible-playbook -i aws_utils/ec2.py -u ubuntu -vvv playbooks/deploy.yml -e '{"elb_dns":"iczn.dis.oztaxa.com", "nxl_env_name":"dis-iczn","apps":[{"app": "mapper"}], "war_names": [{"war_name": "nsl#mapper##1.0017"} ], "war_source_dir": "~/agri/nsl-infra"}
1. Install services --> Run Jenkins job
1. Install editor --> Run Jenkins job
1. Load data i.e bootstrap --> 

