/*
Perquisites before this can be run in a machine ( irrespective of whether it run using Jenkins):
1. Install boto
2. Configure AWS KEYS
3. Configure ssh to enable shh thorugh bastion
*/

stage("Creating environment $ENVIRONMENT_NAME") {
    node{
        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
        dir('nsl-infra'){
            def extra_vars = /'{"nxl_env_name":"$ENVIRONMENT_NAME","nxl_ami": "$AMI_ID"}'/
            sh "ansible-playbook -vvv playbooks/infra.yml  -e $extra_vars"
        }
    }
}
