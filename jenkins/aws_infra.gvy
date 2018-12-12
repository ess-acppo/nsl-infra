/*
Perquisites before this can be run in a machine ( irrespective of whether it run using Jenkins):
1. Install boto
2. Configure AWS KEYS
3. Configure ssh to enable shh thorugh bastion
*/


stage("Creating environment") {
node{
<<<<<<< HEAD
checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
 dir('nsl-infra'){    
    def extra_vars = /'{"nxl_env_name":"$ENVIRONMENT_NAME","nxl_ami": "$AMI_ID", "VPC_ID": "$VPC_ID","public_subnet_cidr" : "$public_subnet_cidr", "public_subnet2_cidr" : "$public_subnet2_cidr", "private_subnet_cidr": "$private_subnet_cidr"}'/
    sh "ansible-playbook -vvv playbooks/infra.yml  -e $extra_vars"
 }
=======
sh 'rm -rf *'
checkout([$class: 'GitSCM', branches: [[name: '*/flex-deploy']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'nsl-infra']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/ess-acppo/nsl-infra.git']]])
slackSend color: 'good', message: "Started Job: ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
if(OLD_VERSION) {
    dir('nsl-infra') {
        sh 'pwd && ls -l'
        sh 'git checkout bc390261cac796aff26093cd35c5dfa45bc5958d'
    }
} else {
    sh 'echo "Building top of the branch"'
}

dir('nsl-infra') {
    sh 'whoami'
    sh 'echo "ANSIBLE VERSION :" && ansible --version'
    sh 'echo "PYTHON VERSION :" && python --version'
    sh 'echo "JAVA VERSION :" && java -version'
    def extra_vars = /'{"nxl_env_name":"$ENVIRONMENT_NAME","nxl_ami": "$AMI_ID", "VPC_ID": "$VPC_ID","public_subnet_cidr" : "$public_subnet_cidr", "public_subnet2_cidr" : "$public_subnet2_cidr", "private_subnet_cidr": "$private_subnet_cidr", "BAS_HOST_SG": "$BAS_HOST_SG"}'/
    sh "ansible-playbook -v playbooks/infra.yml  -e $extra_vars"
}
slackSend color: 'good', message: "Successfully completed Job ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details...>)"
>>>>>>> flex-deploy
}
}
