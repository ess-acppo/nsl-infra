node {
  
   stage('Preparation') { // for display purposes
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
         sh 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64;$WORKSPACE/services/grailsw war;$WORKSPACE/services/grailsw "set-war-path nsl"'
        }
      
   }
   stage("Deploy to $INVENTORY_NAME") {
      dir('nsl-infra'){
          sh 'sed -ie \'s/.*instance_filters = tag:env=.*$/instance_filters = tag:env=$ENVIRONMENT_NAME/g\' aws_utils/ec2.ini && ansible-playbook  -i aws_utils/ec2.py -u ubuntu playbooks/deploy.yml -e \'{"apps":[{"app": "services"}], "war_names": [{"war_name": "nsl#services##1.0123"}   ],   "war_source_dir": "/var/lib/jenkins/workspace/nsl-services-pipeline/services/target"}\''
      }
   }
}
