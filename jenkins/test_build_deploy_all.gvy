node {
    def projectDir
    def warDir
    def shard_vars = '@shard_vars/$SHARD_TYPE.json'

    def env_instance_name = "$ENVIRONMENT_NAME".split(",")[0]
    def env_name = env_instance_name.split("-")[0]
    def elb_dns = "$ENVIRONMENT_NAME"+".oztaxa.com"
    // Git Variables
    def git_tag_domain_plugin = '*/master'
    def git_tag_services = '*/master'
    def git_tag_mapper = '*/master'
    def git_tag_editor = '*/master'
    def git_url_services = 'https://github.com/bio-org-au/services.git'
    stage("Prepare") { // for display purposes
        // Get some code from a GitHub repository
        try {
            slackSend color: 'good', message: "started ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"

            sh 'whoami'
            sh 'echo "ANSIBLE VERSION :" && ansible --version'
            sh 'echo "PYTHON VERSION :" && python --version'
            sh 'echo "JAVA VERSION :" && java -version'
            sh 'rm -rf * && rm -rf /tmp//tmp/services_war_filename /tmp/services_war_filename /tmp/mapper_war_filename'

            // Get some code from a GitHub repository
            sh 'whoami;  touch fake.war; rm *.war || echo "no war files"'

            checkout([$class: 'GitSCM', branches: [[name: "${git_tag_editor}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-editor']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/bio-org-au/nsl-editor.git']]])

            checkout([$class: 'GitSCM', branches: [[name: "${git_tag_mapper}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'mapper']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/bio-org-au/mapper.git']]])

            checkout([$class: 'GitSCM',branches: [[name: "${git_tag_services}"]],doGenerateSubmoduleConfigurations: false,extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'services']],submoduleCfg: [],userRemoteConfigs: [[url: "${git_url_services}"]]])

            checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])

            checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nxl-private']], submoduleCfg: [], userRemoteConfigs: [[url: '/var/lib/jenkins/nxl-private']]])
    
            checkout([$class: 'GitSCM', branches: [[name: "${git_tag_domain_plugin}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-domain-plugin']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/bio-org-au/nsl-domain-plugin.git']]])

        } catch (e) {
               slackSend color: 'bad', message: "failed ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details: ${e.message} >)"
               currentBuild.result = 'FAILURE'
               throw e
        }
    }
        slackSend color: 'good', message: "Building war files in ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
    stage("Copy Ad-hoc Files from Private Repository"){
        dir('nsl-infra') {
                sh 'cp ../nxl-private/bnti/jdk*.tar.gz playbooks/roles/tomcat8/files/'
                sh 'cp ../nxl-private/bnti/tag_role_database.yml aws_utils/group_vars/tag_role_database.yml'
                sh 'cp ../nxl-private/bnti/tag_role_tomcat.yml aws_utils/group_vars/tag_role_tomcat.yml'
                sh 'cp ../nxl-private/bnti/add_user.ldif.j2 playbooks/roles/apacheds/templates/add_user.ldif.j2'
        }
    }

    stage('Building wars for editor,mapper and services') {
        withEnv(['PATH+=/opt/jruby-9.1.13.0/bin']){
            dir('nsl-editor'){
                sh "echo $PATH; which jruby; JAVA_OPTS='-server -d64'; jruby -S bundle install --without development test;bundle exec rake assets:clobber;bundle exec rake assets:precompile  RAILS_ENV=production RAILS_GROUPS=assets;bundle exec warble"
                script{
                    projectDir = pwd()
                    sh 'mv nsl-editor.war nxl#editor##$(cat config/version.properties | sed -e \'s/.*=//g\').war'
                    sh 'echo "nxl#editor##$(cat config/version.properties | sed -e \'s/.*=//g\')" > /tmp/editor_war_filename'
                    def warFileName = readFile('/tmp/editor_war_filename').trim()
                    sh "cp ${warFileName}.war ../../../builds/${env.BUILD_NUMBER}-${warFileName}.war"
                }
            }
        }

            dir('mapper') {
                sh 'cp ../nxl-private/bnti/build-nxl-mapper.sh .'
                sh 'chmod +x ./build-nxl-mapper.sh'
                sh './build-nxl-mapper.sh'
                sh 'mv ./target/nsl-mapper##$(cat application.properties | grep -i "app.version=" | sed -e \'s/^app.version=//g\').war ./target/nxl#mapper##$(cat application.properties | grep -i "app.version=" | sed -e \'s/^app.version=//g\').war'
                sh 'echo "nxl#mapper##$(cat application.properties | grep -i "app.version=" | sed -e \'s/^app.version=//g\')" > /tmp/mapper_war_filename'
                def warFileName = readFile('/tmp/mapper_war_filename').trim()
                sh "cp ./target/${warFileName}.war ../../../builds/${env.BUILD_NUMBER}-${warFileName}.war"
            }
            dir('nsl-domain-plugin') {
                sh 'cp ../nxl-private/bnti/services-BuildConfig.groovy ./grails-app/conf/BuildConfig.groovy'
                sh 'cp ../nxl-private/bnti/build-nsl-dm-plugin.sh .'
                sh 'chmod +x ./build-nsl-dm-plugin.sh'
                sh './build-nsl-dm-plugin.sh'
            }
            dir('services') {
                sh "sed -ie 's;<title>NSL Services;<title>NXL '$ENVIRONMENT_NAME';g' grails-app/views/index.gsp"
                sh 'cat grails-app/views/index.gsp'
                sh 'cp ../nxl-private/bnti/build-nxl-services.sh .'
                sh 'chmod +x ./build-nxl-services.sh'
                sh './build-nxl-services.sh'
                sh 'mv ./target/services##$(cat application.properties | grep -i "app.version=" | sed -e \'s/^app.version=//g\').war ./target/nxl#services##$(cat application.properties | grep -i "app.version=" | sed -e \'s/^app.version=//g\').war'
                sh 'echo "nxl#services##$(cat application.properties | grep -i "app.version=" | sed -e \'s/^app.version=//g\')" > /tmp/services_war_filename'
                def warFileName = readFile('/tmp/services_war_filename').trim()
                sh "cp ./target/${warFileName}.war ../../../builds/${env.BUILD_NUMBER}-${warFileName}.war"
            }


        slackSend color: 'good', message: "Preparing to Deploy war files in ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"

    }
    def services_war_filename = readFile('/tmp/services_war_filename').trim()
    def mapper_war_filename = readFile('/tmp/mapper_war_filename').trim()
    def editor_war_filename = readFile('/tmp/editor_war_filename').trim()
    println "S= $services_war_filename :: M=$mapper_war_filename :: E=$editor_war_filename"
    stage("Deploy services to $ENVIRONMENT_NAME") {
        dir('nsl-infra') {
            services_war_filename = readFile('/tmp/services_war_filename').trim()
            println "Image Copy Dir: $services_war_filename"
            sh "sed -ie 's/nxl#services##1.0210/'${services_war_filename}'/g' playbooks/roles/deploy-war/tasks/main.yml"
            warDir = pwd() + "/../services/target"
            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name","apps":[{"app": "services"}], "war_names": [{"war_name": "$services_war_filename"}   ],   "war_source_dir": "$warDir"}'/
                sh 'echo "whoami: `whoami`"'  
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) {
                def extra_vars = /'{"nxl_env_name":"$env_name","apps":[{"app": "services"}], "war_names": [{"war_name": "$services_war_filename"}   ],   "war_source_dir": "$warDir"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }
        }
    }
    stage("Deploy editor to $ENVIRONMENT_NAME") {

        dir('nsl-infra') {
            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$ENVIRONMENT_NAME","apps":[{"app": "editor"}], "war_names": [{"war_name": "$editor_war_filename"}   ],   "war_source_dir": "$projectDir"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$ENVIRONMENT_NAME/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars -e $shard_vars"
            } else if (INVENTORY_NAME) {
                def extra_vars = /'{"nxl_env_name":"$INVENTORY_NAME","apps":[{"app": "editor"}], "war_names": [{"war_name": "$editor_war_filename"}   ],   "war_source_dir": "$projectDir"}'/
                sh "ansible-playbook -vvv -i inventory/$INVENTORY_NAME -u ubuntu playbooks/deploy.yml -e $extra_vars -e $shard_vars"
            }
        }
    }
    stage("Deploy mapper to $ENVIRONMENT_NAME") {

        dir('nsl-infra') {
            warDir = pwd() + "/../mapper/target/"
            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name","apps":[{"app": "mapper"}], "war_names": [{"war_name": "$mapper_war_filename"}   ],   "war_source_dir": "$warDir"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) {

                def extra_vars = /'{"nxl_env_name":"$env_name","apps":[{"app": "mapper"}], "war_names": [{"war_name": "$mapper_war_filename"}   ],   "war_source_dir": "$warDir"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }
        }
    }
        

    stage("Bootstrapping data into DB") {
    node{
        checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])

        slackSend color: 'good', message: "Starting bootstrap process for DB in ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
        sh 'cp /var/lib/jenkins/nxl-private/bnti/reconstruct-name-strings.sh nsl-infra/playbooks/roles/bootstrap-db/files/reconstruct-name-strings.sh'
        sh 'chmod +x nsl-infra/playbooks/roles/bootstrap-db/files/reconstruct-name-strings.sh'

        sh 'cp /var/lib/jenkins/nxl-private/bnti/refresh-views.sh nsl-infra/playbooks/roles/bootstrap-db/files/refresh-views.sh'
        sh 'chmod +x nsl-infra/playbooks/roles/bootstrap-db/files/refresh-views.sh'
        
        def ds_val = "${DATA_SOURCE}"
        def date_val = "${DATE_TO_USE}"
        echo "${ds_val}"
        if (ds_val == "base") {
            sh 'cp /var/lib/jenkins/nxl-private/bnti/tblbiota_base.csv nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota.csv'
        } else if (ds_val == "empty") {
            sh 'cp /var/lib/jenkins/nxl-private/bnti/tblbiota_empty.csv nsl-infra/playbooks/roles/bootstrap-db/files/tblbiota.csv'
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
            def verbose = '-v'

            slackSend color: 'good', message: "Starting bootstrap process ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
            
            if (ENVIRONMENT_NAME) {
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook $verbose -i aws_utils/ec2.py -u ubuntu playbooks/bootstrap_db.yml --tags \"load-data\" -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) { // not fully implemented
                env_name = "$INVENTORY_NAME".split(",")[0]
                def extra_vars = /'{"nxl_env_name":"$env_name"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }
            slackSend color: 'good', message: "Successfully finished: ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
        }
    }
    }

    stage("Delete Temp Files"){
        sleep(2)
        slackSend color: "good", message: "Deleting Temporary files for ${env.JOB_NAME}  ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"

        sh 'rm -rf /tmp/services_war_filename /tmp/mapper_war_filename /tmp/editor_war_filename'
    }

    stage("Run smoke test"){
        sleep(2)
        slackSend color: "good", message: "${env.JOB_NAME} completed successfully ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"

    }
}
