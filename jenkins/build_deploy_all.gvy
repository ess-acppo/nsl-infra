node {
    def projectDir
    def warDir
    def shard_vars = '@shard_vars/$SHARD_TYPE.json'

    def env_instance_name = "$ENVIRONMENT_NAME".split(",")[0]
    def env_name = env_instance_name.split("-")[0]
    def elb_dns = "$env_name"+"-"+"$SHARD_TYPE"+".oztaxa.com"

    def git_tag_domain_plugin = '34e671f818c83dffba672a1938c060faa2d01db9'
    def git_tag_services = 'e251bdd5f20e73832ec81501ae530b924e1dda2f'
    def git_tag_mapper = '138d1ddd8e71c7a79c7405d3269fd6ceb00aa87f'
    def git_tag_editor = '9675e53469f352fcf4a439b0d8eeacbd91f12285'

    stage("Prepare") { // for display purposes
        // Get some code from a GitHub repository
        slackSend color: 'good', message: "started ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"

        sh 'rm -rf *'

        // Get some code from a GitHub repository
        sh 'whoami;  touch fake.war; rm *.war || echo "no war files"'

        checkout([$class: 'GitSCM', branches: [[name: "${git_tag_editor}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-editor']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/bio-org-au/nsl-editor.git']]])

        checkout([$class: 'GitSCM', branches: [[name: "${git_tag_mapper}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'mapper']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/bio-org-au/mapper.git']]])

        checkout([$class: 'GitSCM',branches: [[name: "${git_tag_services}"]],doGenerateSubmoduleConfigurations: false,extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'services']],submoduleCfg: [],userRemoteConfigs: [[url: 'https://github.com/bio-org-au/services.git']]])

        checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])

        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nxl-private']], submoduleCfg: [], userRemoteConfigs: [[url: '/var/lib/jenkins/nxl-private']]])
    
        checkout([$class: 'GitSCM', branches: [[name: "${git_tag_domain_plugin}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-domain-plugin']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/bio-org-au/nsl-domain-plugin.git']]])
    }

    stage("Copy Ad-hoc Files from Private Repository"){
        dir('nsl-infra') {
                sh 'cp ../nxl-private/bnti/jdk*.tar.gz playbooks/roles/tomcat8/files/'
                sh 'cp ../nxl-private/bnti/tag_tole_database.yml aws_utils/group_vars/tag_role_database.yml'
        }
    }

    stage('Building wars for editor,mapper and services') {
        withEnv(['PATH+=/opt/jruby-9.1.13.0/bin']){
            dir('nsl-editor'){
                sh "echo $PATH; which jruby; JAVA_OPTS='-server -d64'; jruby -S bundle install --without development test;bundle exec rake assets:clobber;bundle exec rake assets:precompile  RAILS_ENV=production RAILS_GROUPS=assets;bundle exec warble"
                script{
                    projectDir = pwd()
                    sh 'mv nsl-editor.war nxl#editor##$(cat config/version.properties | sed -e \'s/.*=//g\').war'
                }
            }
        }

            dir('mapper') {
                sh 'cp ../nxl-private/bnti/build-nxl-mapper.sh .'
                sh 'chmod +x ./build-nxl-mapper.sh'
                sh './build-nxl-mapper.sh'
                sh 'mv ./target/nsl-mapper##1.0022.war ./target/nsl#mapper##1.0022.war'
            }
            dir('nsl-domain-plugin') {
                sh 'cp ../nxl-private/bnti/services-BuildConfig.groovy ./grails-app/conf/BuildConfig.groovy'
                sh 'cp ../nxl-private/bnti/build-nsl-dm-plugin.sh .'
                sh 'chmod +x ./build-nsl-dm-plugin.sh'
                sh './build-nsl-dm-plugin.sh'
            }
            dir('services') {
                sh 'cp ../nxl-private/bnti/build-nxl-services.sh .'
                sh 'chmod +x ./build-nxl-services.sh'
                sh './build-nxl-services.sh'
                sh 'mv ./target/services##1.0205.war ./target/nxl#services##1.0205.war'
            }

    }
    stage("Deploy services to $ENVIRONMENT_NAME") {
        dir('nsl-infra') {
            warDir = pwd() + "/../services/target"
            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name","apps":[{"app": "services"}], "war_names": [{"war_name": "nxl#services##1.0205"}   ],   "war_source_dir": "$warDir"}'/
                sh 'echo "whoami: `whoami`"'  
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) {
                def extra_vars = /'{"nxl_env_name":"$env_name","apps":[{"app": "services"}], "war_names": [{"war_name": "nxl#services##1.0205"}   ],   "war_source_dir": "$warDir"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }
        }
    }
    stage("Deploy editor to $ENVIRONMENT_NAME") {

        dir('nsl-infra') {
            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$ENVIRONMENT_NAME","apps":[{"app": "editor"}], "war_names": [{"war_name": "nxl#editor##1.66"}   ],   "war_source_dir": "$projectDir"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$ENVIRONMENT_NAME/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars -e $shard_vars"
            } else if (INVENTORY_NAME) {
                def extra_vars = /'{"nxl_env_name":"$INVENTORY_NAME","apps":[{"app": "editor"}], "war_names": [{"war_name": "nxl#editor##1.66"}   ],   "war_source_dir": "$projectDir"}'/
                sh "ansible-playbook -vvv -i inventory/$INVENTORY_NAME -u ubuntu playbooks/deploy.yml -e $extra_vars -e $shard_vars"
            }
        }
    }
    stage("Deploy mapper to $ENVIRONMENT_NAME") {

        dir('nsl-infra') {
            warDir = pwd() + "/../mapper/target/"
            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name","apps":[{"app": "mapper"}], "war_names": [{"war_name": "nsl#mapper##1.0022"}   ],   "war_source_dir": "$warDir"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) {

                def extra_vars = /'{"nxl_env_name":"$env_name","apps":[{"app": "mapper"}], "war_names": [{"war_name": "nsl#mapper##1.0022"}   ],   "war_source_dir": "$warDir"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }
        }
    }

    stage("Bootstrapping data into DB") {
    node{
        checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
        dir('nsl-infra'){
            def verbose = ''
            if(VERBOSE){
                verbose = '-vvv'
            }
            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook $verbose -i aws_utils/ec2.py -u ubuntu playbooks/bootstrap_db.yml --tags \"load-data\" -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) { // not fully implemented
                env_name = "$INVENTORY_NAME".split(",")[0]
                def extra_vars = /'{"nxl_env_name":"$env_name"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }
        }
    }
    }

    stage("Run smoke test"){
        sleep(2)
        slackSend color: "good", message: "${env.JOB_NAME} completed successfully ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"

    }


}