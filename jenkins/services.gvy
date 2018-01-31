node {
  
   stage("Preparation for "+ "${ENVIRONMENT_NAME ?: INVENTORY_NAME}" ) { // for display purposes
      // Get some code from a GitHub repository
      sh 'rm -rf *'
      
      checkout([$class: 'GitSCM', branches: [[name: '*/dawr-master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'services']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/services.git']]])
      
      checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
    
     
   }
   stage('Unit test') {
        
        dir('services'){
            try{
                sh 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64;$WORKSPACE/services/grailsw clean-all;$WORKSPACE/services/grailsw "test-app unit:"'
        
            }catch(e) {
                currentBuild.result = 'failure'
            }
           }
      
   }
   stage('Building war') {
      
        dir('services'){
         sh 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64;$WORKSPACE/services/grailsw war;$WORKSPACE/services/grailsw "set-war-path nxl"'
        }
      
   }
   stage("Deploy") {
      dir('nsl-infra'){
          def warDir = pwd()+"/../services/target/"
          if (ENVIRONMENT_NAME) {
              def extra_vars = /'{"nxl_env_name":"$ENVIRONMENT_NAME","apps":[{"app": "services"}], "war_names": [{"war_name": "nxl#services##1.0123"}   ],   "war_source_dir": "$warDir"}'/
              sh "sed -ie 's/.*instance_filters = tag:env=.*\$/instance_filters = tag:env=$ENVIRONMENT_NAME/g' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars '@shard_vars/icn.json'"
          }else if (INVENTORY_NAME){
              def extra_vars = /'{"nxl_env_name":"$INVENTORY_NAME","apps":[{"app": "services"}], "war_names": [{"war_name": "nxl#services##1.0123"}   ],   "war_source_dir": "$warDir"}'/
              sh "ansible-playbook  -i inventory/$INVENTORY_NAME -u ubuntu playbooks/deploy.yml -e $extra_vars --extra-vars '@shard_vars/icn.json'"
          }


      }
   }
}
