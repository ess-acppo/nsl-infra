/*
Perquisites before this can be run in a machine ( irrespective of whether it run using Jenkins):
1. Install boto
2. Configure AWS KEYS
3. Configure ssh to enable shh through bastion
*/

stage("Bootstrapping data into DB") {
    node{
        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
        dir('nsl-infra'){
            def shard_vars = '@shard_vars/$SHARD_TYPE.json'

            if (ENVIRONMENT_NAME) {
                def env_instance_name = "$ENVIRONMENT_NAME".split(",")[0]
                def env_name = env_instance_name.split("-")[0]
                def elb_dns = "$SHARD_TYPE"+"."+"$env_name"+".oztaxa.com"
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/bootstrap_db.yml --tags \"load-data\" -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) { // not fully implemented
                def env_name = "$INVENTORY_NAME".split(",")[0]
                def extra_vars = /'{"nxl_env_name":"$env_name"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }
        }
    }
}
