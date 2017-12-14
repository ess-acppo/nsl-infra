/*
Perquisites before this can be run in a machine ( irrespective of whether it run using Jenkins):
1. Install boto
2. Configure AWS KEYS
3. Configure ssh to enable shh thorugh bastion
*/

node {
  
   stage("Preparation for $ENVIRONMENT_NAME") { // for display purposes
      // Get some code from a GitHub repository
      sh 'rm -rf *'
      
      checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
    
     
   }

   stage("Creating env called $ENVIRONMENT_NAME") {
      dir('nsl-infra'){
          sh 'ansible-playbook -vvv playbooks/infra.yml  -e "nxl_env_name=$ENVIRONMENT_NAME"'
      }
   }

    stage("Deploy app to $ENVIRONMENT_NAME") {
        dir('nsl-infra'){
            sh 'sed -ie \'s/.*instance_filters = tag:env=.*$/instance_filters = tag:env=$ENVIRONMENT_NAME/g\' aws_utils/ec2.ini && ansible-playbook -i aws_utils/ec2.py -u ubuntu  playbooks/deploy.yml -e \'{"elb_dns": "linnaeus-elb-809937712.ap-southeast-2.elb.amazonaws.com","apps":[{"app": "services"}], "war_names": [{"war_name": "nsl#services##1.0123"}   ],   "war_source_dir": "/var/lib/jenkins/workspace/nsl-services-pipeline/services/target"}\''
        }
    }
}
