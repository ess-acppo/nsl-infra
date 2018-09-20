node {

    stage("Prepare") { // for display purposes
        // Get some code from a GitHub repository
        slackSend color: 'good', message: "started ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"

        sh 'rm -rf *'

        checkout([$class: 'GitSCM', branches: [[name: '*/BNTi-customize_name_construction']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'services']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/services.git']]])

        checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])

        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nxl-private']], submoduleCfg: [], userRemoteConfigs: [[url: '/var/lib/jenkins/nxl-private']]])
    }
    stage('Building war') {

        dir('services') {
            // sh 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64;$WORKSPACE/services/grailsw war;$WORKSPACE/services/grailsw "set-war-path nxl"'
            sh 'cp ../nxl-private/bnti/build-nxl-services.sh .'
            sh 'chmod +x ./build-nxl-services.sh'
            sh './build-nxl-services.sh'
            sh 'mv ./target/services##1.0205.war ./target/nxl#services##1.0205.war'
        }
    }


    stage("Deploy to environment level-1") {
        dir('nsl-infra') {
            def warDir = pwd() + "/../services/target/"
            def shard_vars = '@shard_vars/$SHARD_TYPE.json'

            if (ENVIRONMENT_NAME) {
                def env_instance_name = "$ENVIRONMENT_NAME".split(",")[0]
                def env_name = env_instance_name.split("-")[0]
                def elb_dns = "$SHARD_TYPE"+"."+"$env_name"+".oztaxa.com"
                def extra_vars = /'{"elb_dns": "$elb_dns","nxl_env_name":"$env_instance_name","apps":[{"app": "services"}], "war_names": [{"war_name": "nxl#services##1.0123"}   ],   "war_source_dir": "$warDir"}'/
                sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$env_instance_name/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            } else if (INVENTORY_NAME) {
                def env_name = "$INVENTORY_NAME".split(",")[0]
                def extra_vars = /'{"nxl_env_name":"$env_name","apps":[{"app": "services"}], "war_names": [{"war_name": "nxl#services##1.0123"}   ],   "war_source_dir": "$warDir"}'/
                sh "ansible-playbook  -i inventory/$env_name -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars $shard_vars"
            }


        }
    }
}
    // stage("Integration test in environment level-1") {
    //     sleep(2)
    // }
    // stage("Deploy to environment level-2") {
    //     def env_name = "$ENVIRONMENT_NAME".split(",")[1]
    //     sleep(2)
    //     slackSend color: 'good', message: "New build  ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) deployed to $env_name"

    // }
    // stage("Perform manual validations in environment level-2") {
    //     input message: 'Are you satisfied with current state of application?', ok: 'Yes', submitter: 'admin,arnab', submitterParameter: 'who_approved'

    // }
    // stage("Deploy to environment PROD") {
    //     def env_name = "$ENVIRONMENT_NAME".split(",")[2]
    //     sleep(2)
    // }
    // stage("Run smoke test on PROD"){
    //     sleep(2)
    //     slackSend color: "good", message: "${env.JOB_NAME} deployed ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) into PROD"

    // }


