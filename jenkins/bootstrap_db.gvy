stage("Prepare env-name= $ENVIRONMENT_NAME for play") {
    node{
        dir('nsl-infra') {
                checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
                //sh 'mkdir playbooks/roles/bootstrap-db/files/'
                sh 'cp /home/dawr/tblBiota_$(date +%Y%m%d).csv playbooks/roles/bootstrap-db/files/tblbiota.csv'
            }
        }
}
stage("Running Bootstrap data Operation") {
    node{
        dir('nsl-infra'){
            def shard_vars = '@shard_vars/$SHARD_TYPE.json'

            def verbose = '-v'

            if (ENVIRONMENT_NAME) {
                def env_instance_name = "$ENVIRONMENT_NAME".split(",")[0]
                def env_name = env_instance_name.split("-")[0]
                def elb_dns = "$env_name"+"-"+"$SHARD_TYPE"+".oztaxa.com"
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook $verbose -i aws_utils/ec2.py -u ubuntu playbooks/bootstrap_db.yml --tags \"load-data\" -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) { // not fully implemented
                def env_name = "$INVENTORY_NAME".split(",")[0]
                def extra_vars = /'{"nxl_env_name":"$env_name"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }
        }
    }
}
