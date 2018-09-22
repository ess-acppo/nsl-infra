node{
    stage("Prepare env-name= $ENVIRONMENT_NAME for play") {
                sh 'rm -rf *'
                sh 'pwd && ls -alhF'
                sh 'whoami'
                sh 'echo "ANSIBLE VERSION :" && ansible --version'
                sh 'echo "PYTHON VERSION :" && python --version'
                sh 'echo "JAVA VERSION :" && java -version'
                checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
                //sh 'mkdir playbooks/roles/bootstrap-db/files/'
                //sh 'cp /home/dawr/tblBiota_$(date +%Y%m%d).csv nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota.csv'
                sh 'cp /var/lib/jenkins/nxl-private/bnti/tblbiota_base.csv nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota.csv'
    }
    stage("Running Bootstrap data Operation env-name= $ENVIRONMENT_NAME") {

        slackSend color: 'good', message: "Started Job ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
                
        def ds_val = "${DATA_SOURCE}"
        def date_val = "${DATE_TO_USE}"
        echo "${ds_val}"
        
        slackSend color: 'good', message: "Processing csv files ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"

        sh 'cp /var/lib/jenkins/nxl-private/bnti/reconstruct-name-strings.sh nsl-infra/playbooks/roles/bootstrap-db/files/reconstruct-name-strings.sh'
        sh 'chmod +x nsl-infra/playbooks/roles/bootstrap-db/files/reconstruct-name-strings.sh'

        sh 'cp /var/lib/jenkins/nxl-private/bnti/refresh-views.sh nsl-infra/playbooks/roles/bootstrap-db/files/refresh-views.sh'
        sh 'chmod +x nsl-infra/playbooks/roles/bootstrap-db/files/refresh-views.sh'

        if (ds_val == "base") {
            sh 'cp /var/lib/jenkins/nxl-private/bnti/tblbiota_base.csv nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota.csv'
        } else {
            if (ds_val == "today") {
                sh 'cp /home/dawr/tblBiota_$(date +%Y%m%d).csv nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota_pp.csv'
                sh 'nsl-infra/playbooks/roles/bootstrap-db/templates/clean-tblbiota.sh nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota_pp.csv > nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota.csv'
            } else {
                echo "${DATE_TO_USE}"
                sh 'cp /home/dawr/tblBiota_"${DATE_TO_USE}".csv nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota_pp.csv'
                sh 'nsl-infra/playbooks/roles/bootstrap-db/templates/clean-tblbiota.sh nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota_pp.csv > nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota.csv'
            }
        }
        
        slackSend color: 'good', message: "Finished Processing csv ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"

        dir('nsl-infra'){
            def shard_vars = '@shard_vars/$SHARD_TYPE.json'

            def verbose = '-v'

            slackSend color: 'good', message: "Starting bootstrap process ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
            
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

            slackSend color: 'good', message: "Successfully finished: ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
        }
    }
}