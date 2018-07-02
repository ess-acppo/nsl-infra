node {
    def projectDir
    def warDir
    def shard_vars = '@shard_vars/$SHARD_TYPE.json'

    def env_instance_name = "$ENVIRONMENT_NAME".split(",")[0]
    def env_name = env_instance_name.split("-")[0]
    def elb_dns = "$SHARD_TYPE" + "." + "$env_name" + ".oztaxa.com"

    stage("Prepare") { // for display purposes
        // Get some code from a GitHub repository
        slackSend color: 'good', message: "started ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"

        sh 'rm -rf *'

        // Get some code from a GitHub repository
        sh 'whoami;  touch fake.war; rm *.war || echo "no war files"'

        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-editor']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-editor.git']]])

        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'mapper']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/mapper.git']]])

        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'services']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/services.git']]])

        checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])


    }

    stage('Building wars for editor,mapper and services') {
        withEnv(['PATH+=/opt/jruby-9.1.13.0/bin']){
            dir('nsl-editor'){
                sh " echo $PATH; which jruby; JAVA_OPTS='-server -d64'; jruby -S bundle install --without development test;bundle exec rake assets:clobber;bundle exec rake assets:precompile  RAILS_ENV=production RAILS_GROUPS=assets;bundle exec warble"
                script{
                    projectDir = pwd()
                    sh 'mv nsl-editor.war nxl#editor##$(cat config/version.txt).war'
                }
            }
        }

        dir('mapper') {
            sh 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64;$WORKSPACE/mapper/grailsw war;$WORKSPACE/mapper/grailsw "set-war-path nsl"'
        }

        dir('services') {
            sh 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64;$WORKSPACE/services/grailsw war;$WORKSPACE/services/grailsw "set-war-path nxl"'
        }

    }
    stage("Deploy services to $ENVIRONMENT_NAME") {
        dir('nsl-infra') {
            warDir = pwd() + "/../services/target/"


            if (ENVIRONMENT_NAME) {

                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name","apps":[{"app": "services"}], "war_names": [{"war_name": "nxl#services##1.0123"}   ],   "war_source_dir": "$warDir"}'/
                sh 'echo "whoami: `whoami`"'  
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) {

                def extra_vars = /'{"nxl_env_name":"$env_name","apps":[{"app": "services"}], "war_names": [{"war_name": "nxl#services##1.0123"}   ],   "war_source_dir": "$warDir"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }


        }
    }
    stage("Deploy editor to $ENVIRONMENT_NAME") {

        dir('nsl-infra') {
            if (ENVIRONMENT_NAME) {

                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$ENVIRONMENT_NAME","apps":[{"app": "editor"}], "war_names": [{"war_name": "nxl#editor##1.53"}   ],   "war_source_dir": "$projectDir"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$ENVIRONMENT_NAME/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars -e $shard_vars"
            } else if (INVENTORY_NAME) {
                def extra_vars = /'{"nxl_env_name":"$INVENTORY_NAME","apps":[{"app": "editor"}], "war_names": [{"war_name": "nxl#editor##1.53"}   ],   "war_source_dir": "$projectDir"}'/
                sh "ansible-playbook -vvv -i inventory/$INVENTORY_NAME -u ubuntu playbooks/deploy.yml -e $extra_vars -e $shard_vars"
            }
        }
    }
    stage("Deploy mapper to $ENVIRONMENT_NAME") {

        dir('nsl-infra') {
            warDir = pwd() + "/../mapper/target/"


            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name","apps":[{"app": "mapper"}], "war_names": [{"war_name": "nsl#mapper##1.0021"}   ],   "war_source_dir": "$warDir"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) {

                def extra_vars = /'{"nxl_env_name":"$env_name","apps":[{"app": "mapper"}], "war_names": [{"war_name": "nsl#mapper##1.0021"}   ],   "war_source_dir": "$warDir"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }


        }
    }

    stage("Run smoke test"){
        sleep(2)
        slackSend color: "good", message: "${env.JOB_NAME} deployed ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) into PROD"

    }


}